package com.sidegallery.app

import android.graphics.Bitmap
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testGifEncoderMultipleFrames() {
    val encoder = GifEncoder()
    val outputStream = ByteArrayOutputStream()
    encoder.start(outputStream)
    encoder.setDelay(100)
    encoder.setRepeat(0)

    val frame1 = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
    frame1.eraseColor(android.graphics.Color.RED)
    val frame2 = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
    frame2.eraseColor(android.graphics.Color.GREEN)
    val frame3 = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
    frame3.eraseColor(android.graphics.Color.BLUE)

    assertTrue(encoder.addFrame(frame1))
    assertTrue(encoder.addFrame(frame2))
    assertTrue(encoder.addFrame(frame3))
    assertTrue(encoder.finish())

    val bytes = outputStream.toByteArray()
    assertTrue("GIF output must have data", bytes.isNotEmpty())
    
    // Check GIF89a header
    val header = String(bytes, 0, 6, Charsets.US_ASCII)
    assertEquals("GIF89a", header)

    // Check GIF trailer ';' (0x3B) at the end
    assertEquals(0x3B.toByte(), bytes.last())

    // Verify with standard ImageIO GIF reader that it decodes all 3 frames without errors!
    val iis = javax.imageio.ImageIO.createImageInputStream(java.io.ByteArrayInputStream(bytes))
    val readers = javax.imageio.ImageIO.getImageReadersByFormatName("gif")
    assertTrue(readers.hasNext())
    val reader = readers.next()
    reader.input = iis
    val numFrames = reader.getNumImages(true)
    assertEquals(3, numFrames)
    for (i in 0 until 3) {
      val img = reader.read(i)
      assertNotNull("Frame $i must not be null", img)
      assertEquals(320, img.width)
      assertEquals(240, img.height)
      // Check that the bottom pixels are NOT black (they shouldn't be cut off)
      val bottomPixel = img.getRGB(160, 230)
      assertNotEquals(0xFF000000.toInt(), bottomPixel)
    }
  }
}

