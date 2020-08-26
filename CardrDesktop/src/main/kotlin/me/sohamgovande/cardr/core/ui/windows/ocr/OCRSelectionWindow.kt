package me.sohamgovande.cardr.core.ui.windows.ocr

import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.image.Image
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.paint.Color
import javafx.stage.StageStyle
import me.sohamgovande.cardr.CHROME_OCR_MODE
import me.sohamgovande.cardr.core.ui.CardrUI
import me.sohamgovande.cardr.core.ui.WindowDimensions
import me.sohamgovande.cardr.core.ui.tabs.EditCardTabUI
import me.sohamgovande.cardr.core.ui.tabs.TabUI
import me.sohamgovande.cardr.core.ui.windows.ModalWindow
import me.sohamgovande.cardr.core.ui.windows.SignInWindow
import me.sohamgovande.cardr.core.ui.windows.ocr.ResizeListener.Companion.BORDER_SIZE
import me.sohamgovande.cardr.data.prefs.Prefs
import me.sohamgovande.cardr.data.urls.UrlHelper
import me.sohamgovande.cardr.platformspecific.MacMSWordInteractor
import me.sohamgovande.cardr.util.*
import org.apache.logging.log4j.LogManager
import java.awt.Rectangle
import java.awt.Robot
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.*
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.system.exitProcess


class OCRSelectionWindow(private val cardrUI: CardrUI, private val currentTab: () -> EditCardTabUI): ModalWindow("OCR Region"){

    private val menuBox = HBox()
    private val captureBtn = Button("Capture")

    private var xOffset = 0.0
    private var yOffset = 0.0

    private var thread: Thread? = null

    override fun show() {
        if (openWindows.any { it is SignInWindow })
            return
        hideAllWindows()
        window.initStyle(StageStyle.TRANSPARENT)
        window.title = title
        window.scene = generateUI()
        window.isResizable = true
        window.isAlwaysOnTop = true
        window.show()

        window.setOnCloseRequest {
            close(it)
            for (listener in onCloseListeners) {
                listener(onCloseData)
            }
        }

        addResizeListener(window)
        openWindows.add(this)

        val windowDimensions = Prefs.get().ocrWindowDimensions
        if (windowDimensions.x != -1024.1024) {
            windowDimensions.apply(window)
        }
    }

    fun ensureDependencies(): Boolean {
        if (getOSType() != OS.MAC)
            return true
        val cellarFile = Paths.get(System.getProperty("cardr.data.dir"),"ocr","dependencies","Cellar").toFile()
        if (cellarFile.exists()) {
            val files = cellarFile.listFiles()!!
            var hasDependencies = true
            for (file in files) {
                if (!File("/usr/local/opt/${file.name}").exists()) {
                    hasDependencies = false
                    break
                }
            }
            if (hasDependencies)
                return true
        }

        var success = true
        showInfoDialogBlocking("Allow Cardr to install the necessary files for OCR?", "We may need to request administrator permission to copy files to system directories.", "Cancel") {
            success = false
        }
        if (success) {
            MacMSWordInteractor().copyOCRDependencies()
        }
        return success
    }

    private fun onCapture() {
        logger.info("OCR onCapture invoked. CHROME_OCR_MODE=$CHROME_OCR_MODE")
        Prefs.get().ocrWindowDimensions = WindowDimensions(window)
        logger.info("Saving window dimensions ${Prefs.get().ocrWindowDimensions}")
        Prefs.save()

        logger.info("Hiding window...")
        window.hide()

        thread = Thread {
            logger.info("Instantiating robot...")
            val robot = Robot()
            if (isMac()) {
                logger.info("macOS detected, so delaying 500ms")
                robot.delay(500)
            }
            logger.info("Taking screen capture...")
            val image = robot.createScreenCapture(Rectangle(
                window.x.toInt(), window.y.toInt(), window.width.toInt(), window.height.toInt()
            ))
            val imageFile = Paths.get(System.getProperty("cardr.data.dir"), "ocr", "ocr-region.png").toFile()
            logger.info("Taking screen capture, writing to ${imageFile.canonicalPath}")
            ImageIO.write(image, "png", imageFile)
            Platform.runLater {
                window.show()
                captureBtn.graphic = null
                captureBtn.text = "Loading..."
                captureBtn.isDisable = true
            }

            val text = getOCRFromAPI()
            Platform.runLater {
                if (text == null) {
                    logger.info("OCR API returned no valid text")
                    close(null)
                    return@runLater
                }

                logger.info("OCR successful. Closing OCR region and re-opening all windows.")
                close(null)
                showAllWindows()

                val builder = if (cardrUI.ocrCardBuilderWindow == null) OCRCardBuilderWindow(cardrUI, currentTab) else cardrUI.ocrCardBuilderWindow!!
                if (cardrUI.ocrCardBuilderWindow == null) {
                    cardrUI.ocrCardBuilderWindow = builder
                    builder.show()
                } else {
                    builder.window.requestFocus()
                }
                builder.addOnCloseListener(this::loadOCRText)
                builder.importText(text)
            }
        }
        thread!!.start()
    }

