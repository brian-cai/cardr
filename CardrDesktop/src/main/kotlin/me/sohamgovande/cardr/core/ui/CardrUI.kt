package me.sohamgovande.cardr.core.ui

import javafx.application.Platform
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.scene.control.*
import javafx.scene.effect.ColorAdjust
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.KeyCombination
import javafx.scene.layout.*
import javafx.scene.text.TextAlignment
import javafx.scene.web.WebView
import javafx.stage.Stage
import me.sohamgovande.cardr.CardrDesktop
import me.sohamgovande.cardr.core.auth.CardrUser
import me.sohamgovande.cardr.core.card.Author
import me.sohamgovande.cardr.core.card.AuthorNameFormat
import me.sohamgovande.cardr.core.card.Cite
import me.sohamgovande.cardr.core.card.Timestamp
import me.sohamgovande.cardr.platformspecific.WinMSWordInteractor
import me.sohamgovande.cardr.platformspecific.MacMSWordInteractor
import me.sohamgovande.cardr.data.prefs.Prefs
import me.sohamgovande.cardr.data.prefs.PrefsObject
import me.sohamgovande.cardr.core.ui.windows.*
import me.sohamgovande.cardr.util.*
import me.sohamgovande.cardr.core.web.WebsiteCardCutter
import me.sohamgovande.cardr.data.updater.UpdateChecker
import me.sohamgovande.cardr.data.urls.UrlHelper
import org.apache.logging.log4j.LogManager
import org.jsoup.Jsoup
import java.awt.Desktop
import java.awt.Toolkit
import java.io.InputStream
import java.lang.NullPointerException
import java.net.URL
import java.nio.file.Paths
import java.util.function.Consumer

class CardrUI(private val stage: Stage) {

    private var authors: Array<Author> = arrayOf(Author(SimpleStringProperty(""), SimpleStringProperty("")))
    private var title: StringProperty = SimpleStringProperty("")
    private var timestamp: Timestamp = Timestamp()
    private var publisher: StringProperty = SimpleStringProperty("")
    private var url: StringProperty = SimpleStringProperty("")
    private var cardTag: StringProperty = SimpleStringProperty("")
    private val changeListenerUpdateHTML = { _: ObservableValue<out String>, _: String, _: String ->
        Unit
        // Sufficiently delay it to occur after the event goes through
        refreshHTML()
    }
    private val cardBody: StringProperty = SimpleStringProperty("")

    private val propertyUrlTextField = TextField()
    private val propertyPubTextField = TextField()

    private val propertyDayTF = TextField()
    private val propertyMonthTF = TextField()
    private val propertyYearTF = TextField()

    private val propertyTitleTextField = TextField()
    private val cardTagTextField = TextField()
    private val urlTF = TextField()

    private val cardWV = WebView()

    private var lastUI: GridPane? = null
    private val pGrid = GridPane()
    private lateinit var pGridScrollPane: ScrollPane
    private var generateAuthorGridBoxCallback: (GridPane) -> Unit = {}
    var loaded = false

    private val panel = VBox()
    private val searchBarPanel = HBox()
    private val gotoUrlButton = Button("GO")
    private val bodyAreaPanel = HBox()

    private val slashLabel = Label("      /      ")
    private val slashLabel2 = Label("      /      ")
    private val dateGrid = GridPane()

    private val cardDisplayArea = VBox()
    private val cardDisplayMenu = VBox()

    private val exportToWordSettings = VBox()
    private val copyBtn = Button("Copy")
    private val removeSelectedBtn = Button("Remove Selected Text")
    private val restoreRemovedBtn = Button("Restore to Original")
    private val keepOnlySelectedBtn = Button("Remove Except for Selected Text")
    private val editCardFormat = Button("Edit Card Format")
    private val exportBtn = Button("Send to Word")

    private val underlineBtn = Button()
    private val emphasizeBtn = Button()
    private val highlightBtn = Button()

    private val refreshBtn = Button()

    private val wordWindowList = ComboBox<String>()
    private val removeWords = arrayListOf<String>()
    private val removeParagraphs = arrayListOf<String>()

    private val deleteAuthorButtons = arrayListOf<Button>()
    private val searchButtons = arrayListOf<Button>()

    var currentUser = CardrUser()

    private var reader: WebsiteCardCutter? = null
    private val menubarHelper = MenubarHelper(this, stage)

    init {
        currentUser.onSuccessfulLogin = menubarHelper::onSuccessfulLogin
    }

