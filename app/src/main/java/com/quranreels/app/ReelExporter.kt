package com.quranreels.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Environment
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReelExporter {
    private const val WIDTH = 720
    private const val HEIGHT = 1280
    private const val FPS = 30
    private const val DURATION_MS = 10_000L

    fun export(
        context: Context,
        verse: String,
        meta: String,
        overlay: String,
        backgroundUri: Uri?,
        audioUri: Uri?,
        onProgress: (Int) -> Unit
    ): File {
        val movies = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: throw IllegalStateException("Movies directory is unavailable")
        movies.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val temporaryVideo = File(movies, "render_$stamp.mp4")
        val finalVideo = File(movies, "quran_reel_$stamp.mp4")
        val background = loadBackground(context, backgroundUri)
        val recorder = MediaRecorder()

        try {
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            recorder.setVideoSize(WIDTH, HEIGHT)
            recorder.setVideoFrameRate(FPS)
            recorder.setVideoEncodingBitRate(4_000_000)
            recorder.setOutputFile(temporaryVideo.absolutePath)
            recorder.prepare()
            val surface = recorder.surface
            recorder.start()

            val frameCount = (DURATION_MS * FPS / 1000L).toInt()
            val renderStartNs = System.nanoTime()
            for (frame in 0 until frameCount) {
                val canvas = surface.lockCanvas(null)
                drawFrame(canvas, background, verse, meta, overlay, frame, frameCount)
                surface.unlockCanvasAndPost(canvas)
                if (frame % FPS == 0) onProgress((frame * 100) / frameCount)
                val targetNs = renderStartNs + ((frame + 1).toLong() * 1_000_000_000L / FPS)
                val remainingMs = (targetNs - System.nanoTime()) / 1_000_000L
                if (remainingMs > 0) Thread.sleep(remainingMs.coerceAtMost(34L))
            }
            recorder.stop()
            recorder.reset()
            recorder.release()

            if (audioUri != null) {
                muxAudio(context, temporaryVideo, audioUri, finalVideo, DURATION_MS * 1000L)
                temporaryVideo.delete()
            } else {
                if (!temporaryVideo.renameTo(finalVideo)) {
                    temporaryVideo.copyTo(finalVideo, overwrite = true)
                    temporaryVideo.delete()
                }
            }
            onProgress(100)
            return finalVideo
        } catch (error: Throwable) {
            runCatching { recorder.reset() }
            runCatching { recorder.release() }
            temporaryVideo.delete()
            finalVideo.delete()
            throw error
        } finally {
            background?.recycle()
        }
    }

    private fun loadBackground(context: Context, uri: Uri?): Bitmap? {
        if (uri == null) return null
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }

    private fun drawFrame(
        canvas: Canvas,
        background: Bitmap?,
        verse: String,
        meta: String,
        overlay: String,
        frame: Int,
        frameCount: Int
    ) {
        val progress = frame.toFloat() / frameCount.coerceAtLeast(1)
        val gradient = LinearGradient(
            0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(),
            intArrayOf(Color.rgb(18, 55, 44), Color.rgb(10, 20, 17), Color.rgb(5, 9, 8)),
            null, Shader.TileMode.CLAMP
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = gradient
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
        paint.shader = null

        if (background != null) {
            val source = Rect(0, 0, background.width, background.height)
            val scale = maxOf(WIDTH.toFloat() / background.width, HEIGHT.toFloat() / background.height)
            val scaledW = (background.width * scale).toInt()
            val scaledH = (background.height * scale).toInt()
            val destination = Rect((WIDTH - scaledW) / 2, (HEIGHT - scaledH) / 2, (WIDTH + scaledW) / 2, (HEIGHT + scaledH) / 2)
            paint.alpha = 115
            canvas.drawBitmap(background, source, destination, paint)
            paint.alpha = 255
        }

        paint.color = Color.argb(145, 0, 0, 0)
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)

        paint.color = Color.rgb(216, 182, 106)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 28f
        paint.typeface = android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD)
        canvas.drawText("ريلز قرآني", WIDTH / 2f, 132f, paint)

        paint.color = Color.WHITE
        paint.textSize = 38f
        paint.typeface = android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL)
        val verseY = HEIGHT / 2f
        canvas.drawText(verse, WIDTH / 2f, verseY, paint)

        paint.color = Color.rgb(216, 182, 106)
        paint.textSize = 20f
        canvas.drawText(meta, WIDTH / 2f, verseY + 70f, paint)

        val barLeft = 100f
        val barRight = WIDTH - 100f
        val barY = verseY + 135f
        paint.color = Color.argb(95, 255, 255, 255)
        canvas.drawRoundRect(barLeft, barY, barRight, barY + 8f, 8f, 8f, paint)
        paint.color = Color.rgb(32, 201, 139)
        canvas.drawRoundRect(barLeft, barY, barLeft + (barRight - barLeft) * progress, barY + 8f, 8f, 8f, paint)

        paint.color = Color.WHITE
        paint.textSize = 17f
        paint.alpha = 210
        canvas.drawText("تزامن النص مع التلاوة", WIDTH / 2f, barY + 48f, paint)
        val words = verse.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isNotEmpty()) {
            val wordIndex = (progress * words.size).toInt().coerceIn(0, words.lastIndex)
            paint.color = Color.rgb(216, 182, 106)
            paint.textSize = 18f
            paint.alpha = 245
            canvas.drawText("الكلمة الحالية: ${words[wordIndex]}", WIDTH / 2f, barY + 82f, paint)
        }
        if (overlay.isNotBlank()) {
            paint.alpha = 230
            paint.textSize = 22f
            canvas.drawText(overlay, WIDTH / 2f, HEIGHT - 120f, paint)
        }
        paint.alpha = 255
    }

    private fun muxAudio(context: Context, video: File, audioUri: Uri, output: File, maxDurationUs: Long) {
        val videoExtractor = MediaExtractor()
        videoExtractor.setDataSource(video.absolutePath)
        val audioExtractor = MediaExtractor()
        context.contentResolver.openAssetFileDescriptor(audioUri, "r")?.use { descriptor ->
            audioExtractor.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
        } ?: throw IllegalArgumentException("Audio file cannot be opened")

        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val videoTrack = selectTrack(videoExtractor, "video/")
            val audioTrack = selectTrack(audioExtractor, "audio/")
            if (videoTrack < 0) throw IllegalStateException("Generated video track is missing")
            val muxVideo = muxer.addTrack(videoExtractor.getTrackFormat(videoTrack))
            val muxAudio = if (audioTrack >= 0) muxer.addTrack(audioExtractor.getTrackFormat(audioTrack)) else -1
            muxer.start()
            copyTrack(videoExtractor, videoTrack, muxer, muxVideo, maxDurationUs)
            if (audioTrack >= 0) copyTrack(audioExtractor, audioTrack, muxer, muxAudio, maxDurationUs)
            muxer.stop()
        } finally {
            runCatching { muxer.release() }
            videoExtractor.release()
            audioExtractor.release()
        }
    }

    private fun selectTrack(extractor: MediaExtractor, prefix: String): Int {
        for (index in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith(prefix)) return index
        }
        return -1
    }

    private fun copyTrack(extractor: MediaExtractor, track: Int, muxer: MediaMuxer, destination: Int, maxDurationUs: Long) {
        extractor.selectTrack(track)
        val buffer = ByteBuffer.allocate(1024 * 1024)
        val info = android.media.MediaCodec.BufferInfo()
        while (true) {
            buffer.clear()
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0 || extractor.sampleTime > maxDurationUs) break
            info.offset = 0
            info.size = size
            info.presentationTimeUs = extractor.sampleTime
            info.flags = extractor.sampleFlags
            muxer.writeSampleData(destination, buffer, info)
            extractor.advance()
        }
        extractor.unselectTrack(track)
    }
}