    private fun getOCRFromAPI(): String? {
        logger.info("Using Jar API to load OCR text")
        if (isMac()) {
            System.setProperty("jna.library.path", Paths.get(System.getProperty("cardr.data.dir"), "ocr", "native").toFile().canonicalPath)
            System.setProperty("jna.boot.library.path", Paths.get(System.getProperty("cardr.data.dir"), "ocr", "native").toFile().canonicalPath)
            System.setProperty("jna.nounpack", "true")
        }

        if (doOCRMethod == null || ocrInstance == null) {
            logger.info("First time using OCR - need to initialize reflection API")
            @Suppress("DEPRECATION") val classLoader = URLClassLoader(arrayOf(Paths.get(System.getProperty("cardr.data.dir"), "ocr", "CardrOCR.jar").toFile().toURL()), ClassLoader.getSystemClassLoader())
            val ocrClass = classLoader.loadClass("me.sohamgovande.cardr.ocr.CardrOCR")

            if (doOCRMethod == null)
                doOCRMethod = ocrClass.getMethod("doOCR")
            if (ocrInstance == null)
                ocrInstance = ocrClass.getConstructor(Array<String>::class.java)
                        .newInstance(arrayOf(System.getProperty("cardr.data.dir")))
        } else {
            logger.info("Reflection API has already been loaded")
        }
        logger.info("Invoking API call...")
        val executor = Executors.newCachedThreadPool()
        val handler = executor.submit<Any> {
            doOCRMethod!!.invoke(ocrInstance!!)
            null
        }
        try {
            handler.get(5000, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            showVSWarning()
        } catch (e: InterruptedException) {
            logger.error("Error invoking OCR API - InterruptedException", e)
            return null
        } catch (e: ExecutionException) {
            logger.error("Error invoking OCR API - ExecutionException", e)
            if (e.cause is InvocationTargetException
                && ((e.cause as InvocationTargetException).cause is UnsatisfiedLinkError ||
                    ((e.cause as InvocationTargetException).targetException is NoClassDefFoundError))
                && isWindows()) {
                showVSWarning()
            } else {
                Platform.runLater { showErrorDialog(e) }
            }
            handler.cancel(true)
            return null
        } catch (e: InvocationTargetException) {
            logger.error("Error invoking OCR API - InvocationTargetException", e)
            Platform.runLater { showErrorDialog(e) }
            return null
        } finally {
            handler.cancel(true)
        }


        logger.info("Reading back OCR from ocr-result.txt")
        val ocrResultPath = Paths.get(System.getProperty("cardr.data.dir"), "ocr", "ocr-result.txt")
        if (!ocrResultPath.toFile().exists()) {
            logger.info("ocr-result.txt does not exist")
            Platform.runLater { showErrorDialogBlocking("Error loading OCR", "OCR result file not found") }
            return null
        } else {
            return Files.readString(ocrResultPath)
        }
    }

//    private fun getOCRFromREST(imageFile: File): String {
//        val client = HttpClientBuilder.create().build()
//        val request = HttpPost("https://api.ocr.space/parse/image")
//        request.setHeader("apikey", SecretData.OCRSPACE_APIKEY)
//        request.entity = MultipartEntityBuilder.create()
//            .addBinaryBody("file", imageFile)
//            .build()
//
//        val response: CloseableHttpResponse = client.execute(request)
//        val data = response.entity.content.bufferedReader().use(BufferedReader::readText)
//        val jsonData = JsonParser().parse(data).asJsonObject
//        return StringEscapeUtils.unescapeJson(jsonData["ParsedResults"].asJsonArray[0].asJsonObject["ParsedText"].asString)
//    }

    private fun showVSWarning() {
        Platform.runLater {
            showErrorDialogBlocking("Please install the Visual Studio Redistributable to be able to use OCR.", "We were unable to do OCR, and this most likely means that you don't have the required VS64 library on your computer. Don't worry - it's super easy to install.\n\n1. Once you click OK, we'll take you to a Microsoft webpage.\n\n2. Find the box titled \"Microsoft Visual C++ Redistributable for Visual Studio 2019\". Here, select your system architecture (most probably x64) and click \"Download\".\n\n3. Follow the installer - it should only take a few steps.\n\n4. Restart Cardr.\n\nClick OK to confirm that you have read this message.")
            UrlHelper.browse("visualStudio")
        }
    }

    private fun loadOCRText(data: HashMap<String, Any>) {
        if (!data.containsKey("ocrText"))
            return

        var text = (data["ocrText"] as String).replace("\r","\n")
        while (text.contains("\n\n"))
            text = text.replace("\n\n","\n")
        text = text.trim()

        val currentTab = this.currentTab()

        currentTab.overrideBodyParagraphs = text.split("\n").toMutableList()
        val sb = StringBuilder()
        currentTab.overrideBodyParagraphs!!.forEach {
            sb.append("<p>")

            sb.append(it)
            sb.append(' ')

            sb.append("</p>")
        }

        currentTab.removeWords.clear()
        currentTab.removeParagraphs.clear()

        currentTab.overrideBodyHTML = null
        currentTab.enableCardBodyEditOptions()
        currentTab.cardBody.set(sb.toString())
        currentTab.refreshHTML()
    }

    private fun hideAllWindows() {
        cardrUI.stage.isIconified = true
        for (window in openWindows) {
            window.window.isIconified = true
        }
    }

    private fun showAllWindows() {
        cardrUI.stage.isIconified = false
        if (!cardrUI.stage.isShowing)
            cardrUI.stage.show()
        for (window in openWindows) {
            window.window.isIconified = false
            if (!window.window.isShowing)
                window.window.show()
        }
    }

    override fun generateUI(): Scene {
        menuBox.spacing = 10.0
        menuBox.padding = Insets(10.0)

        captureBtn.graphic = TabUI.loadMiniIcon("/capture-ocr.png", true, 1.5)
        captureBtn.setOnAction { onCapture() }

        val closeBtn = Button("Close")
        closeBtn.graphic = TabUI.loadMiniIcon("/close.png", true, 1.5)
        closeBtn.setOnAction {
            if (thread != null && thread!!.isAlive)
                thread!!.interrupt()
            close(null)
            if (CHROME_OCR_MODE) {
                exitProcess(0)
            } else {
                CHROME_OCR_MODE = false
                showAllWindows()
            }
        }

        val openFull = Button("Full Cardr")
        openFull.graphic = TabUI.loadMiniIcon("/window.png", true, 1.5)
        openFull.setOnAction {
            if (thread != null && thread!!.isAlive)
                thread!!.interrupt()
            close(null)
            CHROME_OCR_MODE = false
            showAllWindows()
        }

        menuBox.alignment = Pos.CENTER
        menuBox.children.add(captureBtn)
        if (CHROME_OCR_MODE)
            menuBox.children.add(openFull)
        menuBox.children.add(closeBtn)

        val root = BorderPane()
        root.styleClass.add("custom-dashed-border")
        root.center = menuBox

        root.setOnMousePressed {
            if (it.sceneX < BORDER_SIZE || it.sceneY < BORDER_SIZE || abs(root.width - it.sceneX) < BORDER_SIZE || abs(root.height - it.sceneY) < BORDER_SIZE) {
                xOffset = -1.0
                yOffset = -1.0
            } else {
                xOffset = it.sceneX
                yOffset = it.sceneY
            }
        }

        root.setOnMouseDragged {
            if (xOffset == -1.0 && yOffset == -1.0)
                return@setOnMouseDragged
            window.x = it.screenX - xOffset
            window.y = it.screenY - yOffset
        }


        val scene = Scene(root, 400.0, 300.0)
        scene.fill = Color.web("#000000", 0.25)
        scene.stylesheets.add(javaClass.getResource("/styles-transparent.css").toExternalForm())
        window.icons.add(Image(javaClass.getResourceAsStream("/icon-128.png")))
        return scene
    }

    companion object {
        val logger = LogManager.getLogger(OCRSelectionWindow::class.java)
        var ocrInstance: Any? = null
        var doOCRMethod: Method? = null

        @JvmStatic
        fun openWindow(cardrUI: CardrUI, currentTab: () -> EditCardTabUI) {
            val window = OCRSelectionWindow(cardrUI, currentTab)
            if (window.ensureDependencies())
                window.show()
        }
    }
}
