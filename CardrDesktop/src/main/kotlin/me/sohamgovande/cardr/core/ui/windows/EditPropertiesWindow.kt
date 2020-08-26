package me.sohamgovande.cardr.core.ui.windows

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.layout.VBox
import javafx.scene.text.Font
import javafx.scene.text.Text
import javafx.scene.text.TextFlow
import me.sohamgovande.cardr.core.ui.property.CardProperty
import me.sohamgovande.cardr.core.ui.tabs.EditCardTabUI
import me.sohamgovande.cardr.core.ui.tabs.TabUI
import me.sohamgovande.cardr.data.prefs.Prefs
import me.sohamgovande.cardr.util.OS
import me.sohamgovande.cardr.util.getOSType


class EditPropertiesWindow(private val currentTab: EditCardTabUI) : ModalWindow("Customize Properties Editor", isModal = false) {

    private val table = TableView<CardProperty>()

    private var enabledGrid = BooleanArray(currentTab.propertyManager.cardProperties.size)

    override fun generateUI(): Scene {
        val vbox = VBox()
        vbox.padding = Insets(10.0)
        vbox.spacing = 10.0

        table.items = FXCollections.observableList(currentTab.propertyManager.cardProperties)
        for (index in Prefs.get().activeProperties)
            enabledGrid[index] = true

        val colName = TableColumn<CardProperty, String>("Property Name")
        val colEnabled = TableColumn<CardProperty, Boolean>("")
        val colMacros = TableColumn<CardProperty, String>("Associated Macros")
        val colMoveUp = TableColumn<CardProperty, Unit?>("")
        val colMoveDown = TableColumn<CardProperty, Unit?>("")

        colName.minWidth = 100.0
        colName.maxWidth = 100.0

        val width = 35.0 + if (isMac()) 5.0 else 0.0
        colMoveUp.minWidth = width
        colMoveUp.maxWidth = width
        colMoveDown.minWidth = width
        colMoveDown.maxWidth = width
        colEnabled.minWidth = width
        colEnabled.maxWidth = width

        table.columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY

        colName.setCellValueFactory { SimpleStringProperty(it.value.name) }
        colMacros.setCellValueFactory { SimpleStringProperty(it.value.macros.joinToString()) }
        colEnabled.setCellValueFactory {
            SimpleBooleanProperty(Prefs.get().activeProperties.contains(currentTab.propertyManager.cardProperties.indexOf(it.value)))
        }
        colEnabled.setCellFactory { object : TableCell<CardProperty, Boolean>() {
            private val btn = Button()

            fun loadGraphic() {
                val cIndex = currentTab.propertyManager.cardProperties.indexOf(table.items[index])
                btn.graphic = TabUI.loadMiniIcon(
                    if (enabledGrid[cIndex]) "/visible.png" else "/hidden.png", false, 1.0
                )
            }

            override fun updateItem(item: Boolean?, empty: Boolean) {
                super.updateItem(item, empty)

                if (item == null || empty) {
                    text = null
                    graphic = null
                } else {
                    val cIndex = currentTab.propertyManager.cardProperties.indexOf(table.items[index])
                    btn.setOnAction {
                        enabledGrid[cIndex] = !enabledGrid[cIndex]
                        loadGraphic()
                        updatePrefs()
                        currentTab.refreshPropertiesGrid()
                    }
                    loadGraphic()
                    graphic = btn
                    text = null
                }
            }
        }}

        colMoveUp.setCellValueFactory { SimpleObjectProperty(Unit) }
        colMoveUp.setCellFactory { object : TableCell<CardProperty, Unit?>() {
            private val btn = Button()

            override fun updateItem(item: Unit?, empty: Boolean) {
                super.updateItem(item, empty)

                if (item == null || empty) {
                    text = null
                    graphic = null
                } else {
                    btn.setOnAction {
                        val modify = table.items
                        val temp = modify[index-1]
                        modify[index-1] = modify[index]
                        modify[index] = temp
                        table.items = modify

                        val temp2 = enabledGrid[index-1]
                        enabledGrid[index-1] = enabledGrid[index]
                        enabledGrid[index] = temp2

                        table.selectionModel.select(index-1)

                        updatePrefs()
                        currentTab.refreshPropertiesGrid()
                    }
                    btn.graphic = TabUI.loadMiniIcon("/up.png", false, 1.0)
                    btn.isDisable = index == 0
                    graphic = btn
                    text = null
                }
            }
        }}

        colMoveDown.setCellValueFactory { SimpleObjectProperty(Unit) }
        colMoveDown.setCellFactory { object : TableCell<CardProperty, Unit?>() {
            private val btn = Button()

            override fun updateItem(item: Unit?, empty: Boolean) {
                super.updateItem(item, empty)

                if (item == null || empty) {
                    text = null
                    graphic = null
                } else {
                    btn.setOnAction {
                        val modify = table.items
                        val temp = modify[index+1]
                        modify[index+1] = modify[index]
                        modify[index] = temp
                        table.items = modify

                        val temp2 = enabledGrid[index+1]
                        enabledGrid[index+1] = enabledGrid[index]
                        enabledGrid[index] = temp2

                        table.selectionModel.select(index+1)

                        updatePrefs()
                        currentTab.refreshPropertiesGrid()
                    }
                    btn.graphic = TabUI.loadMiniIcon("/down.png", false, 1.0)
                    btn.isDisable = index == table.items.size-1
                    graphic = btn
                    text = null
                }
            }
        }}

        table.columns.addAll(colMoveUp, colMoveDown, colEnabled, colName, colMacros)
        for (col in table.columns)
            col.isSortable = false

        table.fixedCellSize = 30.0 + if (isMac()) 5.0 else 0.0
        table.prefHeight = table.fixedCellSize * (table.items.size + 1)

        val header = Label("Customize Properties Editor")
        header.font = Font.font(20.0)

        val text = Text("Here, you can change the order of properties and show/hide them in the properties editor. All your changes will be reflected live in the properties editor. You can use the Associated Macros in Settings > Edit Card & Cite Format.")
        text.styleClass.add("custom-text")
        val subheader = TextFlow(text)
        subheader.prefWidth = 600.0

        val done = Button("Done")
        done.setOnAction { close(null) }

        vbox.children.addAll(header, subheader, table, done)

        val scene = Scene(vbox)
        scene.stylesheets.add(javaClass.getResource(Prefs.get().getStylesheet()).toExternalForm())
        super.window.icons.add(Image(javaClass.getResourceAsStream("/icon-128.png")))
        return scene
    }

    fun updatePrefs() {
        val indices = mutableListOf<Int>()

        for ((index, value) in enabledGrid.withIndex()) {
            val cIndex = currentTab.propertyManager.cardProperties.indexOf(table.items[index])
            if (value)
                indices.add(cIndex)
        }

        Prefs.get().activeProperties = indices
        Prefs.save()
    }
}