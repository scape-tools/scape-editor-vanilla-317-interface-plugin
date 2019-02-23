package plugin

import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.collections.transformation.FilteredList
import javafx.collections.transformation.SortedList
import javafx.embed.swing.SwingFXUtils
import javafx.fxml.FXML
import javafx.scene.canvas.Canvas
import javafx.scene.control.*
import javafx.scene.image.ImageView
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Pane
import org.imgscalr.Scalr
import scape.editor.fs.graphics.RSWidget
import scape.editor.fs.graphics.draw.RSRaster
import scape.editor.fx.TupleCellFactory
import scape.editor.gui.controller.BaseController
import scape.editor.gui.event.LoadInterfaceEvent
import scape.editor.gui.model.InterfaceWrapper
import scape.editor.gui.model.NamedValueModel
import scape.editor.gui.model.ValueModel
import scape.editor.gui.plugin.PluginManager
import scape.editor.gui.util.setColorTransparent
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.lang.reflect.Modifier
import java.net.URL
import java.util.*

class InterfaceController : BaseController() {

    val data = FXCollections.observableArrayList<NamedValueModel>()

    lateinit var interfaceView: TreeTableView<InterfaceWrapper>

    lateinit var idCol: TreeTableColumn<InterfaceWrapper, Number>

    lateinit var typeCol: TreeTableColumn<InterfaceWrapper, String>

    lateinit var previewCol: TreeTableColumn<InterfaceWrapper, InterfaceWrapper>

    lateinit var editPane : Pane

    lateinit var nameCol : TableColumn<NamedValueModel, String>
    lateinit var valueCol : TableColumn<NamedValueModel, ValueModel>

    lateinit var propertyTf: TextField

    lateinit var propertyView : TableView<NamedValueModel>

    val rootLayer : Canvas = Canvas()

    val uiLayer = Canvas()

    override fun initialize(location: URL?, resources: ResourceBundle?) {

        rootLayer.widthProperty().bind(editPane.widthProperty())
        rootLayer.heightProperty().bind(editPane.heightProperty())
        uiLayer.widthProperty().bind(editPane.widthProperty())
        uiLayer.heightProperty().bind(editPane.heightProperty())

        interfaceView.root = TreeItem(InterfaceWrapper(-1, -1, RSWidget(-1)))
        idCol.setCellValueFactory { it -> SimpleIntegerProperty(it.value.value.id)}
        typeCol.setCellValueFactory { it -> SimpleStringProperty(it.value.value.getTypeName())}
        previewCol.setCellValueFactory { it -> SimpleObjectProperty(it.value.value) }
        previewCol.setCellFactory { _ -> object: TreeTableCell<InterfaceWrapper, InterfaceWrapper?>() {

            private val imageView = ImageView()

            override fun updateItem(wrapper: InterfaceWrapper?, empty: Boolean) {
                super.updateItem(wrapper, empty)
                text = ""

                if (wrapper == null) {
                    graphic = null
                    return
                }

                if (!empty) {
                    var bimage = toBufferedImage(InterfaceWrapper.widgets, wrapper.widget)

                    if (bimage == null || bimage.width <= 0 || bimage.height <= 0) {
                        graphic = null
                        return
                    }

                    if (bimage.width > 64 || bimage.height > 64) {
                        bimage = Scalr.resize(bimage, 64)
                    }

                    imageView.image = SwingFXUtils.toFXImage(bimage?.setColorTransparent(java.awt.Color.BLACK), null)
                }

                graphic = if (empty) null else imageView
            }
        } }

        interfaceView.selectionModel.selectedItemProperty().addListener { _, _, newValue ->
            newValue ?: return@addListener
            val widget = newValue.value.widget

            clearEditCanvas()
            renderOnCanvas(newValue)

            data.clear()

            for (field in widget::class.java.fields) {

                if (Modifier.isStatic(field.modifiers)) {
                    continue
                }

                val name = field.name

                if (name == "children" || name == "childX" || name == "childY" || name.contains("script", true)) {
                    continue
                }

                val value = field.get(widget) ?: continue

                data.add(NamedValueModel(field.name, value))

            }
        }

        nameCol.setCellValueFactory { it -> it.value.nameProperty }
        valueCol.setCellValueFactory { it -> it.value.valueProperty }
        valueCol.setCellFactory { _ ->  TupleCellFactory() }

        val filteredValueList = FilteredList(data, {_ -> true})
        propertyTf.textProperty().addListener { _, _, newValue -> filteredValueList.setPredicate { it ->
            if (newValue == null || newValue.isEmpty()) {
                return@setPredicate true
            }

            val lowercase = newValue.toLowerCase()

            if (it.name.toLowerCase().contains(lowercase)) {
                return@setPredicate true
            }

            return@setPredicate false
        }
        }

        val sortedValueList = SortedList(filteredValueList)
        sortedValueList.comparatorProperty().bind(propertyView.comparatorProperty())
        propertyView.items = sortedValueList

    }