    fun initialize(): VBox {
        stage.widthProperty().addListener { _, _, _ -> onWindowResized() }
        stage.heightProperty().addListener { _, _, _ -> onWindowResized() }

        logger.info("Generating menu bar")
        panel.children.add(VBox(menubarHelper.generateMenuBar()))

        logger.info("Creating UI components")
        searchBarPanel.spacing = 5.0
        searchBarPanel.padding = Insets(5.0)

        urlTF.promptText = "Paste a URL to get started"
        urlTF.prefWidth = CardrDesktop.WIDTH - 50

        gotoUrlButton.prefWidth = 50.0
        searchBarPanel.children.add(urlTF)
        searchBarPanel.children.add(gotoUrlButton)

        bodyAreaPanel.padding = Insets(5.0)

        pGrid.hgap = 10.0
        pGrid.vgap = 10.0
        pGrid.minWidth = 300.0
        pGrid.prefHeight = CardrDesktop.HEIGHT - 100 // Take up the rest remaining space

        bindToRefreshWebView(propertyUrlTextField)
        pGrid.add(Label("URL"), 0, 0)
        pGrid.add(propertyUrlTextField, 1, 0)

        bindToRefreshWebView(propertyPubTextField)
        pGrid.add(Label("Publication"), 0, 1)
        pGrid.add(propertyPubTextField, 1, 1)

        bindToRefreshWebView(propertyDayTF)
        propertyDayTF.prefColumnCount = 2
        propertyDayTF.promptText = "31"

        bindToRefreshWebView(propertyMonthTF)
        propertyMonthTF.prefColumnCount = 2
        propertyMonthTF.promptText = "01"

        bindToRefreshWebView(propertyYearTF)
        propertyYearTF.prefColumnCount = 4
        propertyYearTF.promptText = currentDate().year.toString()

        pGrid.add(Label("Date"), 0, 2)

        dateGrid.padding = Insets(0.0)
        dateGrid.add(propertyMonthTF, 0, 0)
        dateGrid.add(slashLabel, 1, 0)
        dateGrid.add(propertyDayTF, 2, 0)
        dateGrid.add(slashLabel2, 3, 0)
        dateGrid.add(propertyYearTF, 4, 0)

        pGrid.add(dateGrid, 1, 2)

        bindToRefreshWebView(propertyTitleTextField)
        pGrid.add(Label("Title"), 0, 3)
        pGrid.add(propertyTitleTextField, 1, 3)

        cardTagTextField.promptText = ""
        cardTagTextField.textProperty().bindBidirectional(cardTag)
        cardTagTextField.textProperty().addListener { _, _, text ->
            cardTagTextField.text = text.replace("--", "–").replace("–-","—").replace("---", "—")
        }

        bindToRefreshWebView(cardTagTextField)
        pGrid.add(Label("Card Tag"), 0, 4)
        pGrid.add(cardTagTextField, 1, 4)
        pGrid.add(Label("Authors"), 0, 5)

        pGrid.columnConstraints.add(ColumnConstraints(60.0))
        pGrid.columnConstraints.add(ColumnConstraints(225.0))

        pGrid.add(Label("Word"), 0, 6)

        exportToWordSettings.spacing = 5.0

        val header = Label("Send Card to Word")
        header.style = "-fx-font-weight: bold;"
        header.prefWidth = 225.0
        header.textAlignment = TextAlignment.CENTER
        exportToWordSettings.children.add(header)
        exportToWordSettings.children.add(Label("Select Word window:"))

        val exportToWordHBox = GridPane()
        exportToWordHBox.hgap = 5.0

        wordWindowList.padding = Insets(0.0, 0.0, 0.0, 10.0)
        exportToWordHBox.add(refreshBtn, 0, 0)
        exportToWordHBox.add(wordWindowList, 1, 0)
        exportToWordSettings.children.add(exportToWordHBox)

        cardWV.prefWidth = CardrDesktop.WIDTH - 300
        cardWV.prefHeight = CardrDesktop.HEIGHT - 100

        exportToWordSettings.children.add(exportBtn)
        pGrid.add(exportToWordSettings, 1, 6)

        cardDisplayMenu.padding = Insets(0.0, 5.0, 5.0, 5.0)
        cardDisplayMenu.spacing = 5.0

        loadMenuIcons()

        highlightBtn.setOnAction { highlightSelectedText() }
        underlineBtn.setOnAction { underlineSelectedText() }
        emphasizeBtn.setOnAction { underlineSelectedText(); boldSelectedText() }

        val cdm1 = FlowPane()
        cdm1.hgap = 5.0
        cdm1.vgap = 5.0
        cdm1.children.add(removeSelectedBtn)
        cdm1.children.add(keepOnlySelectedBtn)
        cdm1.children.add(restoreRemovedBtn)
        cdm1.children.add(copyBtn)
        cdm1.children.add(editCardFormat)

        cardDisplayMenu.children.add(cdm1)

        cardDisplayArea.children.add(cardDisplayMenu)
        cardDisplayArea.children.add(cardWV)

        pGridScrollPane = ScrollPane(pGrid)
        pGridScrollPane.vbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
        pGridScrollPane.hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
        pGridScrollPane.style = "-fx-background-color:transparent;"
        bodyAreaPanel.children.add(pGridScrollPane)
        bodyAreaPanel.children.add(cardDisplayArea)

        panel.children.add(searchBarPanel)
        panel.children.add(bodyAreaPanel)

        cardTag.addListener { _, _, title -> updateWindowTitle(title) }

        logger.info("Initializing Word windows")
        refreshWordWindows()
        return panel
    }

