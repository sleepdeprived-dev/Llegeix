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
    implementation(libs.compose.mp.material.icons.core)
    implementation(libs.compose.mp.navigation.compose)
    // Dispatchers.Main on the Mac is the Swing event thread; the ViewModels run on it.
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.json)

    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(libs.junit)
    testImplementation(libs.compose.mp.lifecycle.runtime.compose)
    testImplementation(libs.kotlinx.coroutines.test)
}

// The Mac's screens under test use the development translator from tools/macos
// and the models already fetched there, in a scratch data folder.
tasks.withType<Test>().configureEach {
    val tools = rootProject.layout.projectDirectory.dir("tools/macos/build").asFile
    systemProperty("llegeix.bergamot", tools.resolve("bergamot-translator/build/app/bergamot").path)
    systemProperty("llegeix.pdfium", tools.resolve("pdfium/lib/libpdfium.dylib").path)
    systemProperty("llegeix.ocr", tools.resolve("llegeix-ocr").path)
    systemProperty("llegeix.testModels", tools.resolve("models").path)
    systemProperty("llegeix.screenshots", layout.buildDirectory.dir("screenshots").get().asFile.path)
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
        // And PDFium from tools/macos/fetch-pdfium.sh, and the reader of scanned
        // pages from tools/macos/build-ocr.sh, likewise.
        jvmArgs += "-Dllegeix.pdfium=" + rootProject.file("tools/macos/build/pdfium/lib/libpdfium.dylib").path
        jvmArgs += "-Dllegeix.ocr=" + rootProject.file("tools/macos/build/llegeix-ocr").path
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "Llegeix"
            // jpackage wants a plain x.y.z; kept in step with the Android versionName.
            packageVersion = "4.4.6"
            macOS {
                bundleID = "com.david.llegeix"
            }
        }
    }
}