    override fun onClear() {
        data.clear()
        clearEditCanvas()
        interfaceView.root.children.clear()
    }

    private fun clearRootLayer() {
        val g = rootLayer.graphicsContext2D
        g.clearRect(0.0, 0.0, rootLayer.width, rootLayer.height)
    }

    private fun clearUILayer() {
        val g = uiLayer.graphicsContext2D
        g.clearRect(0.0, 0.0, uiLayer.width, uiLayer.height)
    }

    @FXML
    fun clearEditCanvas() {
        editPane.children.clear()
        editPane.children.add(rootLayer)
        editPane.children.add(uiLayer)
        clearRootLayer()
        clearUILayer()
    }

    private fun renderOnCanvas(treeItem: TreeItem<InterfaceWrapper>) {
        clearEditCanvas()

        val selected = treeItem.value.widget

        if (selected.group == RSWidget.TYPE_CONTAINER) {

            if (selected.children == null || selected.children.isEmpty()) {
                return
            }

            val parent = selected

            for (childIndex in 0 until selected.children.size) {

                val child = InterfaceWrapper.widgets[selected.children[childIndex]]

                val childImage = toBufferedImage(InterfaceWrapper.widgets, child) ?: continue

                if (childImage.width <= 0 || childImage.height <= 0) {
                    continue
                }

                val fxImage = SwingFXUtils.toFXImage(childImage.setColorTransparent(java.awt.Color.BLACK), null)

                val layer = Canvas(rootLayer.width, rootLayer.height)
                layer.addEventHandler(MouseEvent.MOUSE_PRESSED) { _ -> clickInterfaceInCanvas(parent, childIndex) }

                val g = layer.graphicsContext2D
                g.drawImage(fxImage, parent.childX[childIndex].toDouble(), parent.childY[childIndex].toDouble())

                editPane.children.add(layer)
            }

        } else {

            val parentTI = treeItem.parent ?: return

            val selectedChildIndex = parentTI.children.indexOf(treeItem)

            val parent = parentTI.value.widget

            for (childIndex in 0 until parent.children.size) {

                val child = InterfaceWrapper.widgets[parent.children[childIndex]]

                val childImage = toBufferedImage(InterfaceWrapper.widgets, child) ?: continue

                if (childImage.width <= 0 || childImage.height <= 0) {
                    continue
                }

                val fxImage = SwingFXUtils.toFXImage(childImage.setColorTransparent(java.awt.Color.BLACK), null)

                val layer = Canvas(rootLayer.width, rootLayer.height)

                val g = layer.graphicsContext2D
                g.drawImage(fxImage, parent.childX[childIndex].toDouble(), parent.childY[childIndex].toDouble())

                editPane.children.add(layer)
            }

            clickInterfaceInCanvas(parent, selectedChildIndex)

        }

    }

