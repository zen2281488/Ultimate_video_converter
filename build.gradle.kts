plugins {
    kotlin("jvm") version "2.0.0"
    id("org.openjfx.javafxplugin") version "0.0.13"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-javafx:1.7.3")
    implementation("org.openjfx:javafx-controls:17")
    implementation("org.openjfx:javafx-fxml:17")
}

javafx {
    version = "17"
    modules = listOf("javafx.controls", "javafx.fxml")
}

application {
    // Указываем главный класс приложения
    mainClass.set("com.example.MainApp")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}