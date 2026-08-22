package com.sidegallery.app

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min

object GifConverter {

    private const val TAG = "GifConverter"
    const val MAX_VIDEO_DURATION_MS = 15_000L // 15 seconds max

    /**
     * Extracts frames across the selected segment of a video and encodes them into
     * a fully animated GIF89a file.
     */
    suspend fun convertVideoToGif(
        context: Context,
        videoUri: Uri,
        destFile: File,
        targetWidth: Int = 320,
        fps: Int = 8,
        startMs: Long = 0L,
        endMs: Long? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        var tempVideoFile: File? = null
        try {
            // Guarantee seekable local file descriptor for MediaMetadataRetriever
            tempVideoFile = File(context.cacheDir, "temp_vid_in_${System.currentTimeMillis()}.mp4")
            context.contentResolver.openInputStream(videoUri)?.use { input ->
                FileOutputStream(tempVideoFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (!tempVideoFile.exists() || tempVideoFile.length() <= 0L) {
                Log.e(TAG, "convertVideoToGif: tempVideoFile does not exist or is empty")
                return@withContext false
            }

            retriever.setDataSource(tempVideoFile.absolutePath)

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val rawDurationMs = durationStr?.toLongOrNull() ?: 5000L

            var safeStartMs = startMs
            var safeEndMs = endMs ?: (startMs + MAX_VIDEO_DURATION_MS)

            // Double Trim / Offset Protection:
            // If startMs >= rawDurationMs, the input video was already trimmed from 0 to N ms
            if (safeStartMs >= rawDurationMs || safeStartMs < 0L) {
                Log.w(TAG, "convertVideoToGif: startMs ($safeStartMs) >= rawDurationMs ($rawDurationMs) or negative. Resetting to 0..$rawDurationMs")
                safeStartMs = 0L
                safeEndMs = rawDurationMs
            } else {
                safeStartMs = safeStartMs.coerceIn(0L, rawDurationMs)
                safeEndMs = safeEndMs.coerceIn(safeStartMs + 300L, safeStartMs + MAX_VIDEO_DURATION_MS).coerceAtMost(rawDurationMs)
            }

            val actualDurationMs = max(300L, safeEndMs - safeStartMs)

            // Maintain smooth framerate (8-10 FPS) and balanced resolution (260-320px)
            val finalWidth = if (actualDurationMs > 6000L) min(targetWidth, 260) else targetWidth
            val targetFps = fps.coerceIn(8, 12)
            val frameIntervalMs = (1000 / targetFps).coerceIn(80, 150)
            val totalFrames = ((actualDurationMs / frameIntervalMs) + 1).toInt().coerceIn(2, 160)

            Log.d(TAG, "convertVideoToGif: rawDurationMs=$rawDurationMs, reqStartMs=$startMs, reqEndMs=$endMs -> safeStartMs=$safeStartMs, safeEndMs=$safeEndMs, actualDurationMs=$actualDurationMs, totalFrames=$totalFrames, frameIntervalMs=$frameIntervalMs, size=${finalWidth}px @ ${targetFps}fps")

            // Extract initial frame to determine aspect ratio
            val firstFrameTimeUs = safeStartMs * 1000L
            val firstFrameRaw = extractFrame(retriever, firstFrameTimeUs, finalWidth, null)
            if (firstFrameRaw == null) {
                Log.e(TAG, "convertVideoToGif: Failed to extract initial frame at $firstFrameTimeUs us")
                return@withContext false
            }

            val aspect = firstFrameRaw.height.toFloat() / firstFrameRaw.width.toFloat()
            val targetHeight = (finalWidth * aspect).toInt().coerceAtLeast(1)

            destFile.parentFile?.mkdirs()
            val encoder = GifEncoder()
            val outputStream = FileOutputStream(destFile)
            encoder.start(outputStream)
            encoder.setDelay(frameIntervalMs)
            encoder.setRepeat(0) // Infinite loop

            var framesAdded = 0

            // Add first frame scaled
            val scaledFirst = if (firstFrameRaw.width == finalWidth && firstFrameRaw.height == targetHeight) {
                firstFrameRaw
            } else {
                Bitmap.createScaledBitmap(firstFrameRaw, finalWidth, targetHeight, true)
            }
            if (encoder.addFrame(scaledFirst)) {
                framesAdded++
            }
            if (scaledFirst != firstFrameRaw) {
                scaledFirst.recycle()
            }
            firstFrameRaw.recycle()

            // Extract remaining frames across the selected segment
            for (i in 1 until totalFrames) {
                val currentMs = safeStartMs + (i * frameIntervalMs)
                if (currentMs > safeEndMs) {
                    Log.d(TAG, "convertVideoToGif: currentMs ($currentMs) > safeEndMs ($safeEndMs), breaking loop at frame $i")
                    break
                }
                val frameTimeUs = currentMs * 1000L

                val rawBitmap = extractFrame(retriever, frameTimeUs, finalWidth, targetHeight)
                if (rawBitmap != null) {
                    val scaledBitmap = if (rawBitmap.width == finalWidth && rawBitmap.height == targetHeight) {
                        rawBitmap
                    } else {
                        Bitmap.createScaledBitmap(rawBitmap, finalWidth, targetHeight, true)
                    }
                    if (encoder.addFrame(scaledBitmap)) {
                        framesAdded++
                    }
                    if (scaledBitmap != rawBitmap) {
                        scaledBitmap.recycle()
                    }
                    rawBitmap.recycle()
                } else {
                    Log.w(TAG, "convertVideoToGif: frame $i at $frameTimeUs us was null, skipped")
                }
            }

            encoder.finish()
            outputStream.close()

            Log.d(TAG, "convertVideoToGif: finished encoding. framesAdded=$framesAdded / $totalFrames, destSize=${destFile.length()} bytes")

            return@withContext framesAdded > 0 && destFile.exists() && destFile.length() > 0
        } catch (e: Exception) {
            Log.e(TAG, "convertVideoToGif error: ${e.message}", e)
            destFile.delete()
            return@withContext false
        } finally {
            try { retriever.release() } catch (e: Exception) {}
            tempVideoFile?.let {
                if (it.exists()) it.delete()
            }
        }
    }

    private fun extractFrame(
        retriever: MediaMetadataRetriever,
        timeUs: Long,
        dstWidth: Int? = null,
        dstHeight: Int? = null
    ): Bitmap? {
        // 1. Preferred closest frame scaled (Android 8.1+) - drastically reduces decode memory & prevents decoder stalls on long/high-res videos
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && dstWidth != null && dstHeight != null && dstWidth > 0 && dstHeight > 0) {
            try {
                val b = retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST, dstWidth, dstHeight)
                if (b != null) return b
            } catch (e: Throwable) {
                Log.w(TAG, "extractFrame getScaledFrameAtTime OPTION_CLOSEST failed at $timeUs us: ${e.message}")
            }
        }

        // 2. Preferred closest frame full size
        try {
            val b = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            if (b != null) return b
        } catch (e: Throwable) {
            Log.w(TAG, "extractFrame OPTION_CLOSEST failed at $timeUs us: ${e.message}")
        }

        // 3. Scaled fallback to sync frame
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && dstWidth != null && dstHeight != null && dstWidth > 0 && dstHeight > 0) {
            try {
                val b = retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, dstWidth, dstHeight)
                if (b != null) return b
            } catch (e: Throwable) {}
        }

        // 4. Fallback to closest sync frame
        try {
            val b = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (b != null) return b
        } catch (e: Throwable) {
            Log.w(TAG, "extractFrame OPTION_CLOSEST_SYNC failed at $timeUs us: ${e.message}")
        }

        // 5. Fallback to previous sync frame
        try {
            val b = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_PREVIOUS_SYNC)
            if (b != null) return b
        } catch (e: Throwable) {
            Log.w(TAG, "extractFrame OPTION_PREVIOUS_SYNC failed at $timeUs us: ${e.message}")
        }

        // 6. Fallback to next sync frame
        try {
            val b = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_NEXT_SYNC)
            if (b != null) return b
        } catch (e: Throwable) {
            Log.w(TAG, "extractFrame OPTION_NEXT_SYNC failed at $timeUs us: ${e.message}")
        }

        Log.e(TAG, "extractFrame: all options returned null at $timeUs us")
        return null
    }
}