    @FXML
    private fun clickInterfaceInCanvas(parent:RSWidget, childIndex: Int) {
        val child =  InterfaceWrapper.widgets[parent.children[childIndex]]

        val g = uiLayer.graphicsContext2D
        g.stroke = javafx.scene.paint.Color.YELLOW
        g.strokeRoundRect(parent.childX[childIndex].toDouble(), parent.childY[childIndex].toDouble(), child.width.toDouble(), child.height.toDouble(), 5.0, 5.0)

        uiLayer.toFront()
    }

    override fun onPopulate() {
        interfaceView.root.children.clear()
        PluginManager.post(LoadInterfaceEvent(currentPlugin, interfaceView.root))
    }

    fun toBufferedImage(widgets: Array<RSWidget>, widget: RSWidget): BufferedImage? {
        if (widget.width <= 0 || widget.height <= 0) {
            return null
        }

        RSRaster.init(widget.height, widget.width, IntArray(widget.width * widget.height))
        RSRaster.reset()

        if (widget.group == RSWidget.TYPE_CONTAINER) {
            renderWidget(widgets, widget, 0, 0, 0)
        } else if (widget.group == RSWidget.TYPE_SPRITE) {
            if (widget.defaultSprite != null) {
                widget.defaultSprite.drawSprite(0, 0)
            }
        } else if (widget.group == RSWidget.TYPE_TEXT) {
            renderText(widget, 0, 0)
        } else if (widget.group == RSWidget.TYPE_RECTANGLE) {
            renderRectangle(widget, 0, 0)
        }

        val data = RSRaster.raster

        val bimage = BufferedImage(RSRaster.width, RSRaster.height, BufferedImage.TYPE_INT_RGB)

        val pixels = (bimage.raster.dataBuffer as DataBufferInt).data

        System.arraycopy(data, 0, pixels, 0, data.size)

        return bimage
    }

