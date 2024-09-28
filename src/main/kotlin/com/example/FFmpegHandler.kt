package com.example

import net.bramp.ffmpeg.FFmpeg
import net.bramp.ffmpeg.FFprobe
import java.io.File
import kotlin.system.exitProcess

class FFmpegHandler {
    private val ffmpeg: FFmpeg
    private val ffprobe: FFprobe

    init {
        val ffmpegPath = "${System.getProperty("user.dir")}/src/ffmpeg/bin/"
        try {
            ffmpeg = FFmpeg(File(ffmpegPath, "ffmpeg").absolutePath)
            ffprobe = FFprobe(File(ffmpegPath, "ffprobe").absolutePath)
            println("FFmpeg доступен.")
        } catch (e: Exception) {
            println("Ошибка: FFmpeg не доступен. ${e.message}")
            exitProcess(1)
        }
    }

    fun getFFmpeg(): FFmpeg = ffmpeg
    fun getFFprobe(): FFprobe = ffprobe
    fun cancel() {
        TODO("Not yet implemented")
    }
}