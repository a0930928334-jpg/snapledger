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
            // Exe = installer wizard, Msi = Windows Installer package. `createDistributable` gives a portable folder.
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "SnapLedger"
            packageVersion = "0.4.0"
            description = "Local-first personal finance ledger"
            vendor = "SnapLedger"
            // Bundled runtime: java.sql for the SQLite JDBC driver (found with :desktopApp:suggestRuntimeModules).
            modules("java.instrument", "java.sql", "jdk.unsupported")

            windows {
                iconFile.set(project.file("icons/snapledger.ico"))
                menu = true
                menuGroup = "SnapLedger"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                // Must never change: lets a newer installer upgrade an older installation in place.
                upgradeUuid = "91b7c89e-8e35-4ef1-bb88-c5c215f9e8e6"
            }
        }
    }
}
