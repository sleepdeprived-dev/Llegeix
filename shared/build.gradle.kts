import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

/**
 * The code the phone and the Mac have in common.
 *
 * Both targets run on a JVM, so common code may use the JDK (java.util.Locale,
 * java.text.Normalizer, …). What it may not use is anything from android.*.
 */
kotlin {
    android {
        namespace = "com.david.llegeix.shared"
        compileSdk = 37
        minSdk = 31
        // The app compiles for Java 11, and it inlines from here.
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.mp.runtime)
            implementation(libs.compose.mp.foundation)
            implementation(libs.compose.mp.animation)
            implementation(libs.compose.mp.ui)
            implementation(libs.compose.mp.material3)
            implementation(libs.compose.mp.material.icons.core)
            // Android has org.json built in, and a second copy in the APK trips
            // lint's DuplicatePlatformClasses. Only the Mac app bundles it.
            compileOnly(libs.json)
        }
        val desktopMain by getting {
            dependencies {
                implementation(libs.json)
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(libs.junit)
                implementation(libs.json)
            }
        }
    }
}