    private fun loadMiniIcon(path: String): ImageView? {
        val copyResource: InputStream? = javaClass.getResourceAsStream(path)
        if (copyResource != null) {
            val image = Image(copyResource, 15.0, 15.0, true, true)
            val imageView = ImageView(image)
            if (Prefs.get().darkMode) {
                val effect = ColorAdjust()
                effect.brightness = 1.0
                imageView.effect = effect
            }
            return imageView
        }
        return null
    }

    private fun visitURL(url: String) {
        Thread {
            currentUser.visitWebsite(url)
        }.start()
    }

    fun doDeferredLoad() {
        // Button actions
        gotoUrlButton.setOnAction {
            Thread {
                try {
                    val reader = WebsiteCardCutter(urlTF.text, null)
                    this.reader = reader
                    removeWords.clear()
                    removeParagraphs.clear()

                    this.authors = reader.getAuthors() ?: this.authors
                    this.timestamp = reader.getDate()
                    this.publisher = SimpleStringProperty(reader.getPublication())
                    this.url = SimpleStringProperty(reader.getURL())
                    this.title = SimpleStringProperty(reader.getTitle() ?: "")
                    updateWindowTitle(reader.getTitle() ?: "")
                    this.cardTag.set(title.get())
                    this.cardBody.set(reader.getBodyParagraphText(true))

                    Platform.runLater {
                        visitURL(urlTF.text)
                        propertyTitleTextField.textProperty().bindBidirectional(this.title)
                        propertyPubTextField.textProperty().bindBidirectional(this.publisher)
                        propertyUrlTextField.textProperty().bindBidirectional(this.url)

                        propertyDayTF.textProperty().bindBidirectional(this.timestamp.day)
                        propertyMonthTF.textProperty().bindBidirectional(this.timestamp.month)
                        propertyYearTF.textProperty().bindBidirectional(this.timestamp.year)

                        generateAuthorGridBoxCallback(generateAuthorsGrid(generateAuthorGridBoxCallback))
                    }
                } catch (e: Exception) {
                    showErrorDialog("Error reading page: ${e.message}", "A ${e.javaClass.simpleName} exception occurred while loading $url")
                    logger.error("Error scraping page", e)
                }
            }.start()
        }

        copyBtn.setOnAction { copyCardToClipboard() }
        removeSelectedBtn.setOnAction { removeSelectedText() }
        restoreRemovedBtn.setOnAction {
            removeWords.clear()
            removeParagraphs.clear()
            refreshHTML()
            val alert = Alert(Alert.AlertType.INFORMATION)
            alert.headerText = "Article content restored to original."
            alert.showAndWait()
        }

        if (getOSType() == OS.WINDOWS) {
            val msWordInteractor = WinMSWordInteractor()
            wordWindowList.items = FXCollections.observableList(msWordInteractor.getValidWordWindows())

            if (!wordWindowList.items.isEmpty()) {
                wordWindowList.selectionModel.select(0)
            }
        } else if (getOSType() == OS.MAC) {
            val msWordInteractor = MacMSWordInteractor()
            wordWindowList.items = FXCollections.observableList(msWordInteractor.getValidWordWindows())
            if (!wordWindowList.items.isEmpty()) {
                wordWindowList.selectionModel.select(0)
            }
        }

        keepOnlySelectedBtn.setOnAction { keepOnlySelectedText() }

        editCardFormat.setOnAction { FormatPrefsWindow().show() }

        refreshBtn.setOnAction { refreshWordWindows() }

        exportBtn.setOnAction { sendCardToVerbatim() }

        urlTF.setOnKeyPressed {
            if (((it.isControlDown || it.isMetaDown) && it.text == "v") || it.code == KeyCode.ENTER) {
                Platform.runLater { gotoUrlButton.fire() }
            }
        }

        // Web view default content
        cardWV.engine.loadContent(generateDefaultHTML())

        // Generate author grid box callback
        generateAuthorGridBoxCallback = {
            pGrid.children.remove(lastUI)
            pGrid.requestLayout()
            pGrid.add(it, 1, 5)
            lastUI = it
        }
        generateAuthorGridBoxCallback(generateAuthorsGrid(generateAuthorGridBoxCallback))

        propertyTitleTextField.textProperty().bindBidirectional(this.title)
        propertyPubTextField.textProperty().bindBidirectional(this.publisher)
        propertyUrlTextField.textProperty().bindBidirectional(this.url)

        propertyDayTF.textProperty().bindBidirectional(this.timestamp.day)
        propertyMonthTF.textProperty().bindBidirectional(this.timestamp.month)
        propertyYearTF.textProperty().bindBidirectional(this.timestamp.year)

        Thread {
            logger.info("Checking login status")
            checkLoginStatus()

            logger.info("Checking for updates")
            checkForUpdates()
        }.start()
        loaded = true
    }

