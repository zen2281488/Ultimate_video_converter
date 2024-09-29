plugins {
    kotlin("jvm") version "2.0.0"
    id("org.openjfx.javafxplugin") version "0.0.13"
    id("edu.sc.seis.launch4j") version "3.0.6"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.openjfx:javafx-controls:17")
    implementation("org.openjfx:javafx-fxml:17")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-javafx:1.7.3")
    implementation("net.bramp.ffmpeg:ffmpeg:0.8.0")
}

javafx {
    version = "17"
    modules = listOf("javafx.controls", "javafx.fxml")
}

application {
    mainClass.set("com.example.MainApp")
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to application.mainClass.get(),
            "Class-Path" to configurations.runtimeClasspath.get().joinToString(" ") { it.toURI().toString() }
        )
    }
    from(sourceSets.main.get().resources)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

tasks.withType<edu.sc.seis.launch4j.tasks.DefaultLaunch4jTask> {
    outfile.set("build/launch4j/Converter.exe")
    mainClassName.set("com.example.MainApp")
    productName.set("My App")
}

tasks.test {
    useJUnitPlatform()
}