/**
 * Lightweight, zero-dependency GIF89a Animated Encoder in pure Kotlin.
 */
class GifEncoder {
    private var width = 0
    private var height = 0
    private var delay = 100 // ms
    private var repeat = 0 // 0 = infinite loop
    private var out: OutputStream? = null
    private var started = false
    private var firstFrame = true

    fun setDelay(ms: Int) {
        delay = ms
    }

    fun setRepeat(loopCount: Int) {
        repeat = loopCount
    }

    fun start(os: OutputStream): Boolean {
        out = os
        started = true
        firstFrame = true
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
            // First frame: write GIF header, Screen Descriptor with Global Color Table, and Netscape loop block
            if (firstFrame) {
                writeHeader(os)
                writeLogicalScreenDescriptor(os, width, height, palette)
                writeNetscapeApplicationExtension(os)
                firstFrame = false
            }

            // Frame Graphic Control Extension (Disposal 1 = overwrite/leave in place)
            writeGraphicControlExtension(os, delay)

            // Image Descriptor (Local Color Table present: 0x87)
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

    private fun writeLogicalScreenDescriptor(os: OutputStream, w: Int, h: Int, globalPalette: IntArray) {
        writeShort(os, w)
        writeShort(os, h)
        // Packed field: 0xF7 = Global Color Table present (0x80) + 8-bit resolution (0x70) + 256 colors (0x07)
        os.write(0xF7)
        os.write(0) // Background color index
        os.write(0) // Pixel aspect ratio
        // Write Global Color Table
        writeColorTable(os, globalPalette)
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
        // Packed field: 0x04 = Disposal method 1 (Do not dispose / Leave in place), no transparency
        os.write(0x04)
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
     * Fast 3-3-2 uniform color quantizer mapping 32-bit ARGB to 256-color palette.
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
     * Standard GIF LZW compressor.
     */
    private fun writeLzwData(os: OutputStream, pixels: ByteArray, initCodeSize: Int) {
        val colorDepth = 8
        val actualInitCodeSize = kotlin.math.max(2, colorDepth)
        os.write(actualInitCodeSize)

        val BITS = 12
        val HSIZE = 5003
        val masks = intArrayOf(
            0x0000, 0x0001, 0x0003, 0x0007, 0x000F, 0x001F, 0x003F, 0x007F, 0x00FF,
            0x01FF, 0x03FF, 0x07FF, 0x0FFF, 0x1FFF, 0x3FFF, 0x7FFF, 0xFFFF
        )

        var n_bits = 0
        val maxbits = BITS
        var maxcode = 0
        val maxmaxcode = 1 shl BITS

        val htab = IntArray(HSIZE)
        val codetab = IntArray(HSIZE)
        val hsize = HSIZE
        var free_ent = 0
        var clear_flg = false

        var g_init_bits = 0
        var ClearCode = 0
        var EOFCode = 0

        var cur_accum = 0
        var cur_bits = 0
        var a_count = 0
        val accum = ByteArray(256)

        fun flush_char(outs: OutputStream) {
            if (a_count > 0) {
                outs.write(a_count)
                outs.write(accum, 0, a_count)
                a_count = 0
            }
        }

        fun char_out(c: Byte, outs: OutputStream) {
            accum[a_count++] = c
            if (a_count >= 254) flush_char(outs)
        }

        fun MAXCODE(nBits: Int) = (1 shl nBits) - 1

        fun output(code: Int, outs: OutputStream) {
            cur_accum = cur_accum and masks[cur_bits]
            if (cur_bits > 0)
                cur_accum = cur_accum or (code shl cur_bits)
            else
                cur_accum = code
            cur_bits += n_bits
            while (cur_bits >= 8) {
                char_out((cur_accum and 0xff).toByte(), outs)
                cur_accum = cur_accum ushr 8
                cur_bits -= 8
            }

            if (free_ent > maxcode || clear_flg) {
                if (clear_flg) {
                    n_bits = g_init_bits
                    maxcode = MAXCODE(n_bits)
                    clear_flg = false
                } else {
                    ++n_bits
                    if (n_bits == maxbits)
                        maxcode = maxmaxcode
                    else
                        maxcode = MAXCODE(n_bits)
                }
            }
            if (code == EOFCode) {
                while (cur_bits > 0) {
                    char_out((cur_accum and 0xff).toByte(), outs)
                    cur_accum = cur_accum ushr 8
                    cur_bits -= 8
                }
                flush_char(outs)
            }
        }

        fun cl_hash(hSize: Int) {
            for (i in 0 until hSize) htab[i] = -1
        }

        fun cl_block(outs: OutputStream) {
            cl_hash(hsize)
            free_ent = ClearCode + 2
            clear_flg = true
            output(ClearCode, outs)
        }

        fun compress(init_bits: Int, outs: OutputStream) {
            var fcode: Int
            var i: Int
            var c: Int
            var ent: Int
            var disp: Int
            var hshift: Int

            g_init_bits = init_bits
            clear_flg = false
            n_bits = g_init_bits
            maxcode = MAXCODE(n_bits)
            ClearCode = 1 shl (init_bits - 1)
            EOFCode = ClearCode + 1
            free_ent = ClearCode + 2
            a_count = 0

            var curPixel = 0
            fun nextPixel(): Int {
                if (curPixel >= pixels.size) return -1
                return pixels[curPixel++].toInt() and 0xFF
            }

            ent = nextPixel()
            hshift = 0
            var tmp = hsize
            while (tmp < 65536) {
                tmp *= 2
                ++hshift
            }
            hshift = 8 - hshift
            val hsize_reg = hsize
            cl_hash(hsize_reg)
            output(ClearCode, outs)

            while (true) {
                c = nextPixel()
                if (c == -1) break

                fcode = (c shl maxbits) + ent
                i = (c shl hshift) xor ent

                if (htab[i] == fcode) {
                    ent = codetab[i]
                    continue
                } else if (htab[i] >= 0) {
                    disp = hsize_reg - i
                    if (i == 0) disp = 1
                    var hit = false
                    do {
                        i -= disp
                        if (i < 0) i += hsize_reg
                        if (htab[i] == fcode) {
                            ent = codetab[i]
                            hit = true
                            break
                        }
                    } while (htab[i] >= 0)
                    if (hit) continue
                }

                output(ent, outs)
                ent = c
                if (free_ent < maxmaxcode) {
                    codetab[i] = free_ent++
                    htab[i] = fcode
                } else {
                    cl_block(outs)
                }
            }

            output(ent, outs)
            output(EOFCode, outs)
        }

        compress(actualInitCodeSize + 1, os)
        os.write(0) // Block terminator
    }
}
