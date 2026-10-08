package uz.notgis.documate

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date

/**
 * Global crash ushlagich: yopilish sababini filesDir/crash_last.txt ga yozadi.
 * Keyingi ochilishda CrashScreen orqali ko'rsatiladi — logcat siz ham sabab bilinadi.
 */
object CrashReporter {
    private const val NAME = "crash_last.txt"

    fun install(ctx: Context) {
        val appCtx = ctx.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                File(appCtx.filesDir, NAME).writeText(
                    "${Date()}\n${e}\nThread: ${t.name}\n${sw}".take(8000)
                )
            } catch (_: Exception) {
            }
            if (prev != null) {
                prev.uncaughtException(t, e)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }

    fun read(ctx: Context): String? {
        val f = File(ctx.filesDir, NAME)
        return if (f.exists()) runCatching { f.readText() }.getOrNull()?.ifBlank { null } else null
    }

    fun clear(ctx: Context) {
        runCatching { File(ctx.filesDir, NAME).delete() }
    }
}

@Composable
fun CrashScreen(report: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Ilova xatosi (crash)", style = MaterialTheme.typography.titleLarge)
        Text(
            "Pastdagi matnni nusxalab, dasturchiga yuboring:",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        Text(
            report,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Button(
                onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("crash", report))
                },
                modifier = Modifier.weight(1f),
            ) { Text("Nusxalash") }
            OutlinedButton(
                onClick = onClose,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            ) { Text("Yopish") }
        }
    }
}
