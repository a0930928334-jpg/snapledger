import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Persistence module: SQLDelight schema, repositories, encrypted backups (from M2).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
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
            implementation(projects.core)
        }
    }
}
