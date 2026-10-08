package uz.notgis.documate

import android.annotation.SuppressLint
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Haqiqiy (formatlash saqlangan) docx/xlsx ko'rinishi — WebView + offline JS.
 * mammoth (docx→HTML) va SheetJS (xlsx→HTML) assets/ ichida, INTERNETsiz ishlaydi.
 * Katta fayllar (>12 MB) yoki xatoda — native matn ko'rinishiga qaytiladi.
 */
object OfficeWeb {
    const val MAX_WEB_BYTES = 12 * 1024 * 1024

    private val libCache = HashMap<String, String>()

    fun assetJs(ctx: android.content.Context, name: String): String = synchronized(libCache) {
        libCache.getOrPut(name) {
            ctx.assets.open(name).bufferedReader().use { it.readText() }
                // minified JS ichidagi "</script" satri HTML ni buzmasligi uchun
                .replace("</script", "<\\/script")
        }
    }

    suspend fun fileBase64(ctx: android.content.Context, doc: DocFile): String? =
        withContext(Dispatchers.IO) {
            try {
                ctx.contentResolver.openInputStream(doc.uri)?.use { ins ->
                    // o'lcham chegarasi (katta base64 WebView ni o'ldiradi)
                    val size = ins.available().toLong()
                    if (size > MAX_WEB_BYTES) return@withContext null
                    val bytes = ins.readBytes()
                    if (bytes.size > MAX_WEB_BYTES || bytes.isEmpty()) return@withContext null
                    Base64.encodeToString(bytes, Base64.NO_WRAP)
                }
            } catch (e: Exception) {
                null
            }
        }

    fun docxHtml(mammothJs: String, b64: String, loading: String): String = buildString {
        append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">")
        append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
        append("<style>body{font-family:sans-serif;padding:12px;line-height:1.55;color:#111}img{max-width:100%;height:auto}table{border-collapse:collapse;max-width:100%}td,th{border:1px solid #999;padding:5px;font-size:14px}h1,h2{line-height:1.3}</style>")
        append("</head><body><div id=\"c\">")
        append(loading)
        append("</div><script>")
        append(mammothJs)
        append("</script><script>try{var raw=Uint8Array.from(atob(\"")
        append(b64)
        append("\"),function(c){return c.charCodeAt(0)});")
        append("mammoth.convertToHtml({arrayBuffer:raw.buffer},{convertImage:mammoth.images.imgElement(function(im){return im.read(\"base64\").then(function(s){return{src:\"data:\"+im.contentType+\";base64,\"+s}})}})}).then(function(r){document.getElementById(\"c\").innerHTML=r.value||\"<p>?</p>\";document.title=\"OK\";}).catch(function(e){document.title=\"ERR:\"+e;});}catch(e){document.title=\"ERR:\"+e;}</script></body></html>")
    }

    fun xlsxHtml(sheetJs: String, b64: String, loading: String): String = buildString {
        append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">")
        append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
        append("<style>body{font-family:sans-serif;padding:8px;color:#111}h2{font-size:17px;color:#5B3A8E}table{border-collapse:collapse;margin-bottom:20px}td,th{border:1px solid #999;padding:5px;font-size:13px}</style>")
        append("</head><body><div id=\"c\">")
        append(loading)
        append("</div><script>")
        append(sheetJs)
        append("</script><script>try{var raw=Uint8Array.from(atob(\"")
        append(b64)
        append("\"),function(c){return c.charCodeAt(0)});var wb=XLSX.read(raw,{type:\"array\"});var h=\"\";wb.SheetNames.forEach(function(n,i){var esc=n.replace(/&/g,\"&amp;\").replace(/</g,\"&lt;\");h+=\"<h2>\"+(i+1)+\". \"+esc+\"</h2>\"+XLSX.utils.sheet_to_html(wb.Sheets[n]);});document.getElementById(\"c\").innerHTML=h||\"<p>?</p>\";document.title=\"OK\";}catch(e){document.title=\"ERR:\"+e;}</script></body></html>")
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebDoc(html: String?, t: L, onFail: () -> Unit) {
    if (html == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    var wv by remember { mutableStateOf<WebView?>(null) }
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.setSupportZoom(true)
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            view.evaluateJavascript("document.title") { title ->
                                if (title != null && title.contains("ERR")) onFail()
                            }
                        }
                    }
                    loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                    wv = this
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun DocxWebView(doc: DocFile, t: L, onFail: () -> Unit) {
    val ctx = LocalContext.current
    val html by produceState<String?>(null, doc.uri) {
        value = withContext(Dispatchers.IO) {
            try {
                val b64 = OfficeWeb.fileBase64(ctx, doc) ?: return@withContext null
                val js = OfficeWeb.assetJs(ctx, "mammoth.min.js")
                OfficeWeb.docxHtml(js, b64, t.opening)
            } catch (e: Exception) {
                null
            }
        }
    }
    val h = html
    if (h == null) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            TextButton(onClick = onFail) { Text(t.opening) }
        }
    } else {
        WebDoc(h, t, onFail)
    }
}

@Composable
fun XlsxWebView(doc: DocFile, t: L, onFail: () -> Unit) {
    val ctx = LocalContext.current
    val html by produceState<String?>(null, doc.uri) {
        value = withContext(Dispatchers.IO) {
            try {
                val b64 = OfficeWeb.fileBase64(ctx, doc) ?: return@withContext null
                val js = OfficeWeb.assetJs(ctx, "xlsx.full.min.js")
                OfficeWeb.xlsxHtml(js, b64, t.opening)
            } catch (e: Exception) {
                null
            }
        }
    }
    val h = html
    if (h == null) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            TextButton(onClick = onFail) { Text(t.opening) }
        }
    } else {
        WebDoc(h, t, onFail)
    }
}