    fun loadMenuIcons() {
        restoreRemovedBtn.graphic = loadMiniIcon("/restore.png")
        removeSelectedBtn.graphic = loadMiniIcon("/remove.png")
        copyBtn.graphic = loadMiniIcon("/copy.png")
        refreshBtn.graphic = loadMiniIcon("/refresh.png")
        editCardFormat.graphic = loadMiniIcon("/edit.png")
        keepOnlySelectedBtn.graphic = loadMiniIcon("/keep-text.png")

//        highlightBtn.graphic = loadMiniIcon("/highlight.png")
//        emphasizeBtn.graphic = loadMiniIcon("/emphasize.png")
//        underlineBtn.graphic = loadMiniIcon("/underline.png")

        for (btn in deleteAuthorButtons) {
            btn.graphic = loadMiniIcon("/remove.png")
        }

        for (btn in searchButtons) {
            btn.graphic = loadMiniIcon("/search.png")
        }

    }

    private fun checkForUpdates() {
        UpdateChecker().checkForUpdates()
    }

    private fun checkLoginStatus() {
        if ((CardrDesktop.IS_FIRST_LAUNCH && CardrDesktop.WAS_FIRST_LAUNCH_SUCCESSFUL) || CardrDesktop.OVERRIDE_LOGIN_CHECK)
            return
        if (Prefs.get().emailAddress.isEmpty()
            || Prefs.get().accessToken.isEmpty()) {
            // Needs to sign in
            logger.info("User needs to sign in - first time")
            Platform.runLater { SignInWindow(SignInLauncherOptions.WELCOME, currentUser).show() }
        } else {
            val renewResult = currentUser.renew()
            if (!renewResult.wasSuccessful()) {
                logger.info("User needs to sign in - token expired")
                // Access token has expired
                Platform.runLater { SignInWindow(SignInLauncherOptions.TOKEN_EXPIRED, currentUser).show() }
            } else {

                logger.info("Successfully renewed login token")
            }
        }
    }

