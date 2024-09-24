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

    override fun start(primaryStage: Stage) {
        primaryStage.title = "Video Converter"

        val ffmpegHandler = FFmpegHandler()

        // Field for displaying selected files
        val selectedFilesLabel = Label("Selected Files:")
        val fileListView = ListView<String>()

        // Button to select files
        val chooseFileButton = Button("Select Video Files")
        chooseFileButton.setOnAction {
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

        // Field to select conversion format
        val formatLabel = Label("Select Format for Conversion:")
        val formatChoiceBox = ChoiceBox<String>().apply {
            items.addAll("mp4", "avi", "mov", "mkv", "flv")
            value = "mp4" // default value
        }

        // Field for displaying logs
        val logArea = TextArea().apply {
            isEditable = false // Make the console read-only
            setStyle("-fx-font-family: 'monospace';") // Set monospaced font
        }

        // Redirect standard output to the log with UTF-8 support
        val outputStream = ConsoleOutputStream(logArea)
        System.setOut(PrintStream(outputStream, true, Charsets.UTF_8.name()))
        System.setErr(PrintStream(outputStream, true, Charsets.UTF_8.name()))

        // Progress bar for video conversion
        val progressBar = ProgressBar(0.0).apply {
            isVisible = false // Initially hidden
        }

        // Buttons for conversion and cancellation
        val convertButton = Button("Convert")
        val cancelButton = Button("Cancel").apply {
            isDisable = true // Disable cancel button initially
        }

        convertButton.setOnAction {
            val selectedFormat = formatChoiceBox.value
            val selectedFiles = fileListView.items

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
                val job = coroutineScope.launch(Dispatchers.IO) {
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
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            showAlert("Error", "Failed to convert $filePath: ${e.message}")
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
        val vbox = VBox(10.0, selectedFilesLabel, fileListView, chooseFileButton, formatLabel, formatChoiceBox, buttonLayout, logArea, progressBar).apply {
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

    // Class to redirect output to TextArea with UTF-8 support
    class ConsoleOutputStream(private val textArea: TextArea) : OutputStream() {
        private val buffer = StringBuilder()

        override fun write(b: Int) {
            if (b == '\n'.toInt()) {
                Platform.runLater { // Update UI on the JavaFX Application Thread
                    textArea.appendText(buffer.toString() + "\n")
                }
                buffer.setLength(0)
            } else {
                buffer.append(b.toChar())
            }
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            launch(MainApp::class.java)
        }
    }
}