package me.sohamgovande.cardr.core.ui.windows

import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.Text
import javafx.scene.text.TextFlow
import javafx.scene.web.HTMLEditor
import javafx.stage.WindowEvent
import me.sohamgovande.cardr.CardrDesktop
import me.sohamgovande.cardr.core.ui.CardrUI
import me.sohamgovande.cardr.core.ui.property.CardProperty
import me.sohamgovande.cardr.core.ui.property.CardPropertyManager
import me.sohamgovande.cardr.core.ui.tabs.EditCardTabUI
import me.sohamgovande.cardr.data.prefs.Prefs
import me.sohamgovande.cardr.data.prefs.PrefsObject
import me.sohamgovande.cardr.util.OS
import me.sohamgovande.cardr.util.getOSType
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

class FormatPrefsWindow(private val cardrUI: CardrUI, private val propertyManager: CardPropertyManager): ModalWindow("Settings - Card Format") {

    private val editText = HTMLEditor()

    init {
        window.widthProperty().addListener {_, _, _ -> onWindowResized()}
        window.heightProperty().addListener {_, _, _ -> onWindowResized()}
    }

    override fun close(event: WindowEvent?) {
        if (!forcedClose) {
            Prefs.get().cardFormat = editText.htmlText.replace("contenteditable=\"true\"", "")
            Prefs.save()
        }
        super.close(event)
    }

    private fun onWindowResized() {
        editText.prefWidth = window.width
    }

    private fun generateFontSizeNote(): TextFlow {
        val text0 = Text("Font Size: ")
        val text1 = Text("If you select font sizes 12pt or 14pt, they will be automatically converted into 11pt and 13pt, respectively, when the card is transferred to Word or Google Docs. To override this and continue using 12pt and 14pt font sizes, please ")
        val text2 = Text("STRIKETHROUGH")
        val text3 = Text(" all 12pt and 14pt text that you wish should keep its given font size.\n")
        val text4 = Text("Font Family: ")
        val text5 = Text("On macOS, in the editor window, please use Arial instead of Calibri. Arial will automatically convert to Calibri when pasting to Word or Google Docs.")
        text0.style = "-fx-font-weight: bold;"
        text2.isStrikethrough = true
        text4.style = "-fx-font-weight: bold;"
        val textFlow = TextFlow(text0, text1, text2, text3, text4, text5)
        for (testNode in textFlow.children) {
            if (testNode !is Text)
                continue
            testNode.styleClass.add("custom-text")
        }
        return textFlow
    }

    override fun generateUI(): Scene {
        val vbox = VBox()
        vbox.padding = Insets(10.0)
        vbox.spacing = 10.0

        val header = Label("Card and Cite Formatting Settings")
        header.font = Font.font(20.0)

        val subheader = Label("A Quick Note on Fonts")
        subheader.font = Font.font(15.0)
        subheader.style = "-fx-font-weight: bold;"
        val note = generateFontSizeNote()

        val resetBtn = Button("Reset to Default")
        val infoBtn = Button("Macro List")
        val btnHbox = HBox()
        btnHbox.spacing = 10.0
        btnHbox.children.add(resetBtn)
        btnHbox.children.add(infoBtn)

        editText.htmlText = Prefs.get().cardFormat
        editText.prefWidth = 600.0
        editText.maxHeight = 225.0
        editText.padding = Insets(1.0)
        if (Prefs.get().darkMode)
            editText.border = Border(BorderStroke(Color.web("#222"), BorderStrokeStyle.SOLID, CornerRadii.EMPTY, BorderWidths.DEFAULT))
        else
            editText.border = Border(BorderStroke(Color.web("#ddd"), BorderStrokeStyle.SOLID, CornerRadii.EMPTY, BorderWidths.DEFAULT))
        editText.style = "-fx-font-size: 14.0; -fx-font-family: 'Calibri';"

        val editHBox = HBox()
        editHBox.children.add(editText)
        editHBox.prefWidth = 600.0

        resetBtn.setOnAction {
            var new = PrefsObject.DEFAULT_CARD_FORMAT
            if (isMac())
                new = new.replace("Calibri", PrefsObject.MAC_CALIBRI_FONT)
            editText.htmlText = new
        }
        infoBtn.setOnAction {
            val alert = Alert(Alert.AlertType.NONE)
            alert.dialogPane.stylesheets.add(CardrDesktop::class.java.getResource(Prefs.get().getStylesheet()).toExternalForm())
            alert.title = "Macros"
            alert.headerText = "Available Macros"
            alert.buttonTypes.add(ButtonType.CLOSE)

            val macroList = arrayListOf("{CardBody}")
            for (property in propertyManager.cardProperties)
                for (macro in property.macros)
                    macroList.add(macro)

            macroList.sort()

            val list = ListView(FXCollections.observableList(macroList))

            val copyBtn = Button("Copy")
            copyBtn.prefWidth = 250.0
            copyBtn.setOnAction {
                if (list.selectionModel.selectedIndex != -1) {
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(
                        StringSelection(list.selectionModel.selectedItem),
                        null
                    )
                    alert.close()
                }
            }

            val view = VBox()
            view.children.add(list)
            view.children.add(copyBtn)

            list.prefHeight = 150.0
            alert.dialogPane.content = view
            alert.show()
        }

        val applyBtn = Button("Apply")
        applyBtn.requestFocus()
        applyBtn.setOnAction {
            window.onCloseRequest.handle(null)
        }

        vbox.children.add(header)
        vbox.children.add(subheader)
        vbox.children.add(note)
        vbox.children.add(editHBox)
        vbox.children.add(btnHbox)
        vbox.children.add(applyBtn)

        val scene = Scene(vbox, 650.0, 500.0)
        scene.stylesheets.add(javaClass.getResource(Prefs.get().getStylesheet()).toExternalForm())
        super.window.icons.add(Image(javaClass.getResourceAsStream("/icon-128.png")))
        return scene
    }
}