package de.safetube.parent.child

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Test

class QrCodeTest {

    @Test
    fun `QR-Code lässt sich zurücklesen`() {
        val payload = """{"server": "https://safetube.isoletta.me", "code": "AB12CD34"}"""

        val pixels = QrCode.pixels(payload, size = 300)

        assertEquals(300 * 300, pixels.size)
        val source = RGBLuminanceSource(300, 300, pixels)
        // PURE_BARCODE: Bild enthält nur den Code (wie von uns erzeugt), keine Perspektive suchen.
        val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), mapOf(DecodeHintType.PURE_BARCODE to true))
        assertEquals(payload, decoded.text)
    }
}
