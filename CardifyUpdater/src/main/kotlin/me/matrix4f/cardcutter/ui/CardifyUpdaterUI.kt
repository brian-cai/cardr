package me.matrix4f.cardcutter.ui

import javafx.application.Platform
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.control.ProgressBar
import javafx.scene.image.Image
import javafx.scene.layout.VBox
import javafx.scene.text.Font
import javafx.stage.Stage
import javafx.stage.WindowEvent
import me.matrix4f.cardcutter.updater.CardifyVersionData
import me.matrix4f.cardcutter.updater.UpdateExecutor
import org.apache.logging.log4j.LogManager

class CardifyUpdaterUI(private val stage: Stage, private val version: CardifyVersionData) {

    private val box = VBox()
    private val subheader = Label("Ready to install Cardify ${version.cardifyVersion.name}?")
    private val updater = UpdateExecutor(version)
    private val updaterThread = Thread {
        updater.update()
    }

    private fun close(@Suppress("UNUSED_PARAMETER") event: WindowEvent?) {
        if (updaterThread.isAlive)
            updaterThread.interrupt()
    }

    fun startUpdate() {
        box.children.remove(subheader)

        val status = Label("Installing update...")
        status.isWrapText = true
        val progressBar = ProgressBar()
        progressBar.prefWidth = 225.0

        box.children.add(progressBar)
        box.children.add(status)

        updater.messageHandler = {
            Platform.runLater {
                status.text = it
                logger.info("Message: $it")
            }
        }
        updater.onClose = {
            close(null)
        }
        updaterThread.start()
    }

    fun initialize(): Scene {
        stage.setOnCloseRequest(this::close)

        box.spacing = 5.0
        box.padding = Insets(10.0)

        val header = Label("Update to ${version.cardifyVersion.name}")
        header.font = Font.font(18.0)

        subheader.font = Font.font(14.0)
        subheader.isWrapText = true

        box.children.add(header)
        box.children.add(subheader)

        val scene = Scene(box, WIDTH, HEIGHT)
        stage.icons.add(Image(javaClass.getResourceAsStream("/icon-128.png")))
        return scene
    }

    companion object {
        const val WIDTH = 250.0
        const val HEIGHT = 100.0

        val logger = LogManager.getLogger(CardifyUpdaterUI::class.java)
    }
}