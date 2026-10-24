package f3l1x.catter.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.FileProvider
import f3l1x.catter.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val size: Long,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data class Downloading(val bytes: Long, val total: Long, val speed: Long) : UpdateState
    data class Failed(val message: String) : UpdateState
}

object UpdateChecker {

    private const val REPO = "xzyyysh/catter"
    private const val RELEASE = "https://api.github.com/repos/$REPO/releases/latest"
    private const val README = "https://raw.githubusercontent.com/$REPO/main/README.md"
    private const val INTERVAL_MS = 60 * 60 * 1000L
    private const val FOLDER = "updates"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _update = MutableStateFlow<UpdateInfo?>(null)
    val update: StateFlow<UpdateInfo?> = _update.asStateFlow()

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var started = false
    private var dismissed: String? = null

    fun start(context: Context) {
        if (started) return
        started = true
        val appContext = context.applicationContext

        scope.launch {
            while (true) {
                runCatching { check(appContext) }
                    .onFailure { Logger.d("update check skipped: ${it.message}") }
                delay(INTERVAL_MS)
            }
        }
    }

    fun dismiss() {
        dismissed = _update.value?.version
        _update.value = null
        _state.value = UpdateState.Idle
    }

    fun download(context: Context) {
        val info = _update.value ?: return
        if (_state.value is UpdateState.Downloading) return

        val appContext = context.applicationContext
        if (!canInstall(appContext)) {
            openInstallSettings(appContext)
            _state.value = UpdateState.Failed("Allow installs from Catter, then tap download again")
            return
        }

        scope.launch {
            _state.value = UpdateState.Downloading(0L, info.size, 0L)
            runCatching { fetchApk(appContext, info) }
                .onSuccess { file ->
                    _state.value = UpdateState.Idle
                    install(appContext, file)
                }
                .onFailure {
                    Logger.e("update download failed", it)
                    _state.value = UpdateState.Failed(it.message ?: "Download failed")
                }
        }
    }

    fun cleanup(context: Context) {
        scope.launch(Dispatchers.IO) {
            folder(context).listFiles().orEmpty().forEach { file ->
                val version = file.name.removePrefix("catter-").removeSuffix(".apk")
                if (!isNewer(version, BuildConfig.VERSION_NAME)) file.delete()
            }
        }
    }

    private suspend fun check(context: Context) = withContext(Dispatchers.IO) {
        val body = request(RELEASE) ?: return@withContext

        val json = JSONObject(body)
        val version = json.optString("tag_name").removePrefix("v").removePrefix("V")
        if (version.isBlank()) return@withContext
        if (!isNewer(version, BuildConfig.VERSION_NAME)) return@withContext
        if (version == dismissed) return@withContext

        val assets = json.optJSONArray("assets")
        val apk = (0 until (assets?.length() ?: 0))
            .mapNotNull { assets?.optJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
            ?: return@withContext

        val url = apk.optString("browser_download_url")
        if (url.isBlank()) return@withContext

        val notes = json.optString("body").ifBlank { request(README).orEmpty() }
        Logger.d("update available: $version")

        _update.value = UpdateInfo(
            version = version,
            notes = notes,
            apkUrl = url,
            size = apk.optLong("size"),
        )
    }

    private fun fetchApk(context: Context, info: UpdateInfo): File {
        val folder = folder(context).apply { mkdirs() }
        val target = File(folder, "catter-${info.version}.apk")
        if (target.exists()) target.delete()

        val connection = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Catter/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Accept", "application/octet-stream")
        }

        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")

            val total = connection.contentLengthLong.takeIf { it > 0L } ?: info.size
            val buffer = ByteArray(64 * 1024)
            var bytes = 0L
            var startedAt = 0L
            var lastEmit = 0L
            var speed = 0L

            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break

                        output.write(buffer, 0, read)
                        bytes += read

                        val now = SystemClock.elapsedRealtime()
                        if (startedAt == 0L) startedAt = now
                        if (now - lastEmit >= 150L) {
                            val elapsed = now - startedAt
                            if (elapsed > 400L) speed = bytes * 1000L / elapsed
                            _state.value = UpdateState.Downloading(bytes, total, speed)
                            lastEmit = now
                        }
                    }
                }
            }

            _state.value = UpdateState.Downloading(bytes, total, speed)
            Logger.d("update downloaded: ${target.name}")
            return target
        } catch (throwable: Throwable) {
            target.delete()
            throw throwable
        } finally {
            connection.disconnect()
        }
    }

    private fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        runCatching { context.startActivity(intent) }
            .onFailure {
                Logger.e("update install intent failed", it)
                _state.value = UpdateState.Failed("No installer available")
            }
    }

    private fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    private fun openInstallSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        runCatching { context.startActivity(intent) }
            .onFailure { Logger.w("install settings unavailable", it) }
    }

    private fun request(url: String): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Catter/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Accept", "application/vnd.github+json, text/plain")
        }

        try {
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) return null
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("HTTP $code")
            return body
        } finally {
            connection.disconnect()
        }
    }

    private fun folder(context: Context): File = File(context.cacheDir, FOLDER)

    private fun isNewer(remote: String, current: String): Boolean {
        val left = remote.split('.').mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }
        val right = current.split('.').mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }

        for (index in 0 until maxOf(left.size, right.size)) {
            val a = left.getOrElse(index) { 0 }
            val b = right.getOrElse(index) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
