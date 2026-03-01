plugins {
    kotlin("jvm") version "2.0.20"
    id("org.openjfx.javafxplugin") version "0.1.0"
    application
}

group = "com.rdr.roast"
version = "0.1.0"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

javafx {
    version = "23"
    modules("javafx.controls", "javafx.fxml", "javafx.graphics")
}

dependencies {
    implementation("io.github.mkpaz:atlantafx-base:2.1.0")
    implementation("org.kordamp.ikonli:ikonli-javafx:12.3.1")
    implementation("org.kordamp.ikonli:ikonli-fontawesome5-pack:12.3.1")
    implementation("org.controlsfx:controlsfx:11.2.1")
    implementation("com.ghgande:j2mod:3.2.1")
    implementation("com.fazecast:jSerialComm:2.10.4")
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("ch.qos.logback:logback-classic:1.5.16")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-javafx:1.9.0")

    // Chart — JFreeChart + JavaFX bridge
    implementation("org.jfree:jfreechart:1.5.4")
    implementation("org.jfree:org.jfree.fxgraphics2d:2.1.5")
    implementation("org.jfree:org.jfree.chart.fx:2.0.1")
    implementation("org.apache.commons:commons-math3:3.6.1")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("io.mockk:mockk:1.13.14")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("com.rdr.roast.AppKt")
}
