package pe.aphid.core.data.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Comparte archivos exportados mediante FileProvider (autoridad `<paquete>.fileprovider`). */
object ShareHelper {
    const val MIME_PDF = "application/pdf"
    const val MIME_CSV = "text/csv"
    const val MIME_JSON = "application/json"

    fun exportDir(context: Context): File = File(context.cacheDir, "exports").apply { mkdirs() }

    fun share(context: Context, file: File, mime: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun shareText(context: Context, text: String, chooserTitle: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Nombre de archivo seguro. */
    fun safeName(base: String): String = base.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "aphid" }
}
