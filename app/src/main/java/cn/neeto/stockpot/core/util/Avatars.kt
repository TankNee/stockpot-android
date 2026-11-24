package cn.neeto.stockpot.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * 头像：相册里挑一张，裁成正方形、压到 320px 再上传。
 *
 * 手机相册原图动辄几 MB，服务端只收 600KB 以内，也没必要为了一个圆头像传原图。
 * 这里顺手按 EXIF 把竖拍的照片转正——不然选完头像人是躺着的。
 */
object Avatars {

    /** 上传用的边长和画质：320px 的 jpeg 大约二三十 KB，圆头像够清楚了。 */
    private const val UPLOAD_SIDE = 320
    private const val UPLOAD_QUALITY = 86

    /** 解码时就压到这么大，免得几千万像素的原图在内存里摊开。 */
    private const val DECODE_LIMIT = 720

    /** 相册里那张图 → `data:image/jpeg;base64,...`，失败返回 null（文件读不到、不是图片）。 */
    fun dataUrlFrom(context: Context, uri: Uri): String? {
        val raw = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return null
        if (raw.isEmpty()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, DECODE_LIMIT)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(raw, 0, raw.size, options) ?: return null
        val rotated = rotateByExif(context, uri, decoded)
        val square = centerSquare(rotated)
        val scaled = Bitmap.createScaledBitmap(square, UPLOAD_SIDE, UPLOAD_SIDE, true)

        val bytes = ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, UPLOAD_QUALITY, out)
            out.toByteArray()
        }
        // 中间那几份位图交给 GC 就好：最大也就 720px，手动 recycle 反而容易踩到「同一份回收两次」
        if (bytes.isEmpty()) return null
        return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /** 服务端给回来的字节 → 位图，顺带按 [limit] 缩一遍。 */
    fun decode(bytes: ByteArray, limit: Int = 512): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, limit)
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }.getOrNull()

    private fun sampleSize(width: Int, height: Int, limit: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= limit || h / 2 >= limit) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    private fun centerSquare(source: Bitmap): Bitmap {
        val side = minOf(source.width, source.height)
        val left = (source.width - side) / 2
        val top = (source.height - side) / 2
        if (side == source.width && side == source.height) return source
        return Bitmap.createBitmap(source, left, top, side, side)
    }

    /** 竖着拍的照片在文件里是「横着存 + 一个旋转标记」，不转正就是躺着的人脸。 */
    private fun rotateByExif(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val degrees = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                when (
                    ExifInterface(input).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                ) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
