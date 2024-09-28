package com.example

import javafx.application.Platform
import javafx.scene.control.ProgressBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bramp.ffmpeg.FFmpegExecutor
import net.bramp.ffmpeg.FFmpegUtils
import net.bramp.ffmpeg.builder.FFmpegBuilder
import net.bramp.ffmpeg.probe.FFmpegProbeResult
import net.bramp.ffmpeg.progress.Progress
import net.bramp.ffmpeg.progress.ProgressListener
import java.io.File
import java.util.concurrent.TimeUnit

class VideoCompressor(
    private val ffmpegHandler: FFmpegHandler,
    private val outputFormat: String,
    private val outputSize: String
) {
    private val availableFormats = listOf("mp4", "avi", "mov", "mkv", "flv")

    // Resolutions for 4:3 aspect ratio
    private val resolutionMap = mapOf(
        "360p" to Pair(480, 360),
        "480p" to Pair(640, 480),
        "1080p" to Pair(1440, 1080),
        "2K" to Pair(1920, 1440),
        "4K" to Pair(2880, 2160)
    )

    suspend fun compressVideo(inputPath: String, progressBar: ProgressBar) = withContext(Dispatchers.IO) {
        val filename = File(inputPath).name
        val outputFilename = "${filename.substringBeforeLast(".")}.$outputFormat"
        val outputPath = "${System.getProperty("user.dir")}/$outputFilename"

        val (width, height) = resolutionMap[outputSize] ?: throw IllegalArgumentException("Invalid resolution")

        println("Обработка $filename...")

        // Build the FFmpeg command using the wrapper
        val builder = FFmpegBuilder()
            .setInput(inputPath)
            .addOutput(outputPath)
            .setFormat(outputFormat)
            .setVideoResolution(width, height)
            .done()

        val executor = FFmpegExecutor(ffmpegHandler.getFFmpeg(), ffmpegHandler.getFFprobe())

        val probe: FFmpegProbeResult = ffmpegHandler.getFFprobe().probe(inputPath)
        val totalDuration = probe.format.duration // получаем длительность видео

        // Execute the FFmpeg command with a progress listener
        executor.createJob(builder, object : ProgressListener {
            override fun progress(progress: Progress) {
                val percentage = progress.out_time_ns / 1_000_000_000.0 / totalDuration
                Platform.runLater {
                    progressBar.progress = percentage
                }
            }
        }).run()

        println("Видео $filename успешно сжато и сохранено как $outputFilename")
    }
}
