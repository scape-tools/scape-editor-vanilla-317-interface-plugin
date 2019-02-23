package plugin

import scape.editor.fs.RSArchive
import scape.editor.fs.RSFileStore
import scape.editor.fs.RSFileSystem
import scape.editor.fs.graphics.RSFont
import scape.editor.gui.plugin.Plugin
import scape.editor.gui.plugin.extension.InterfaceExtension

import java.io.IOException

@Plugin(name = "Vanilla 317 Interface Plugin", authors = ["Nshusa"])
class InterfaceDefinitionPlugin : InterfaceExtension() {

    override fun applicationIcon(): String {
        return "icons/icon.png"
    }

    override fun fxml(): String {
        return "scene.fxml"
    }

    override fun stylesheets(): Array<String> {
        return arrayOf("css/style.css")
    }

    @Throws(IOException::class)
    override fun getInterfaceArchive(fs: RSFileSystem): RSArchive {
        return fs.getArchive(RSArchive.INTERFACE_ARCHIVE)
    }

    @Throws(IOException::class)
    override fun getGraphicsArchive(fs: RSFileSystem): RSArchive {
        return fs.getArchive(RSArchive.MEDIA_ARCHIVE)
    }

    @Throws(IOException::class)
    override fun getFonts(fs: RSFileSystem): Array<RSFont> {
        val archive = fs.getArchive(RSArchive.TITLE_ARCHIVE)
        val smallFont = RSFont.decode(archive, "p11_full", false)
        val frameFont = RSFont.decode(archive, "p12_full", false)
        val boldFont = RSFont.decode(archive, "b12_full", false)
        val font2 = RSFont.decode(archive, "q8_full", true)
        return arrayOf(smallFont, frameFont, boldFont, font2)
    }

    @Throws(IOException::class)
    override fun getModelStore(fs: RSFileSystem): RSFileStore {
        return fs.getStore(RSFileStore.MODEL_FILE_STORE)
    }

}