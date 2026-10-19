import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Persistence module: SQLDelight schema, repositories, encrypted backups.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvmToolchain(21)
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvmTarget.get()))
        }
    }
    android {
        namespace = "dev.snapledger.data"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvmTarget.get()))
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.core)
            implementation(libs.kotlinx.serialization.json)
        }
        // Code shared by the desktop JVM and Android (both have javax.crypto), e.g. backup encryption.
        val jvmShared = create("jvmShared") { dependsOn(commonMain.get()) }
        jvmMain.get().dependsOn(jvmShared)
        androidMain.get().dependsOn(jvmShared)
        jvmMain.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

sqldelight {
    databases {
        create("SnapLedgerDatabase") {
            packageName.set("dev.snapledger.data.db")
        }
    }
}

tasks.withType<Test>().configureEach {
    systemProperty("snapledger.samplesDir", rootProject.file("samples").absolutePath)
    systemProperty(
        "snapledger.buildDir",
        layout.buildDirectory
            .get()
            .asFile.absolutePath,
    )
}
