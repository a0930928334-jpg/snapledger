import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// Windows (desktop JVM) entry point and packaging.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(projects.ui)
    implementation(projects.data)
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)

    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.junit4)
    testImplementation(kotlin("test"))
}

tasks.withType<Test>().configureEach {
    systemProperty("snapledger.samplesDir", rootProject.file("samples").absolutePath)
    systemProperty(
        "snapledger.screenshotDir",
        layout.buildDirectory
            .dir("screenshots")
            .get()
            .asFile.absolutePath,
    )
}

compose.desktop {
    application {
        mainClass = "dev.snapledger.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "SnapLedger"
            packageVersion = "0.1.0"
        }
    }
}