    private fun generateAuthorsGrid(regenerateUI: (GridPane) -> Unit): GridPane {
        deleteAuthorButtons.clear()

        val authorGrid = GridPane()
        authorGrid.vgap = 2.0
        authorGrid.hgap = 2.0

        val addAuthor = Button("Add Author...")
        addAuthor.prefWidth = 225.0
        authorGrid.add(addAuthor, 0, 0, 3, 1)

        var index = 0
        var uiRowIndex = 1

        for (author in authors) {
            val authorGridFName = TextField()
            authorGridFName.prefWidth = 100.0
            authorGridFName.promptText = "First name"
            authorGridFName.textProperty().bindBidirectional(author.firstName)
            bindToRefreshWebView(authorGridFName)

            val authorGridLName = TextField()
            authorGridLName.promptText = "Last name"
            authorGridLName.prefWidth = 100.0
            authorGridLName.textProperty().bindBidirectional(author.lastName)
            bindToRefreshWebView(authorGridLName)

            val deleteAuthor = Button()
            deleteAuthor.graphic = loadMiniIcon("/remove.png")
            deleteAuthorButtons.add(deleteAuthor)
            deleteAuthor.prefWidth = 25.0

            val authorGridQuals = TextField()
            authorGridQuals.promptText = "Qualifications"
            authorGridQuals.textProperty().bindBidirectional(author.qualifications)
            bindToRefreshWebView(authorGridQuals)

            val searchQuals = Button()
            searchQuals.graphic = loadMiniIcon("/search.png")
            searchButtons.add(deleteAuthor)
            searchQuals.prefWidth = 25.0

            authorGrid.add(authorGridFName, 0, uiRowIndex)
            authorGrid.add(authorGridLName, 1, uiRowIndex)
            authorGrid.add(deleteAuthor, 2, uiRowIndex)
            uiRowIndex++
            authorGrid.add(authorGridQuals, 0, uiRowIndex, 2, 1)
            authorGrid.add(searchQuals, 2, uiRowIndex)
            uiRowIndex++


            searchQuals.setOnAction {
                val name = "${authorGridFName.text} ${authorGridLName.text}".trim().replace(" ", "%20")
                if (name.isNotEmpty())
                    Desktop.getDesktop().browse(URL("https://www.google.com/search?q=$name").toURI())
            }
            val finalIndex = index
            deleteAuthor.setOnAction {
                if (authors.size > 1) {
                    val authorsMutable = authors.toMutableList()
                    authorsMutable.removeAt(finalIndex)
                    authors = authorsMutable.toTypedArray()

                    regenerateUI(generateAuthorsGrid(regenerateUI))
                    refreshHTML()
                }
            }

            index++
        }

        addAuthor.setOnAction {
            val authorsMutable = authors.toMutableList()
            authorsMutable.add(Author(SimpleStringProperty(""), SimpleStringProperty("")))
            authors = authorsMutable.toTypedArray()

            regenerateUI(generateAuthorsGrid(regenerateUI))
            refreshHTML()
        }

        return authorGrid
    }

    private fun bindToRefreshWebView(component: TextField) {
        component.textProperty().addListener(changeListenerUpdateHTML)
    }

    private fun generateDefaultHTML(): String {
        return """
            |<style>
                |body { background-color: #${if (Prefs.get().darkMode) "373e43" else "f4f4f4"}; }
            |</style>""".trimMargin()
    }

