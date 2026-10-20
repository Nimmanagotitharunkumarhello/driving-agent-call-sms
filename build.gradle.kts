// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // Android Gradle Plugin (AGP) handles building Android apps
    id("com.android.application") version "8.5.2" apply false

    // Kotlin Android plugin compiles Kotlin code for Android
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false

    // Compose compiler plugin for Kotlin 2.0+
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false

    // KSP (Kotlin Symbol Processing) generates code for Room database at compile time
    id("com.google.devtools.ksp") version "2.0.20-1.0.25" apply false
}
