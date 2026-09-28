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

compose.desktop {
    application {
        mainClass = "com.david.llegeix.desktop.MainKt"
        jvmArgs += listOf(
            // Without this the title bar stays light over a dark window.
            "-Dapple.awt.application.appearance=system",
            // Skia's native library; Java otherwise warns on every launch.
            "--enable-native-access=ALL-UNNAMED",
        )
        // While developing, the translator from tools/macos/build-bergamot.sh.
        // A packaged app carries its own copy.
        jvmArgs += "-Dllegeix.bergamot=" + rootProject.file(
            "tools/macos/build/bergamot-translator/build/app/bergamot",
        ).path
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