    private fun generateFullHTML(switchFont: Boolean, forCopy: Boolean, cardBodyReplacement: String?): String {
        val cite = createCite()
        val spacePlaceholder = "sas8d9f7aj523kj5h123jkhsaf"
        val doc = Jsoup.parse(Prefs.get().cardFormat.replace("&nbsp;",spacePlaceholder))

        val now = currentDate()
        val fontElements = doc.select("font")
        val fontMap = mapOf(
            Pair("1","8"),
            Pair("2","10"),
            Pair("3","11"),
            Pair("4","13"),
            Pair("5","18"),
            Pair("6","24"),
            Pair("7","36")
        )

        for (elem in doc.select("span")) {
            if (elem.hasAttr("style") && !elem.attr("style").contains("font-size")) {
                elem.attr("style","${elem.attr("style")}font-size:${fontMap["3"]}pt;")
            }
        }

        for (font in fontElements) {
            var parent = font.parent()
            while (!parent.tagName().equals("p") && !parent.tagName().equals("b") && !parent.tagName().matches(Regex("h."))) {
                parent = parent.parent()
            }
            var style = ""
            if (font.hasAttr("face"))
                style += "font-family:'${font.attr("face")}';"
            if (!font.hasAttr("size"))
                 font.attr("size", "3") // 12pt font
            style += "font-size:${fontMap[font.attr("size")]}pt;"
            font.tagName("span")
            font.attr("style",style)
            font.removeAttr("face")
            font.removeAttr("size")
        }

        for (elem in doc.allElements) {
            if (elem.children().size > 0 && elem.ownText().length == 0)
                continue
            val cardBody = cardBodyReplacement ?: getCardBodyHTML(cardBody.get(), true)
            elem.html(
                elem.html()
                    .replace("{AuthorLastName}", cite.getAuthorName(AuthorNameFormat.LAST_NAME))
                    .replace("{DateShortened}", cite.date.toString(false))
                    .replace("{AuthorFullName}", cite.getAuthorName(AuthorNameFormat.FULL_NAME))
                    .replace("{AuthorFirstName}", cite.getAuthorName(AuthorNameFormat.FIRST_NAME))
                    .replace("{Qualifications}", cite.getAuthorQualifications())
                    .replace("{DateFull}", cite.date.toString(true))
                    .replace("{CurrentDate}", "${now.monthValue}${Timestamp.getSeparator()}${now.dayOfMonth}${Timestamp.getSeparator()}${now.year}")
                    .replace("{Publication}", cite.publication)
                    .replace("{Title}", cite.title)
                    .replace("{Url}", cite.url)
                    .replace("{Tag}", cardTag.value)
                    .replace("{CardBody}", cardBody)
            )
        }

        doc.select("head")[0].html("""
            <style>
                body { font-family: 'System'; font-size: 11pt;
                ${if (Prefs.get().darkMode && !forCopy) {
                    "background-color: #373e43; color: #ffffff;"
                } else "background-color: #f4f4f4;"}
            </style> 
            <script>
                function getSelectionTextCustom() {
                    var text = "";
                    if (window.getSelection) {
                        text = window.getSelection().toString();
                    } else if (document.selection && document.selection.type != "Control") {
                        text = document.selection.createRange().text;
                    }
                    return text;
                }
                
                function highlightSelectedText(color) {
                    var range, sel = window.getSelection();
                    if (sel.rangeCount && sel.getRangeAt) {
                        range = sel.getRangeAt(0);
                    };
                    document.designMode = "on";
                    if (range) {
                        sel.removeAllRanges();
                        sel.addRange(range);
                    };
                    if (!document.execCommand("HiliteColor", false, color)) {
                        document.execCommand("BackColor", false, color);
                    };
                    document.designMode = "off";
                }
                
                function boldSelectedText() {
                    var range, sel = window.getSelection();
                    if (sel.rangeCount && sel.getRangeAt) {
                        range = sel.getRangeAt(0);
                    };
                    document.designMode = "on";
                    if (range) {
                        sel.removeAllRanges();
                        sel.addRange(range);
                    };
                    document.execCommand("bold", false);
                    document.designMode = "off";
                }
                
                function underlineSelectedText() {
                    var range, sel = window.getSelection();
                    if (sel.rangeCount && sel.getRangeAt) {
                        range = sel.getRangeAt(0);
                    };
                    document.designMode = "on";
                    if (range) {
                        sel.removeAllRanges();
                        sel.addRange(range);
                    };
                    document.execCommand("underline", false);
                    document.designMode = "off";
                }
            </script>
        """.trimIndent())

        for (elem in doc.select("p")) {
            val oldStyle = elem.parent().attr("style")
            elem.attr("style", "$oldStyle${if (oldStyle.contains("font-size:11pt;")) { "line-height:20px;" } else { "" }}margin: 1px 0px 12px 0px; padding: 0px 0px 0px 0px;")
        }
        for (elem in doc.select("h4")) {
            elem.attr("style", "padding: 0px 0px 0px 0px; margin: 0px 0px 0px 0px;")
        }

        var docHtml = doc.html().replace(spacePlaceholder, "&nbsp;")
        if (switchFont && getOSType() == OS.MAC) {
            docHtml = docHtml.replace("font-family:'${PrefsObject.MAC_CALIBRI_FONT}';", "")
        }

        if (Prefs.get().showParagraphBreaks && forCopy)
            docHtml = docHtml.replace("¶ ", "")

        return docHtml
    }

    private fun getCardBodyHTML(cardBody: String, cardBodyIsHTML: Boolean): String {
        var out = cardBody

        val paragraphSuffix = if (Prefs.get().showParagraphBreaks) "¶ " else ""

        if (cardBodyIsHTML) {
            if (Prefs.get().condense) {
                out = "<p class='cardbody'>${cardBody.replace("<p>", "").replace("</p>", paragraphSuffix)}</p>"
            } else {
                out = cardBody.replace("<p>", "<p class='cardbody'>").replace("</p>", "$paragraphSuffix</p>")
            }
        }

        for (remove in removeWords) {
            out = out.replace(remove, "")
        }

        for (remove in removeParagraphs) {
            out = out.replace(remove, "")
        }

        if (Prefs.get().showParagraphBreaks && cardBodyIsHTML) {
            val paragraphBegin = if (Prefs.get().condense) "" else "<p class='cardbody'>"
            val paragraphEnd = if (Prefs.get().condense) "" else "</p>"
            while (out.contains("$paragraphBegin ¶ $paragraphEnd$paragraphBegin ¶ $paragraphEnd"))
                out = out.replace("$paragraphBegin ¶ $paragraphEnd$paragraphBegin ¶ $paragraphEnd", "$paragraphBegin ¶ $paragraphEnd")
            if (out.startsWith("<p class='cardbody'> ¶ "))
                out = "<p class='cardbody'>" + out.substring("<p class='cardbody'> ¶ ".length)
            if (out.endsWith("$paragraphBegin ¶ $paragraphEnd"))
                out = out.substring(0, out.length - "$paragraphBegin ¶ $paragraphEnd".length)
            if (out.endsWith("¶ </p>"))
                out = out.substring(0, out.length - "¶ </p>".length)
        }

        while (out.contains("  "))
            out = out.replace("  ", " ")
        while (out.contains("\n \n"))
            out = out.replace("\n \n", "\n")
        while (out.startsWith(" ") || out.startsWith("\n"))
            out = out.substring(1)
        while (out.startsWith(" \n") || out.startsWith("\n "))
            out = out.substring(2)
        while (out.endsWith(" "))
            out = out.substring(0, out.length - 1)
        while (out.endsWith(" \n") || out.endsWith("\n "))
            out = out.substring(0, out.length - 2)
        return out
    }

