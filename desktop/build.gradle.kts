import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.mp.material3)
    implementation(libs.json)
}

// Figtree is the phone's font file, used as it is rather than copied. It moves
// into the shared module's resources along with the strings.
sourceSets.main {
    resources.srcDir("../app/src/main/res/font")
}

compose.desktop {
    application {
        mainClass = "com.david.llegeix.desktop.MainKt"
        jvmArgs += listOf(
            // Without this the title bar stays light over a dark window.
            "-Dapple.awt.application.appearance=system",
            // Skia's native library; Java otherwise warns on every launch.
            "--enable-native-access=ALL-UNNAMED",
        )
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "Llegeix"
            // jpackage wants a plain x.y.z; kept in step with the Android versionName.
            packageVersion = "4.4.5"
            macOS {
                bundleID = "com.david.llegeix"
            }
        }
    }
}
