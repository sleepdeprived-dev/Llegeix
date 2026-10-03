import org.jetbrains.compose.desktop.application.dsl.TargetFormat

/**
 * The phone's versionName, read from the app's own build file so the two can
 * never disagree: the Mac app's updater compares this with the release's tag.
 * jpackage wants it plain — up to three numbers — which the phone's always is.
 */
val macVersion: String = Regex("""versionName = "([0-9.]+)"""")
    .find(rootProject.file("app/build.gradle.kts").readText())
    ?.groupValues?.get(1)
    ?: error("No versionName in app/build.gradle.kts")

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.mp.material3)
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
    systemProperty("llegeix.version", macVersion)
    systemProperty("llegeix.screenshots", layout.buildDirectory.dir("screenshots").get().asFile.path)
}

val macTools = rootProject.layout.projectDirectory.dir("tools/macos/build")

/**
 * The three native helpers the app carries, laid out where Compose bundles an
 * app's resources: PDFium (tools/macos/fetch-pdfium.sh), the reader of scanned
 * pages (build-ocr.sh) and the translator (build-bergamot.sh). Inside the app
 * they sit in Contents/app/resources, which the code finds through
 * `compose.application.resources.dir`. The translation models are not
 * bundled: they are fetched on first use, a pair at a time.
 */
val prepareMacResources = tasks.register<Sync>("prepareMacResources") {
    val helpers = listOf(
        macTools.file("pdfium/lib/libpdfium.dylib"),
        macTools.file("llegeix-ocr"),
        macTools.file("bergamot-translator/build/app/bergamot"),
    )
    from(helpers)
    into(layout.buildDirectory.dir("macResources/common"))
    doFirst {
        val missing = helpers.map { it.asFile }.filterNot { it.exists() }
        check(missing.isEmpty()) { "Build the Mac helpers first (tools/macos): missing ${missing.joinToString()}" }
    }
}

compose.desktop {
    application {
        mainClass = "com.david.llegeix.desktop.MainKt"
        // jpackage, which makes the .app and the .dmg, is not in Android
        // Studio's runtime: tools/macos/fetch-jdk.sh fetches a JDK that has it.
        javaHome = macTools.dir("jdk").asFile.takeIf { it.resolve("bin/jpackage").canExecute() }?.path
            ?: System.getProperty("java.home")
        jvmArgs += listOf(
            // Without this the title bar stays light over a dark window.
            "-Dapple.awt.application.appearance=system",
            // Skia's native library and PDFium both load through restricted
            // calls; Java otherwise warns on every launch.
            "--enable-native-access=ALL-UNNAMED",
        )
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "Llegeix"
            packageVersion = macVersion
            description = "Llegeix: llegir en català, paraula a paraula"
            copyright = "GPL-3.0"
            // What the JDK has to carry for the app, beyond java.base:
            // the window, preferences, the translator's model downloads,
            // and what Skia and the database driver reach for.
            modules("java.desktop", "java.prefs", "java.net.http", "java.instrument", "jdk.unsupported", "java.sql", "jdk.accessibility")
            appResourcesRootDir.set(layout.buildDirectory.dir("macResources"))
            macOS {
                bundleID = "com.david.llegeix"
                dockName = "Llegeix"
                appCategory = "public.app-category.education"
                minimumSystemVersion = "13.0"
                iconFile.set(project.file("icons/Llegeix.icns"))
                infoPlist {
                    // What macOS says when the sweep first reads these folders.
                    extraKeysRawXml = """
                        <key>NSDocumentsFolderUsageDescription</key>
                        <string>Llegeix hi busca els teus PDF. No es copia ni es mou res.</string>
                        <key>NSDownloadsFolderUsageDescription</key>
                        <string>Llegeix hi busca els teus PDF. No es copia ni es mou res.</string>
                        <key>NSDesktopFolderUsageDescription</key>
                        <string>Llegeix hi busca els teus PDF. No es copia ni es mou res.</string>
                    """.trimIndent()
                }
            }
        }
    }
}

tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(prepareMacResources) }

// Compose copies an app's resources as plain files, so the two helper
// programs arrive in the bundle unable to run. Put the executable bit back
// once the app is assembled; the bundle's signature does not cover file
// modes, and it still verifies afterwards.
val bundledResources = layout.buildDirectory.dir("compose/binaries/main/app/Llegeix.app/Contents/app/resources")
tasks.matching { it.name == "createDistributable" }.configureEach {
    // A local, so the action holds the folder and not the build script.
    val folder = bundledResources
    doLast {
        val resources = folder.get().asFile
        listOf("bergamot", "llegeix-ocr").forEach { name ->
            check(resources.resolve(name).setExecutable(true, false)) { "Could not make $name executable" }
        }
    }
}

// A run from Gradle uses the helpers where tools/macos built them and tells
// the app its version, which a packaged app learns from jpackage's launcher.
// Only here: baked into the app's own launch arguments, these paths would
// stand in for the bundled helpers on this Mac and hide a missing one.
tasks.withType<JavaExec>().matching { it.name == "run" }.configureEach {
    systemProperty("llegeix.bergamot", macTools.file("bergamot-translator/build/app/bergamot").asFile.path)
    systemProperty("llegeix.pdfium", macTools.file("pdfium/lib/libpdfium.dylib").asFile.path)
    systemProperty("llegeix.ocr", macTools.file("llegeix-ocr").asFile.path)
    systemProperty("llegeix.version", macVersion)
}
