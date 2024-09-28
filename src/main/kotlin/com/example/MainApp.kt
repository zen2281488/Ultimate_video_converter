package com.example

import javafx.application.Application
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.FileChooser
import javafx.stage.Stage
import kotlinx.coroutines.*
import java.io.File
import java.io.OutputStream
import java.io.PrintStream

class MainApp : Application() {

    private val conversionJobs = mutableListOf<Job>()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun start(primaryStage: Stage) {
        primaryStage.title = "Video Converter"

        val ffmpegHandler = FFmpegHandler()

        // Поле для отображения выбранных файлов
        val fileListView = VBox(10.0)

        // Кнопка для выбора файлов
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
                    fileListView.children.clear()
                    selectedFiles.forEachIndexed { index, file ->
                        addFileToUI(fileListView, index + 1, file)
                    }
                }
            }
        }

        // Поле для выбора формата конвертации
        val formatLabel = Label("Select Format for Conversion:")
        val formatChoiceBox = ChoiceBox<String>().apply {
            items.addAll("mp4", "avi", "mov", "mkv", "flv")
            value = "mp4"
        }

        val sizeLabel = Label("Select video Size for Conversion (3:2 display resolutions):")
        val sizeChoiceBox = ChoiceBox<String>().apply {
            items.addAll("360p", "480p", "720p", "1080p", "2K", "4K")
            value = "480p"
        }

        // Поле для отображения логов
        val logArea = TextArea().apply {
            isEditable = false
            style = "-fx-font-family: 'monospace';"
            prefWidth = 500.0
            prefHeight = 500.0
        }

        // Перенаправляем вывод в консоль
        val outputStream = ConsoleOutputStream(logArea)
        System.setOut(PrintStream(outputStream, true, Charsets.UTF_8.name()))
        System.setErr(PrintStream(outputStream, true, Charsets.UTF_8.name()))

        // Кнопки для конвертации и отмены
        val convertButton = Button("Convert")
        val cancelButton = Button("Cancel").apply {
            isDisable = true
            isVisible = false
        }

        convertButton.setOnAction {
            val selectedFormat = formatChoiceBox.value
            val selectedSize = sizeChoiceBox.value
            val selectedFiles = fileListView.children.filterIsInstance<GridPane>()

            if (selectedFiles.isEmpty()) {
                showAlert("Error", "Please select at least one file")
                return@setOnAction
            }

            convertButton.isDisable = true
            cancelButton.isDisable = false
            conversionJobs.clear()

            selectedFiles.forEach { gridPane ->
                val fileLabel = gridPane.children[1] as Label
                val progressBar = gridPane.children[2] as ProgressBar
                val filePath = fileLabel.text

                val job = coroutineScope.launch(Dispatchers.IO) {
                    val videoCompressor = VideoCompressor(ffmpegHandler, selectedFormat, selectedSize)
                    try {
                        videoCompressor.compressVideo(filePath, progressBar)
                        Platform.runLater {
                            fileLabel.text = "Completed: $filePath"
                            convertButton.isDisable = false
                        }
                    } catch (e: CancellationException) {
                        Platform.runLater {
                            fileLabel.text = "Cancelled: $filePath"
                        }
                    }
                }
                conversionJobs.add(job)
            }

            println("Conversion started...")
        }

        cancelButton.setOnAction {
            conversionJobs.forEach { job -> job.cancel() }
            conversionJobs.clear()
            cancelButton.isDisable = true
            println("All conversions cancelled.")
        }

        // Размещение кнопок
        val buttonLayout = HBox(10.0, convertButton, cancelButton)

        // Основная компоновка
        val vbox = VBox(10.0, chooseFileButton, formatLabel, formatChoiceBox, sizeLabel, sizeChoiceBox, buttonLayout, logArea, fileListView).apply {
            padding = Insets(20.0)
        }

        val scene = Scene(vbox, 600.0, 400.0)
        primaryStage.scene = scene
        primaryStage.show()

        println("Application started. Ready for use.")
    }

    private fun addFileToUI(fileListView: VBox, index: Int, file: File) {
        val gridPane = GridPane().apply {
            hgap = 10.0
            add(Label("$index."), 0, 0) // Нумерация файлов
            add(Label(file.absolutePath), 1, 0) // Путь к файлу
            add(ProgressBar(0.0).apply { prefWidth = 300.0 }, 2, 0) // Прогресс-бар
        }
        fileListView.children.add(gridPane)
    }
    class ConsoleOutputStream(private val textArea: TextArea) : OutputStream() {
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
                    buffer.setLength(0)
                } else {
                    buffer.append(char)
                }

                if (atBottom) {
                    textArea.scrollTop = Double.MAX_VALUE
                }
            }
        }

        private fun calculateContentHeight(textArea: TextArea): Double {
            val lines = textArea.text.split("\n").size
            val fontHeight = textArea.font.size
            return lines * fontHeight
        }
    }
    private fun showAlert(title: String, content: String) {
        val alert = Alert(Alert.AlertType.INFORMATION)
        alert.title = title
        alert.contentText = content
        alert.showAndWait()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            launch(MainApp::class.java)
        }
    }
}
