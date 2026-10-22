package com.streamhub.app

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object Downloader {

    const val FOLDER = "StreamHub"

    private val initLock = Any()

    @Volatile
    private var ready = false

    suspend fun ensureReady(context: Context): String? = withContext(Dispatchers.IO) {
        try {
            if (!ready) {
                synchronized(initLock) {
                    if (!ready) {
                        YoutubeDL.getInstance().init(context.applicationContext)
                        FFmpeg.getInstance().init(context.applicationContext)
                        ready = true
                    }
                }
            }
            null
        } catch (e: Exception) {
            e.message ?: e.toString()
        }
    }

    suspend fun update(context: Context): String = withContext(Dispatchers.IO) {
        ensureReady(context)?.let { error(it) }
        val status = YoutubeDL.getInstance().updateYoutubeDL(context.applicationContext)?.name
        val version = YoutubeDL.getInstance().versionName(context) ?: "unknown"
        when (status) {
            "DONE" -> "Downloader updated to $version."
            "ALREADY_UP_TO_DATE" -> "Downloader is up to date ($version)."
            else -> "Downloader ready ($version)."
        }
    }

    fun version(context: Context): String =
        runCatching { YoutubeDL.getInstance().versionName(context) }.getOrNull() ?: "not ready"

    suspend fun download(
        context: Context,
        url: String,
        audioOnly: Boolean,
        processId: String,
        onProgress: (Float, Long, String) -> Unit
    ): List<String> = withContext(Dispatchers.IO) {
        val workDir = File(context.getExternalFilesDir(null), "work-$processId")
        try {
            ensureReady(context)?.let { error(it) }
            workDir.mkdirs()

            val request = YoutubeDLRequest(url).apply {
                addOption("-o", workDir.absolutePath + "/%(title).150B.%(ext)s")
                addOption("--no-playlist")
                addOption("--no-mtime")
                if (audioOnly) {
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "0")
                    addOption("--embed-thumbnail")
                    addOption("--embed-metadata")
                } else {
                    addOption("-f", "bv*[ext=mp4][height<=1080]+ba[ext=m4a]/b[ext=mp4]/bv*+ba/b")
                    addOption("--merge-output-format", "mp4")
                }
            }

            YoutubeDL.getInstance().execute(request, processId) { pct, eta, line ->
                onProgress(pct, eta, line)
            }

            val files = workDir.listFiles().orEmpty()
                .filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
            if (files.isEmpty()) error("No file was produced.")

            files.map { file ->
                saveToDownloads(context, file)
                file.name
            }
        } finally {
            workDir.deleteRecursively()
        }
    }

    fun cancel(processId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
    }

    private fun saveToDownloads(context: Context, file: File) {
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Couldn't create ${file.name}")
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            } ?: error("Couldn't write ${file.name}")

            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                FOLDER
            )
            if (!dir.mkdirs() && !dir.isDirectory) error("Couldn't create Download/$FOLDER")
            file.copyTo(File(dir, file.name), overwrite = true)
        }
    }
}
