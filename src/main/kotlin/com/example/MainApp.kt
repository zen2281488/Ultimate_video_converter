package com.example

import VideoCompressor
import javafx.application.Application
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.FileChooser
import javafx.stage.Stage
import kotlinx.coroutines.*
import java.io.OutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets

class MainApp : Application() {

    private val conversionJobs = mutableListOf<Job>() // Store all conversion jobs
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob()) // Coroutine scope for the application

    private lateinit var frameLabel: Label
    private lateinit var fpsLabel: Label
    private lateinit var bitrateLabel: Label
    private lateinit var speedLabel: Label

    override fun start(primaryStage: Stage) {
        primaryStage.title = "Video Converter"

        val ffmpegHandler = FFmpegHandler()

        // Field for displaying selected files
        val selectedFilesLabel = Label("Selected Files:")
        val fileListView = ListView<String>()

        // Button to select files
        val chooseFileButton = Button("Select Video Files").apply {
            setOnAction {
                val fileChooser = FileChooser()
                fileChooser.title = "Choose Video Files"
                fileChooser.extensionFilters.add(
                    FileChooser.ExtensionFilter(
                        "Video Files", "*.mp4", "*.avi", "*.mov", "*.mkv", "*.flv"
                    )
                )
                val selectedFiles = fileChooser.showOpenMultipleDialog(primaryStage)
                if (selectedFiles != null) {
                    fileListView.items.clear()
                    fileListView.items.addAll(selectedFiles.map { it.absolutePath })
                }
            }
        }

        // Field to select conversion format
        val formatLabel = Label("Select Format for Conversion:")
        val formatChoiceBox = ChoiceBox<String>().apply {
            items.addAll("mp4", "avi", "mov", "mkv", "flv")
            value = "mp4" // default value
        }

        // Field for displaying logs
        val logArea = TextArea().apply {
            isEditable = false // Make the console read-only
            style = "-fx-font-family: 'monospace';" // Set monospaced font
            prefWidth = 500.0 // Preferred width
            prefHeight = 500.0 // Preferred height
        }

        // Initialize labels for conversion parameters
        frameLabel = Label("Frame: N/A").apply { isVisible = true }
        fpsLabel = Label("FPS: N/A").apply { isVisible = true }
        bitrateLabel = Label("Bitrate: N/A").apply { isVisible = true }
        speedLabel = Label("Speed: N/A").apply { isVisible = true }

        // Redirect standard output to the log with UTF-8 support
        val outputStream = ConsoleOutputStream(logArea, ::updateLabels)
        System.setOut(PrintStream(outputStream, true, Charsets.UTF_8.name()))
        System.setErr(PrintStream(outputStream, true, Charsets.UTF_8.name()))

        // Progress bar for video conversion
        val progressBar = ProgressBar(0.0).apply {
            isVisible = true // Initially hidden
        }

        // Buttons for conversion and cancellation
        val convertButton = Button("Convert")
        val cancelButton = Button("Cancel").apply {
            isDisable = true // Disable cancel button initially
            isVisible = false
        }

        convertButton.setOnAction {
            val selectedFormat = formatChoiceBox.value
            val selectedFiles = fileListView.items
            convertButton.isDisable = true
            if (selectedFiles.isEmpty()) {
                showAlert("Error", "Please select at least one file")
                return@setOnAction
            }

            // Start video compression for each selected file
            conversionJobs.clear() // Clear previous jobs
            cancelButton.isDisable = false // Enable cancel button
            progressBar.progress = 0.0 // Reset progress bar
            progressBar.isVisible = true // Show progress bar

            val totalFiles = selectedFiles.size
            var processedFiles = 0

            selectedFiles.forEach { filePath ->
                val job = coroutineScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, exception ->
                    Platform.runLater {
                        showAlert("Error", "Failed to convert $filePath: ${exception.message}")
                    }
                }) {
                    val videoCompressor = VideoCompressor(ffmpegHandler, selectedFormat)
                    try {
                        videoCompressor.compressVideo(filePath) // Call compressVideo directly for each file
                        processedFiles++

                        // Update progress on the main thread
                        withContext(Dispatchers.Main) {
                            progressBar.progress = processedFiles.toDouble() / totalFiles
                            println("Conversion completed for $filePath")
                        }
                    } catch (e: CancellationException) {
                        withContext(Dispatchers.Main) {
                            println("Conversion cancelled for $filePath")
                        }
                    }
                }
                conversionJobs.add(job) // Store the job reference
            }

            println("Conversion started...")
        }

        cancelButton.setOnAction {
            conversionJobs.forEach { job -> job.cancel() } // Cancel all ongoing conversion jobs
            conversionJobs.clear() // Clear the job list
            ffmpegHandler.cancel() // Call the cancel method in VideoCompressor to terminate FFmpeg
            cancelButton.isDisable = true // Disable cancel button
            progressBar.isVisible = false // Hide progress bar
            println("All conversions cancelled.")
        }

        // Layout for buttons
        val buttonLayout = HBox(10.0, convertButton, cancelButton)

        // Create main layout and add elements
        val vbox = VBox(10.0, selectedFilesLabel, fileListView, chooseFileButton, formatLabel, formatChoiceBox, buttonLayout, logArea, progressBar, frameLabel, fpsLabel, bitrateLabel, speedLabel).apply {
            padding = Insets(20.0)
        }

        // Create scene and show window
        val scene = Scene(vbox, 600.0, 400.0)
        primaryStage.scene = scene
        primaryStage.show()

        // Example of initial logging
        println("Application started. Ready for use.")
    }

    // Function to show alerts
    private fun showAlert(title: String, content: String) {
        val alert = Alert(Alert.AlertType.INFORMATION)
        alert.title = title
        alert.contentText = content
        alert.showAndWait()
    }

    // Method to update labels
    private fun updateLabels(frame: String?, fps: String?, bitrate: String?, speed: String?) {
        Platform.runLater {
            frameLabel.text = "Frame: ${frame ?: "N/A"}"
            fpsLabel.text = "FPS: ${fps ?: "N/A"}"
            bitrateLabel.text = "Bitrate: ${bitrate ?: "N/A"} kbits/s"
            speedLabel.text = "Speed: ${speed ?: "N/A"}x"
        }
    }

    class ConsoleOutputStream(private val textArea: TextArea, private val updateLabels: (String?, String?, String?, String?) -> Unit) : OutputStream() {
        private val buffer = StringBuilder()

        override fun write(b: Int) {
            val char = b.toChar()

            Platform.runLater {
                val scrollPosition = textArea.scrollTop
                val contentHeight = calculateContentHeight(textArea)
                val visibleHeight = textArea.height
                val atBottom = scrollPosition + visibleHeight >= contentHeight

                if (char == '\r') {
                    val currentText = buffer.toString()
                    val lastIndex = textArea.text.lastIndexOf('\n')
                    if (lastIndex != -1) {
                        textArea.deleteText(lastIndex + 1, textArea.length)
                    } else {
                        textArea.clear()
                    }
                    textArea.appendText(currentText)
                } else if (char == '\n') {
                    textArea.appendText(buffer.toString() + "\n")
                    // Update labels with conversion parameters from the log output
                    updateConversionParameters(buffer.toString())
                    buffer.setLength(0)
                } else {
                    buffer.append(char)
                }

                if (atBottom) {
                    textArea.scrollTop = Double.MAX_VALUE
                }
            }
        }

        // Function to update conversion parameters from the log output
        private fun updateConversionParameters(output: String) {
            val frameRegex = Regex("frame=(\\d+)")
            val fpsRegex = Regex("fps=(\\d+)")
            val bitrateRegex = Regex("bitrate=(\\d+\\.\\d+)kbits/s")
            val speedRegex = Regex("speed=(\\d+\\.\\d+)x")

            val frameMatch = frameRegex.find(output)?.groups?.get(1)?.value
            val fpsMatch = fpsRegex.find(output)?.groups?.get(1)?.value
            val bitrateMatch = bitrateRegex.find(output)?.groups?.get(1)?.value
            val speedMatch = speedRegex.find(output)?.groups?.get(1)?.value

            // Update labels
            updateLabels(frameMatch, fpsMatch, bitrateMatch, speedMatch)
        }

        // Function to calculate the content height
        private fun calculateContentHeight(textArea: TextArea): Double {
            val lines = textArea.text.split("\n").size
            val fontHeight = textArea.font.size
            return lines * fontHeight
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            launch(MainApp::class.java)
        }
    }
}
