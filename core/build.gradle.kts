import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin domain module: no UI, database or platform APIs (CLAUDE.md rule 5).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
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
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// Tests read the fictional examples straight from samples/ so docs, schema and tests stay in sync.
tasks.withType<Test>().configureEach {
    systemProperty("snapledger.samplesDir", rootProject.file("samples").absolutePath)
}
