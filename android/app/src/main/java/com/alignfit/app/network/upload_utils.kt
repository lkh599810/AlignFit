package com.alignfit.app.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.google.gson.JsonParser
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import retrofit2.HttpException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

// ─── Upload helpers shared by the photo and video flows ───────────────────────
// All functions here do blocking I/O: call them from Dispatchers.IO.

// Longest side sent to the server. MediaPipe resizes internally, so full camera
// resolution only costs memory/bandwidth (and a huge annotated image back).
const val UPLOAD_IMAGE_MAX_SIDE = 1600
const val PREVIEW_IMAGE_MAX_SIDE = 1280

// Keep in sync with the backend defaults (ALIGNFIT_MAX_VIDEO_MB / _SECONDS).
const val MAX_VIDEO_BYTES = 100L * 1024 * 1024
const val MAX_VIDEO_SECONDS = 60

/** Decode [uri] downsampled so its longest side is about [maxSide], EXIF-rotated. */
fun decodeSampledBitmap(context: Context, uri: Uri, maxSide: Int): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    var bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        ?: return null

    val scale = maxSide.toFloat() / maxOf(bitmap.width, bitmap.height)
    val rotation = resolver.openInputStream(uri)?.use { exifRotation(it) } ?: 0
    if (scale < 1f || rotation != 0) {
        val matrix = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (rotation != 0) postRotate(rotation.toFloat())
        }
        val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (transformed !== bitmap) bitmap.recycle()
        bitmap = transformed
    }
    return bitmap
}

private fun exifRotation(stream: java.io.InputStream): Int = try {
    when (ExifInterface(stream).getAttributeInt(
        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
    )) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
} catch (_: IOException) {
    0
}

/** Photo -> downscaled, upright JPEG multipart part (field "image"). */
fun buildImagePart(context: Context, uri: Uri): MultipartBody.Part? {
    val bitmap = decodeSampledBitmap(context, uri, UPLOAD_IMAGE_MAX_SIDE) ?: return null
    val bytes = ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        out.toByteArray()
    }
    bitmap.recycle()
    return MultipartBody.Part.createFormData(
        "image", "upload.jpg", bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
    )
}

data class VideoInfo(val displayName: String, val sizeBytes: Long, val durationSeconds: Int?)

fun queryVideoInfo(context: Context, uri: Uri): VideoInfo {
    var name = "video.mp4"
    var size = -1L
    context.contentResolver.query(
        uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null
    )?.use { c ->
        if (c.moveToFirst()) {
            c.getString(0)?.let { name = it }
            if (!c.isNull(1)) size = c.getLong(1)
        }
    }
    val duration = try {
        MediaMetadataRetriever().run {
            try {
                setDataSource(context, uri)
                extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    ?.let { (it / 1000).toInt() }
            } finally {
                release()
            }
        }
    } catch (_: Exception) {
        null
    }
    return VideoInfo(name, size, duration)
}

/** Returns a Korean error message if the video breaks a server limit, else null. */
fun videoLimitError(info: VideoInfo): String? = when {
    info.sizeBytes > MAX_VIDEO_BYTES ->
        "영상 파일이 너무 큽니다. ${MAX_VIDEO_BYTES / (1024 * 1024)}MB 이하 영상을 선택해 주세요."
    info.durationSeconds != null && info.durationSeconds > MAX_VIDEO_SECONDS ->
        "영상이 너무 깁니다. ${MAX_VIDEO_SECONDS}초 이하로 촬영한 영상을 선택해 주세요."
    else -> null
}

/** Video multipart part (field "video") streamed from the content URI, never fully in memory. */
fun buildVideoPart(context: Context, uri: Uri, info: VideoInfo): MultipartBody.Part {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: "video/mp4"
    val body = object : RequestBody() {
        override fun contentType(): MediaType? = mime.toMediaTypeOrNull()
        override fun contentLength(): Long = info.sizeBytes
        override fun writeTo(sink: BufferedSink) {
            val stream = resolver.openInputStream(uri) ?: throw IOException("cannot open video")
            stream.source().use { sink.writeAll(it) }
        }
    }
    val ext = info.displayName.substringAfterLast('.', "mp4").ifBlank { "mp4" }
    return MultipartBody.Part.createFormData("video", "upload.$ext", body)
}

/** Map a network/server failure to a message the user can act on. */
fun Throwable.toUserMessage(): String = when (this) {
    is HttpException -> serverDetail() ?: when (code()) {
        413 -> "파일이 너무 큽니다. 더 작은 파일로 다시 시도해 주세요."
        415 -> "지원하지 않는 파일 형식입니다."
        503 -> "서버의 분석 모델을 사용할 수 없습니다. 잠시 후 다시 시도해 주세요."
        in 500..599 -> "서버에서 오류가 발생했습니다. 잠시 후 다시 시도해 주세요. (${code()})"
        else -> "요청을 처리할 수 없습니다. (${code()})"
    }
    is SocketTimeoutException -> "서버 응답 시간이 초과되었습니다. 네트워크 상태를 확인하거나 더 짧은 영상으로 다시 시도해 주세요."
    is UnknownHostException, is ConnectException ->
        "서버에 연결할 수 없습니다. 인터넷 연결을 확인해 주세요."
    is SSLException -> "보안 연결(HTTPS)에 실패했습니다. 기기 날짜/시간 설정을 확인해 주세요."
    is IOException -> "네트워크 오류가 발생했습니다. 연결 상태를 확인해 주세요."
    is OutOfMemoryError -> "이미지가 너무 커서 처리할 수 없습니다. 다른 사진으로 다시 시도해 주세요."
    else -> "분석 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
}

// FastAPI errors look like {"detail": "..."}; show the server's Korean message.
private fun HttpException.serverDetail(): String? = try {
    response()?.errorBody()?.string()?.let { body ->
        JsonParser.parseString(body).asJsonObject.get("detail")
            ?.takeIf { it.isJsonPrimitive }?.asString
    }
} catch (_: Exception) {
    null
}
