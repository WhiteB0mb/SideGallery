package com.sidegallery.app

import android.graphics.Bitmap
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

@RunWith(RobolectricTestRunner::class)
class GifEncoderTest {
    @Test
    fun testGifEncoderLargeFrames() {
        val baos = ByteArrayOutputStream()
        val encoder = GifEncoder()
        encoder.start(baos)
        encoder.setDelay(100)
        encoder.setRepeat(0)

        // Add 120 frames
        for (i in 0 until 120) {
            val frame = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
            frame.eraseColor(if (i % 2 == 0) android.graphics.Color.RED else android.graphics.Color.BLUE)
            assertTrue(encoder.addFrame(frame))
        }

        assertTrue(encoder.finish())

        val bytes = baos.toByteArray()
        assertTrue("GIF should be larger than a few bytes", bytes.size > 1000)

        val iis = ImageIO.createImageInputStream(java.io.ByteArrayInputStream(bytes))
        val readers = ImageIO.getImageReadersByFormatName("gif")
        val reader = readers.next()
        reader.input = iis
        val numFrames = reader.getNumImages(true)
        assertEquals(120, numFrames)
    }
}
