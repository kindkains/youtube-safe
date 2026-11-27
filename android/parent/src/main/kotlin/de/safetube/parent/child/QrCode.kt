package de.safetube.parent.child

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

object QrCode {
    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    /** ARGB-Pixel eines quadratischen QR-Codes (reines Java, in JVM-Tests prüfbar). */
    fun pixels(text: String, size: Int = 512): IntArray {
        val hints = mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8")
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        return IntArray(size * size) { index -> if (matrix[index % size, index / size]) BLACK else WHITE }
    }

    fun bitmap(text: String, size: Int = 512): ImageBitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels(text, size), 0, size, 0, 0, size, size)
        return bitmap.asImageBitmap()
    }
}
