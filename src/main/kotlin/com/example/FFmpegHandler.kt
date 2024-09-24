package com.example

import java.io.File
import java.io.IOException
import java.lang.reflect.Field
import kotlin.system.exitProcess

class FFmpegHandler {
    private val ffmpegPath: String = "${System.getProperty("user.dir")}/src/ffmpeg/bin/ffmpeg.exe"
    private var process: Process? = null // Store the reference to the FFmpeg process

    init {
        if (!File(ffmpegPath).exists()) {
            println("FFmpeg не найден по пути: $ffmpegPath")
            exitProcess(1)
        }

        try {
            ProcessBuilder(ffmpegPath, "-version").start().waitFor()
            println("FFmpeg доступен.")
        } catch (e: IOException) {
            println("Ошибка: FFmpeg не доступен. ${e.message}")
            exitProcess(1)
        }
    }

    fun getPath(): String {
        return ffmpegPath
    }

    fun cancel() {
        process?.let {
            val pid = getPid(it)
            if (pid != -1) {
                try {
                    // Отправляем сигнал завершения
                    ProcessBuilder("taskkill", "/F", "/PID", pid.toString()).start().waitFor()
                    println("FFmpeg процесс с PID $pid завершен.")
                } catch (e: IOException) {
                    println("Ошибка при завершении процесса: ${e.message}")
                }
            }
            it.destroyForcibly() // Принудительно завершить процесс
            process = null // Очистить ссылку на процесс
        }
    }

    private fun getPid(process: Process): Int {
        return try {
            // Получаем PID из внутреннего поля процесса
            val field: Field = process.javaClass.getDeclaredField("pid")
            field.isAccessible = true
            field.getInt(process)
        } catch (e: Exception) {
            -1 // Если не удалось получить PID, возвращаем -1
        }
    }
}