    fun onWindowResized() {
        urlTF.prefWidth = stage.width - 50
        cardWV.prefWidth = stage.width - 325
        cardWV.prefHeight = stage.height - 150
        pGrid.prefHeight = stage.height - 150
    }

    fun refreshHTML() {
        Platform.runLater {
            cardWV.engine?.loadContent(generateFullHTML(switchFont = false, forCopy = false, cardBodyReplacement = null))
        }
    }

    fun loadFromReader(reader: WebsiteCardCutter) {
        this.reader = reader

        Platform.runLater {
            this.urlTF.text = reader.getURL()
            this.authors = reader.getAuthors() ?: this.authors
            this.timestamp = reader.getDate()
            this.publisher = SimpleStringProperty(reader.getPublication())
            this.url = SimpleStringProperty(reader.getURL())
            this.title = SimpleStringProperty(reader.getTitle() ?: "")
            updateWindowTitle(reader.getTitle() ?: "")

            this.cardTag.set(title.get())
            this.cardBody.set(reader.getBodyParagraphText(true))

            propertyTitleTextField.textProperty().bindBidirectional(this.title)
            propertyPubTextField.textProperty().bindBidirectional(this.publisher)
            propertyUrlTextField.textProperty().bindBidirectional(this.url)

            propertyDayTF.textProperty().bindBidirectional(this.timestamp.day)
            propertyMonthTF.textProperty().bindBidirectional(this.timestamp.month)
            propertyYearTF.textProperty().bindBidirectional(this.timestamp.year)

            generateAuthorGridBoxCallback(generateAuthorsGrid(generateAuthorGridBoxCallback))
        }

    }

    private fun highlightSelectedText() {
        cardWV.engine.executeScript("highlightSelectedText('#ffff00')")
    }

    private fun boldSelectedText() {
        cardWV.engine.executeScript("boldSelectedText()")
    }

    private fun underlineSelectedText() {
        cardWV.engine.executeScript("underlineSelectedText()")
    }


    fun removeSelectedText() {
        var success = false
        try {
            val selection = cardWV.engine.executeScript("getSelectionTextCustom()") as String
            for (str in selection.split(Regex("[\\n\\t\\r]"))) {
                if (str.isNotBlank()) {
                    removeWords.add(str)
                    success = true
                }
            }
            refreshHTML()
        } catch (e: Exception) {
            success = false
        }
        if (!success) {
            val alert = Alert(Alert.AlertType.INFORMATION, "Please highlight text in the preview pane before clicking remove.")
            alert.headerText = "No text selected"
            alert.showAndWait()
        }
    }

    fun refreshWordWindows() {
        if (getOSType() == OS.WINDOWS){
            wordWindowList.items = FXCollections.observableList(WinMSWordInteractor().getValidWordWindows())
            if (!wordWindowList.items.isEmpty()) {
                wordWindowList.selectionModel.select(0)
            }
        } else if (getOSType() == OS.MAC){
            wordWindowList.items = FXCollections.observableList(MacMSWordInteractor().getValidWordWindows())
            if (!wordWindowList.items.isEmpty()) {
                wordWindowList.selectionModel.select(0)
            }
        }
    }

    fun keepOnlySelectedText() {
        var success = false
        try {
            if (reader == null) {
                throw NullPointerException("No reader found")
            }
            keepOnlyText(cardWV.engine.executeScript("getSelectionTextCustom()") as String)
            success = true
        } catch (e: Exception) {
            logger.error(e)
            e.printStackTrace()
        }

        if (!success) {
            val alert = Alert(Alert.AlertType.INFORMATION, "Please highlight at least one full paragraph in the preview pane in order to use this tool.")
            alert.headerText = "Not enough text selected"
            alert.showAndWait()
        }
    }

