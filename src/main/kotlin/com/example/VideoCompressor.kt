import com.example.FFmpegHandler
import kotlinx.coroutines.*
import java.util.concurrent.CancellationException
import java.io.File
import kotlin.system.exitProcess


class VideoCompressor(private val ffmpegHandler: FFmpegHandler, private val outputFormat: String) {
    private val availableFormats = listOf("mp4", "avi", "mov", "mkv", "flv")

    init {
        if (outputFormat !in availableFormats) {
            println("Некорректный формат. Программа завершена.")
            exitProcess(1)
        }
    }

    private var process: Process? = null // Store the reference to the FFmpeg process

    suspend fun compressVideo(inputPath: String) {
        val filename = File(inputPath).name
        val outputFilename = "${filename.substringBeforeLast(".")}.$outputFormat"
        val outputPath = "${System.getProperty("user.dir")}/$outputFilename"

        println("Обработка $filename...")

        val command = listOf(
            ffmpegHandler.getPath(),
            "-i", inputPath,
            "-vf", "scale=w=640:h=480:force_original_aspect_ratio=decrease",
            "-y", outputPath
        )

        process = ProcessBuilder(command)
            .redirectErrorStream(true) // Combine error and output streams
            .start()

        // Run in a loop to read the process output and check for cancellation
        coroutineScope {
            val outputJob = launch {
                process!!.inputStream.bufferedReader().use { reader ->
                    reader.lines().forEach { line ->
                        println(line) // Print each line from the FFmpeg output
                    }
                }
            }

            // Wait for the process to finish or be canceled
            withContext(Dispatchers.IO) {
                try {
                    val exitCode = process!!.waitFor()
                    if (exitCode == 0) {
                        println("Видео $filename успешно сжато и сохранено как $outputFilename")
                    } else {
                        println("Ошибка при обработке видео $filename")
                    }
                } catch (e: InterruptedException) {
                    // If interrupted, destroy the process
                    process?.destroyForcibly() // Use destroyForcibly to ensure termination
                    println("Процесс сжатия видео был прерван.")
                    throw CancellationException("Compression canceled")
                } finally {
                    outputJob.cancel() // Cancel output reading
                }
            }
        }
    }

    fun cancel() {
        process?.destroyForcibly() // Ensure to forcibly destroy the FFmpeg process if it exists
        process = null // Clear the reference to the process
        println("Все процессы конвертации отменены.")
    }
}