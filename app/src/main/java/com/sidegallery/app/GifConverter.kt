package com.sidegallery.app

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min

object GifConverter {

    const val MAX_VIDEO_DURATION_MS = 15_000L // 15 seconds

    /**
     * Extracts frames from a video URI and converts them into an animated GIF file.
     * Takes up to the first 15 seconds of the video.
     */
    suspend fun convertVideoToGif(
        context: Context,
        videoUri: Uri,
        destFile: File,
        targetWidth: Int = 320,
        fps: Int = 8
    ): Boolean = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        var tempVideoFile: File? = null
        try {
            var dataSourceSet = false
            try {
                retriever.setDataSource(context, videoUri)
                dataSourceSet = true
            } catch (e: Exception) {
                // Fallback to caching locally
                tempVideoFile = File(context.cacheDir, "temp_vid_in_${System.currentTimeMillis()}.tmp")
                context.contentResolver.openInputStream(videoUri)?.use { input ->
                    FileOutputStream(tempVideoFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (tempVideoFile.exists() && tempVideoFile.length() > 0L) {
                    retriever.setDataSource(tempVideoFile.absolutePath)
                    dataSourceSet = true
                }
            }

            if (!dataSourceSet) {
                return@withContext false
            }

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val rawDurationMs = durationStr?.toLongOrNull() ?: 3000L
            
            // Limit to max 15s
            val actualDuration = rawDurationMs.coerceIn(500L, MAX_VIDEO_DURATION_MS)
            val frameIntervalMs = (1000 / fps).coerceIn(80, 200)
            val totalFrames = ((actualDuration / frameIntervalMs) + 1).toInt().coerceIn(1, 100)

            destFile.parentFile?.mkdirs()
            val encoder = GifEncoder()
            val outputStream = FileOutputStream(destFile)
            encoder.start(outputStream)
            encoder.setDelay(frameIntervalMs)
            encoder.setRepeat(0) // infinite loop

            var targetHeight = 0
            var framesAdded = 0

            for (i in 0 until totalFrames) {
                val frameTimeUs = (i * frameIntervalMs * 1000L).coerceAtMost(actualDuration * 1000L)
                val rawBitmap = try {
                    retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                        ?: retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } catch (e: Exception) {
                    null
                }

                if (rawBitmap != null) {
                    if (targetHeight == 0) {
                        val aspect = rawBitmap.height.toFloat() / rawBitmap.width.toFloat()
                        targetHeight = (targetWidth * aspect).toInt().coerceAtLeast(1)
                    }
                    val scaledBitmap = if (rawBitmap.width != targetWidth || rawBitmap.height != targetHeight) {
                        Bitmap.createScaledBitmap(rawBitmap, targetWidth, targetHeight, true)
                    } else {
                        rawBitmap
                    }
                    
                    if (encoder.addFrame(scaledBitmap)) {
                        framesAdded++
                    }
                    if (scaledBitmap != rawBitmap) {
                        scaledBitmap.recycle()
                    }
                    rawBitmap.recycle()
                }
            }

            encoder.finish()
            outputStream.close()
            return@withContext framesAdded > 0 && destFile.exists() && destFile.length() > 0
        } catch (e: Exception) {
            e.printStackTrace()
            destFile.delete()
            return@withContext false
        } finally {
            try { retriever.release() } catch (e: Exception) {}
            tempVideoFile?.let {
                if (it.exists()) it.delete()
            }
        }
    }

    private fun scaleBitmapToWidth(source: Bitmap, targetWidth: Int): Bitmap {
        if (source.width <= targetWidth) return source
        val aspectRatio = source.height.toFloat() / source.width.toFloat()
        val targetHeight = (targetWidth * aspectRatio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }
}

/**
 * Lightweight, zero-dependency GIF89a Animated Encoder in pure Kotlin.
 */
class GifEncoder {
    private var width = 0
    private var height = 0
    private var delay = 100 // ms
    private var repeat = 0 // 0 = infinite
    private var out: OutputStream? = null
    private var started = false

    fun setDelay(ms: Int) {
        delay = ms
    }

    fun setRepeat(loopCount: Int) {
        repeat = loopCount
    }

    fun start(os: OutputStream): Boolean {
        out = os
        started = true
        return true
    }

    fun addFrame(bitmap: Bitmap): Boolean {
        if (!started || out == null) return false
        val os = out ?: return false

        width = bitmap.width
        height = bitmap.height

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Generate 256 color palette & indexed pixels
        val (palette, indexedPixels) = quantizePixels(pixels)

        try {
            // First frame: write GIF header and Screen Descriptor
            if (firstFrame) {
                writeHeader(os)
                writeLogicalScreenDescriptor(os, width, height)
                writeNetscapeApplicationExtension(os)
                firstFrame = false
            }

            // Frame Graphic Control Extension
            writeGraphicControlExtension(os, delay)

            // Image Descriptor
            writeImageDescriptor(os, width, height)

            // Local Color Table (256 * 3 bytes)
            writeColorTable(os, palette)

            // LZW Image Data
            writeLzwData(os, indexedPixels, 8)

            os.flush()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private var firstFrame = true

    fun finish(): Boolean {
        if (!started || out == null) return false
        return try {
            out?.write(0x3B) // GIF Trailer ';'
            out?.flush()
            started = false
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun writeHeader(os: OutputStream) {
        os.write("GIF89a".toByteArray(Charsets.US_ASCII))
    }

    private fun writeLogicalScreenDescriptor(os: OutputStream, w: Int, h: Int) {
        writeShort(os, w)
        writeShort(os, h)
        // Packed field: 0x70 = no global color table, 8 bits color resolution
        os.write(0x70)
        os.write(0) // Background color index
        os.write(0) // Pixel aspect ratio
    }

    private fun writeNetscapeApplicationExtension(os: OutputStream) {
        if (repeat >= 0) {
            os.write(0x21) // Extension Introducer
            os.write(0xFF) // Application Extension Label
            os.write(11)   // Block Size
            os.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
            os.write(3)    // Sub-block length
            os.write(1)    // Sub-block ID
            writeShort(os, repeat) // Loop count (0 = infinite)
            os.write(0)    // Block Terminator
        }
    }

    private fun writeGraphicControlExtension(os: OutputStream, delayMs: Int) {
        os.write(0x21) // Extension Introducer
        os.write(0xF9) // Graphic Control Label
        os.write(4)    // Byte size
        os.write(0x00) // Packed field (no transparency, disposal: unspecified)
        val delayHundredths = (delayMs / 10).coerceAtLeast(1)
        writeShort(os, delayHundredths)
        os.write(0)    // Transparent color index
        os.write(0)    // Block Terminator
    }

    private fun writeImageDescriptor(os: OutputStream, w: Int, h: Int) {
        os.write(0x2C) // Image Separator
        writeShort(os, 0) // Left
        writeShort(os, 0) // Top
        writeShort(os, w) // Width
        writeShort(os, h) // Height
        // Packed fields: 0x87 = Local Color Table Present (0x80) + 256 colors (0x07)
        os.write(0x87)
    }

    private fun writeColorTable(os: OutputStream, palette: IntArray) {
        for (i in 0 until 256) {
            val color = if (i < palette.size) palette[i] else 0
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            os.write(r)
            os.write(g)
            os.write(b)
        }
    }

    private fun writeShort(os: OutputStream, value: Int) {
        os.write(value and 0xFF)
        os.write((value shr 8) and 0xFF)
    }

    /**
     * Simple, fast median-cut / uniform color quantizer mapping 32-bit ARGB to 256-color palette.
     */
    private fun quantizePixels(pixels: IntArray): Pair<IntArray, ByteArray> {
        val palette = IntArray(256)
        val indexed = ByteArray(pixels.size)

        // 3-3-2 bit uniform color map (8 bits total: 8 reds, 8 greens, 4 blues = 256 colors)
        for (r in 0 until 8) {
            for (g in 0 until 8) {
                for (b in 0 until 4) {
                    val index = (r shl 5) or (g shl 2) or b
                    val red = (r * 255) / 7
                    val green = (g * 255) / 7
                    val blue = (b * 255) / 3
                    palette[index] = (red shl 16) or (green shl 8) or blue
                }
            }
        }

        for (i in pixels.indices) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF) * 7 / 255
            val g = ((p shr 8) and 0xFF) * 7 / 255
            val b = (p and 0xFF) * 3 / 255
            val idx = (r shl 5) or (g shl 2) or b
            indexed[i] = idx.toByte()
        }

        return Pair(palette, indexed)
    }

    /**
     * GIF LZW compressor for indexed pixel stream.
     */
    private fun writeLzwData(os: OutputStream, pixels: ByteArray, initCodeSize: Int) {
        os.write(initCodeSize) // Minimum LZW code size

        val clearCode = 1 shl initCodeSize
        val eoiCode = clearCode + 1

        var codeSize = initCodeSize + 1
        var nextCode = eoiCode + 1
        var maxCode = 1 shl codeSize

        // Simple hash table for prefix-suffix dictionary
        val hSize = 5003
        val hTable = IntArray(hSize) { -1 }
        val codeTable = IntArray(hSize)

        fun hash(prefix: Int, suffix: Int): Int {
            return ((prefix shl 4) xor suffix) % hSize
        }

        // Bit accumulator
        var bitBucket = 0
        var bitCount = 0
        val block = ByteArray(255)
        var blockLen = 0

        fun outputByte(b: Int) {
            block[blockLen++] = b.toByte()
            if (blockLen == 254) {
                os.write(blockLen)
                os.write(block, 0, blockLen)
                blockLen = 0
            }
        }

        fun outputCode(code: Int) {
            bitBucket = bitBucket or (code shl bitCount)
            bitCount += codeSize

            while (bitCount >= 8) {
                outputByte(bitBucket and 0xFF)
                bitBucket = bitBucket ushr 8
                bitCount -= 8
            }
        }

        fun resetDict() {
            hTable.fill(-1)
            codeSize = initCodeSize + 1
            maxCode = 1 shl codeSize
            nextCode = eoiCode + 1
        }

        outputCode(clearCode)

        if (pixels.isNotEmpty()) {
            var prefix = pixels[0].toInt() and 0xFF

            for (i in 1 until pixels.size) {
                val suffix = pixels[i].toInt() and 0xFF
                var h = hash(prefix, suffix)
                if (h < 0) h += hSize

                var found = false
                while (hTable[h] != -1) {
                    if (hTable[h] == (prefix shl 8) or suffix) {
                        prefix = codeTable[h]
                        found = true
                        break
                    }
                    h = (h + 1) % hSize
                }

                if (!found) {
                    outputCode(prefix)

                    if (nextCode < 4096) {
                        hTable[h] = (prefix shl 8) or suffix
                        codeTable[h] = nextCode++
                        if (nextCode > maxCode && codeSize < 12) {
                            codeSize++
                            maxCode = 1 shl codeSize
                        }
                    } else {
                        outputCode(clearCode)
                        resetDict()
                    }
                    prefix = suffix
                }
            }
            outputCode(prefix)
        }

        outputCode(eoiCode)

        // Flush remaining bits
        if (bitCount > 0) {
            outputByte(bitBucket and 0xFF)
        }

        // Flush remaining block
        if (blockLen > 0) {
            os.write(blockLen)
            os.write(block, 0, blockLen)
            blockLen = 0
        }

        os.write(0) // Block terminator
    }
}
