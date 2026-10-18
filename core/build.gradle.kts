import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin domain module: no UI, database or platform APIs (CLAUDE.md rule 5).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kover)
}

kotlin {
    jvmToolchain(21)
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvmTarget.get()))
        }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