    @Throws(Exception::class)
    fun keepOnlyText(text: String) {
        removeParagraphs.clear()

        var selection = text
            .replace("\n\n", " ")
            .replace("¶ ", "").trim()

        while (selection.contains("  "))
            selection = selection.replace("  ", " ")

        val paragraphs = reader!!.getBodyParagraphs().map { it.text() }.toMutableList()
        var firstIndex = -1
        var lastIndex = -1

        for (i in paragraphs.indices) {
            val paragraph = paragraphs[i]

            if (!selection.contains(paragraph)) {
                if (firstIndex != -1 && lastIndex == -1)
                    lastIndex = i - 1
            } else if (paragraph.isNotBlank()) {
                if (firstIndex == -1) {
                    firstIndex = i
                }
            }
        }

        if (firstIndex != -1 && lastIndex == -1)
            lastIndex = paragraphs.size - 1

        if (firstIndex == -1 && lastIndex == -1)
            throw ArrayIndexOutOfBoundsException("You must highlight at least one full paragraph in the webpage.")

        val placeholder = "asfda8sdfaweh25k3h21klsamnfi5"
        var selectionOutsides = selection
        for (i in firstIndex..lastIndex) {
            selectionOutsides = selectionOutsides.replace(paragraphs[i], placeholder)
        }

        while (selectionOutsides.contains("$placeholder$placeholder"))
            selectionOutsides = selectionOutsides.replace("$placeholder$placeholder", placeholder)

        val beforeAfterSelection = selectionOutsides.split(placeholder)
        if (firstIndex != 0) {
            paragraphs[firstIndex - 1] = paragraphs[firstIndex - 1].replace(beforeAfterSelection[0].trim(), "")
            removeParagraphs.add(paragraphs[firstIndex - 1])
        }

        if (lastIndex != paragraphs.size - 1) {
            paragraphs[lastIndex + 1] = paragraphs[lastIndex + 1].replace(beforeAfterSelection[beforeAfterSelection.size - 1].trim(), "")
            removeParagraphs.add(paragraphs[lastIndex + 1])
        }

        for (i in 0 until firstIndex) {
            removeParagraphs.add(paragraphs[i])
        }

        for (i in lastIndex + 1 until paragraphs.size) {
            removeParagraphs.add(paragraphs[i])
        }
        refreshHTML()
    }

    fun copyCardToClipboard() {
        Toolkit.getDefaultToolkit()
            .systemClipboard
            .setContents(
                HTMLSelection(
                    generateFullHTML(switchFont = true, forCopy = true, cardBodyReplacement = null)
                ),
                null
            )
    }

    fun sendCardToVerbatim() {
        if (reader == null)
            return

        if (wordWindowList.items.size == 0)
            refreshWordWindows()

        if (getOSType() == OS.WINDOWS){
            val msWord = WinMSWordInteractor()
            if (wordWindowList.items.size > 0) {
                msWord.selectWordWindowByDocName(wordWindowList.selectionModel.selectedItem)
            }
        } else if (getOSType() == OS.MAC){
            val msWord = MacMSWordInteractor()
            if (wordWindowList.items.size > 0) {
                msWord.selectWordWindowByDocName(wordWindowList.selectionModel.selectedItem)
            }
        }

        if (Prefs.get().pastePlainText) {
            val cardBodyReplacement = "safd7asdyfkjahnw3k5nsd"
            val cardHtml = generateFullHTML(switchFont = true, forCopy = true, cardBodyReplacement = cardBodyReplacement)
            val cardBodyIndex = cardHtml.indexOf(cardBodyReplacement)
            val beforeBody = cardHtml.substring(0, cardBodyIndex)
            var body = getCardBodyHTML(reader!!.getBodyParagraphText(false), false)
            if (body.endsWith("\n"))
                body += "\n"
            val afterBody = cardHtml.substring(cardBodyIndex + cardBodyReplacement.length)

            pasteObject(beforeBody, KeyboardPasteMode.NORMAL)
            pasteObject(body, KeyboardPasteMode.PLAIN_TEXT)
            println(body)
            if (afterBody != "</span></p>\n </body>\n</html>")
                pasteObject(afterBody, KeyboardPasteMode.NORMAL)
        } else {
            pasteObject(generateFullHTML(switchFont = true, forCopy = true, cardBodyReplacement = null), KeyboardPasteMode.NORMAL)
        }
    }

    private fun updateWindowTitle(title: String) {
        Platform.runLater {
            var trimmed = title.substring(0, Math.min(title.length, 100))
            if (title.length >= 100)
                trimmed += "..."
            stage.title = "$trimmed - cardr ${CardrDesktop.CURRENT_VERSION}"
        }
    }

    private fun createCite() = Cite(
        authors,
        timestamp,
        title.get(),
        publisher.get(),
        url.get()
    )

    companion object {
        private val logger = LogManager.getLogger(CardrUI::class.java)
    }
}