    companion object {
        private fun renderWidget(widgets: Array<RSWidget>, widget: RSWidget, x: Int, y: Int, scroll: Int) {
            if (widget.group != 0 || widget.children == null) {
                return
            }

            val clipLeft = RSRaster.getClipLeft()
            val clipBottom = RSRaster.getClipBottom()
            val clipRight = RSRaster.getClipRight()
            val clipTop = RSRaster.getClipTop()

            RSRaster.setBounds(y + widget.height, x, x + widget.width, y)
            val children = widget.children.size

            for (childIndex in 0 until children) {
                var currentX = widget.childX[childIndex] + x
                var currentY = widget.childY[childIndex] + y - scroll

                val child = widgets[widget.children[childIndex]]

                currentX += child.horizontalDrawOffset
                currentY += child.verticalDrawOffset

                if (child.contentType > 0) {
                    //method75(child);
                }

                if (child.group == RSWidget.TYPE_CONTAINER) {
                    if (child.scrollPosition > child.scrollLimit - child.height) {
                        child.scrollPosition = child.scrollLimit - child.height
                    }
                    if (child.scrollPosition < 0) {
                        child.scrollPosition = 0
                    }

                    renderWidget(widgets, child, currentX, currentY, child.scrollPosition)
                    if (child.scrollLimit > child.height) {
                        //                    drawScrollbar(child.height, child.scrollPosition, currentY,
                        //                            currentX + child.width, child.scrollLimit);
                    }
                } else if (child.group != RSWidget.TYPE_MODEL_LIST) {
                    if (child.group == RSWidget.TYPE_INVENTORY) {

                    } else if (child.group == RSWidget.TYPE_RECTANGLE) {
                        val colour = child.defaultColour

                        if (child.alpha.toInt() == 0) {
                            if (child.filled) {
                                RSRaster.fillRectangle(currentX, currentY, child.width,
                                        child.height, colour)
                            } else {
                                RSRaster.drawRectangle(currentX, currentY, child.width,
                                        child.height, colour)
                            }
                        } else if (child.filled) {
                            RSRaster.fillRectangle(currentX, currentY, child.width, child.height,
                                    colour, 256 - (child.alpha.toInt() and 0xff))
                        } else {
                            RSRaster.drawRectangle(currentX, currentY, child.width, child.height,
                                    colour, 256 - (child.alpha.toInt() and 0xff))
                        }
                    } else if (child.group == RSWidget.TYPE_TEXT) {
                        val font = child.font
                        var text = child.defaultText

                        var colour: Int

                        colour = child.defaultColour

                        if (child.optionType == RSWidget.OPTION_CONTINUE) {
                            text = "Please wait..."
                            colour = child.defaultColour
                        }

                        if (RSRaster.width == 479) {
                            if (colour == 0xffff00) {
                                colour = 255
                            } else if (colour == 49152) {
                                colour = 0xffffff
                            }
                        }

                        var drawY = currentY + font.verticalSpace
                        while (text.isNotEmpty()) {

                            val line = text.indexOf("\\n")
                            val drawn: String
                            if (line != -1) {
                                drawn = text.substring(0, line)
                                text = text.substring(line + 2)
                            } else {
                                drawn = text
                                text = ""
                            }

                            if (child.centeredText) {
                                font.shadowCentre(currentX + child.width / 2, drawY, drawn,
                                        child.shadowedText, colour)
                            } else {
                                font.shadow(currentX, drawY, drawn, child.shadowedText, colour)
                            }
                            drawY += font.verticalSpace
                        }
                    } else if (child.group == RSWidget.TYPE_SPRITE) {
                        val sprite = child.defaultSprite

                        sprite?.drawSprite(currentX, currentY)
                    } else if (child.group == RSWidget.TYPE_MODEL) {

                    } else if (child.group == RSWidget.TYPE_ITEM_LIST) {

                    }
                }
            }

            RSRaster.setBounds(clipTop, clipLeft, clipRight, clipBottom)
        }

        private fun renderText(child: RSWidget, x: Int, y: Int) {
            if (child.group == RSWidget.TYPE_TEXT) {

                val font = child.font
                var text = child.defaultText

                var colour: Int

                colour = child.defaultColour

                if (child.optionType == RSWidget.OPTION_CONTINUE) {
                    text = "Please wait..."
                    colour = child.defaultColour
                }

                if (RSRaster.width == 479) {
                    if (colour == 0xffff00) {
                        colour = 255
                    } else if (colour == 49152) {
                        colour = 0xffffff
                    }
                }

                var drawY = y + font.verticalSpace
                while (text.isNotEmpty()) {

                    val line = text.indexOf("\\n")
                    val drawn: String
                    if (line != -1) {
                        drawn = text.substring(0, line)
                        text = text.substring(line + 2)
                    } else {
                        drawn = text
                        text = ""
                    }

                    if (child.centeredText) {
                        font.shadowCentre(x + child.width / 2, drawY, drawn,
                                child.shadowedText, colour)
                    } else {
                        font.shadow(x, drawY, drawn, child.shadowedText, colour)
                    }
                    drawY += font.verticalSpace
                }
            }
        }

        private fun renderRectangle(child: RSWidget, currentX: Int, currentY: Int) {
            if (child.group == RSWidget.TYPE_RECTANGLE) {
                val colour = child.defaultColour

                if (child.alpha.toInt() == 0) {
                    if (child.filled) {
                        RSRaster.fillRectangle(currentX, currentY, child.width,
                                child.height, colour)
                    } else {
                        RSRaster.drawRectangle(currentX, currentY, child.width,
                                child.height, colour)
                    }
                } else if (child.filled) {
                    RSRaster.fillRectangle(currentX, currentY, child.width, child.height,
                            colour, 256 - (child.alpha.toInt() and 0xFF))
                } else {
                    RSRaster.drawRectangle(currentX, currentY, child.width, child.height,
                            colour, 256 - (child.alpha.toInt() and 0xFF))
                }
            }
        }
    }

}


