import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
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
        // The strings travel as Compose resources, which Android reads from
        // the library's own packaged resources.
        androidResources {
            enable = true
        }
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
            // The app's strings. `api` because the phone and the Mac both name
            // them (Res.string.…) and read them (stringResource) directly.
            api(libs.compose.mp.resources)
            // The database. `api` because the app reaches the DAOs and
            // entities directly, as it did when they lived there.
            api(libs.androidx.room.runtime)
            implementation(libs.compose.mp.lifecycle.runtime.compose)
            api(libs.compose.mp.lifecycle.viewmodel.compose)
            // Android has org.json built in, and a second copy in the APK trips
            // lint's DuplicatePlatformClasses. Only the Mac app bundles it.
            compileOnly(libs.json)
        }
        androidMain.dependencies {
            implementation(libs.mlkit.translate)
            // PDFium, the engine both platforms render with; the Mac loads it itself.
            implementation(libs.pdfiumandroid)
            // The bundled Latin recogniser rather than the Play-Services one: reading a
            // scanned page has to work the first time it is asked for, offline, on a
            // phone that has never seen the app before.
            implementation(libs.mlkit.text.recognition)
            implementation(libs.androidx.activity.compose)
        }
        val desktopMain by getting {
            dependencies {
                implementation(libs.json)
                implementation(libs.androidx.sqlite.bundled)
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(libs.junit)
                implementation(libs.json)
                // Resolving a string asks the system for its theme, which
                // needs Skia's native runtime.
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

// Room's compiler, once per platform: each gets its own generated DAOs.
dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspDesktop", libs.androidx.room.compiler)
}

// The exported schemas are the baseline every migration is checked against,
// so they stay where they have always been.
room {
    schemaDirectory("$projectDir/../app/schemas")
}

/**
 * The app's strings, in Catalan, as Compose resources: the same strings.xml
 * format as Android's res/values, read with the same stringResource() and
 * pluralStringResource(), named Res.string.… where Android said R.string.….
 */
compose.resources {
    publicResClass = true
    packageOfResClass = "com.david.llegeix.resources"
    generateResClass = always
}

// The Mac's translator, PDFium and page reader in tests: the development builds from
// tools/macos, and the models already fetched there, so the suite neither
// downloads nor touches the real Application Support. The tests skip when
// those builds are absent.
tasks.withType<Test>().configureEach {
    val tools = rootProject.layout.projectDirectory.dir("tools/macos/build").asFile
    systemProperty("llegeix.bergamot", tools.resolve("bergamot-translator/build/app/bergamot").path)
    systemProperty("llegeix.pdfium", tools.resolve("pdfium/lib/libpdfium.dylib").path)
    systemProperty("llegeix.ocr", tools.resolve("llegeix-ocr").path)
    // PDFium is called through the JDK's foreign function interface.
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    systemProperty("llegeix.testModels", tools.resolve("models").path)
}
