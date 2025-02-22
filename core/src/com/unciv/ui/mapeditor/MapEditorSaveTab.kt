package com.unciv.ui.mapeditor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.logic.MapSaver
import com.unciv.logic.map.MapType
import com.unciv.logic.map.TileMap
import com.unciv.models.translations.tr
import com.unciv.ui.popup.Popup
import com.unciv.ui.popup.ToastPopup
import com.unciv.ui.popup.YesNoPopup
import com.unciv.ui.utils.*
import kotlin.concurrent.thread

class MapEditorSaveTab(
    private val editorScreen: MapEditorScreen,
    headerHeight: Float
): Table(BaseScreen.skin), TabbedPager.IPageExtensions {
    private val mapFiles = MapEditorFilesTable(
        initWidth = editorScreen.getToolsWidth() - 40f,
        includeMods = false,
        this::selectFile)

    private val saveButton = "Save map".toTextButton()
    private val deleteButton = "Delete map".toTextButton()
    private val mapNameTextField = TextField("", skin)

    private var chosenMap: FileHandle? = null

    init {
        mapNameTextField.maxLength = 100
        mapNameTextField.textFieldFilter = TextField.TextFieldFilter { _, char -> char != '\\' && char != '/' }
        mapNameTextField.selectAll()
        // do NOT take the keyboard focus here! We're not even visible.
        add(mapNameTextField).pad(10f).fillX().row()

        val buttonTable = Table(skin)
        buttonTable.defaults().pad(10f).fillX()
        saveButton.onClick(this::saveHandler)
        mapNameTextField.onChange {
            saveButton.isEnabled = mapNameTextField.text.isNotBlank()
        }
        buttonTable.add(saveButton)

        deleteButton.onClick(this::deleteHandler)
        buttonTable.add(deleteButton)
        buttonTable.pack()

        val fileTableHeight = editorScreen.stage.height - headerHeight - mapNameTextField.prefHeight - buttonTable.height - 22f
        val scrollPane = AutoScrollPane(mapFiles, skin)
        scrollPane.setOverscroll(false, true)
        add(scrollPane).height(fileTableHeight).fillX().row()
        add(buttonTable).row()
    }

    private fun saveHandler() {
        if (mapNameTextField.text.isBlank()) return
        editorScreen.tileMap.mapParameters.name = mapNameTextField.text
        editorScreen.tileMap.mapParameters.type = MapType.custom
        thread(name = "MapSaver", block = this::saverThread)
    }

    private fun deleteHandler() {
        if (chosenMap == null) return
        YesNoPopup("Are you sure you want to delete this map?", {
            chosenMap!!.delete()
            mapFiles.update()
        }, editorScreen).open()
    }

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        pager.setScrollDisabled(true)
        mapFiles.update()
        editorScreen.keyPressDispatcher[KeyCharAndCode.RETURN] = this::saveHandler
        editorScreen.keyPressDispatcher[KeyCharAndCode.DEL] = this::deleteHandler
        editorScreen.keyPressDispatcher[Input.Keys.UP] = { mapFiles.moveSelection(-1) }
        editorScreen.keyPressDispatcher[Input.Keys.DOWN] = { mapFiles.moveSelection(1) }
        selectFile(null)
    }

    override fun deactivated(index: Int, caption: String, pager: TabbedPager) {
        editorScreen.keyPressDispatcher.revertToCheckPoint()
        pager.setScrollDisabled(false)
        stage.keyboardFocus = null
    }

    fun selectFile(file: FileHandle?) {
        chosenMap = file
        mapNameTextField.text = file?.name() ?: editorScreen.tileMap.mapParameters.name
        if (mapNameTextField.text.isBlank()) mapNameTextField.text = "My new map".tr()
        mapNameTextField.setSelection(Int.MAX_VALUE, Int.MAX_VALUE)  // sets caret to end of text
        stage.keyboardFocus = mapNameTextField
        saveButton.isEnabled = true
        deleteButton.isEnabled = (file != null)
        deleteButton.color = if (file != null) Color.SCARLET else Color.BROWN
    }

    private fun saverThread() {
        try {
            val mapToSave = editorScreen.getMapCloneForSave()
            mapToSave.assignContinents(TileMap.AssignContinentsMode.Reassign)
            MapSaver.saveMap(mapNameTextField.text, mapToSave)
            Gdx.app.postRunnable {
                ToastPopup("Map saved successfully!", editorScreen)
            }
            editorScreen.isDirty = false
        } catch (ex: Exception) {
            ex.printStackTrace()
            Gdx.app.postRunnable {
                val cantLoadGamePopup = Popup(editorScreen)
                cantLoadGamePopup.addGoodSizedLabel("It looks like your map can't be saved!").row()
                cantLoadGamePopup.addCloseButton()
                cantLoadGamePopup.open(force = true)
            }
        }
    }
}