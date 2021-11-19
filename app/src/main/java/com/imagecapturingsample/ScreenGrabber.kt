package com.imagecapturingsample

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.concurrent.thread

interface ScreenGrabber {
    fun stop()
}

class AndroidScreenGrabber(
    private val onImage: (image: ByteArray) -> Unit,
    private val applicationContext: Context
) : ImageReader.OnImageAvailableListener, ScreenGrabber {
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var displaySize = Point()
    private val displayMetrics = DisplayMetrics()

    private var imageCounter = 0

    private var handler: Handler? = null
    private var looper: Looper? = null
    private var imageReaderThread: Thread? = null

    fun start() {
        imageReaderThread = thread(start = true) {
            Looper.prepare()
            handler = Handler()
            looper = Looper.myLooper()
            Looper.loop()
        }
        val mediaProjection =
            applicationContext.getSystemService(MediaProjectionManager::class.java)

        /** [permissionIntent] should never be null after the first time it is set */
        permissionIntent?.let { intent ->
            val projection = mediaProjection?.getMediaProjection(Activity.RESULT_OK, intent)
            projection?.let {
                createVirtualDisplay(it)
            }
        }
    }

    override fun stop() {
        imageReader?.setOnImageAvailableListener(null, null)
        virtualDisplay?.release()
        looper?.quitSafely()
        imageReaderThread?.join()
        imageReader?.close()
    }

    /**
     * Create a virtual display needed for screen capturing.
     */
    private fun createVirtualDisplay(mediaProjection: MediaProjection) {
        val windowManager = applicationContext.getSystemService(WindowManager::class.java)
        val defaultDisplay = windowManager?.defaultDisplay

        defaultDisplay?.getMetrics(displayMetrics)
        defaultDisplay?.getSize(displaySize)

        imageReader =
            ImageReader.newInstance(displaySize.x, displaySize.y, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection.createVirtualDisplay(
            VIRTUAL_SCREEN_NAME, displaySize.x, displaySize.y, displayMetrics.densityDpi,
            VIRTUAL_DISPLAY_FLAGS, imageReader?.surface, null, handler
        )
        imageReader?.setOnImageAvailableListener(this, handler)
    }

    override fun onImageAvailable(reader: ImageReader?) {
        reader?.acquireLatestImage()?.use { image ->
            val (buffer, offset) = getBuffer(image)
            val scaledBitmap = createResultBitmap(buffer, offset)
            val compressedBitmap = compressBitmap(scaledBitmap)
            logImageCapture(compressedBitmap.size)
            onImage(compressedBitmap)
        }
    }

    private fun logImageCapture(size: Int) {
        if (imageCounter++ > LOG_IMAGE_THRESHOLD) {
            imageCounter = 0
            Log.i(TAG, "Encoded image size ${size / BYTES_IN_KILOBYTE} KiB")
        }
    }

    /**
     * Extract image buffer and offset form [Image].
     */
    private fun getBuffer(image: Image): Pair<ByteBuffer, Int> {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * displaySize.x
        return Pair(buffer, rowPadding / pixelStride)
    }

    /**
     * Convert the buffer to a Bitmap and scale it.
     */
    private fun createResultBitmap(buffer: ByteBuffer, offset: Int): Bitmap {
        val bitmap =
            Bitmap.createBitmap(displaySize.x + offset, displaySize.y, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        val scaledBitmap =
            Bitmap.createScaledBitmap(bitmap, RESULTING_IMAGE_WIDTH, RESULTING_IMAGE_HEIGHT, true)
        bitmap.recycle()
        return scaledBitmap
    }

    private fun compressBitmap(bitmap: Bitmap): ByteArray {
        val outputBytes = ByteArrayOutputStream()
        outputBytes.use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_COMPRESSION_QUALITY, outputBytes)
            return outputBytes.toByteArray()
        }
    }

    companion object {
        var permissionIntent: Intent? = null
        private const val VIRTUAL_DISPLAY_FLAGS =
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
        private const val VIRTUAL_SCREEN_NAME = "virtual-screen-recording"
        private val TAG = AndroidScreenGrabber::class.java.name

        /**
         * Holds the permission for screen recording. [permissionIntent] should never be null
         * after the first time it is set.
         * */

        private const val LOG_IMAGE_THRESHOLD = 100
        private const val BYTES_IN_KILOBYTE = 1024
        private const val JPEG_COMPRESSION_QUALITY = 16
        private const val RESULTING_IMAGE_WIDTH = 960
        private const val RESULTING_IMAGE_HEIGHT = 540
    }
}
