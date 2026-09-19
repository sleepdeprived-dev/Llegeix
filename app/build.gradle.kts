import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Release signing credentials come from gitignored local.properties, falling
// back to environment variables so a CI runner can supply them instead. When
// neither is present the release build stays unsigned rather than failing, so
// a fresh clone can still run `assembleRelease`.
val signingProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

fun signingValue(key: String, env: String): String? =
    signingProps.getProperty(key) ?: System.getenv(env)

val releaseStoreFile = signingValue("llegeix.storeFile", "LLEGEIX_STORE_FILE")?.let(::File)
val hasReleaseSigning = releaseStoreFile?.exists() == true

android {
    namespace = "com.david.llegeix"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.david.llegeix"
        minSdk = 31
        targetSdk = 37
        versionCode = 40
        versionName = "4.1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = signingValue("llegeix.storePassword", "LLEGEIX_STORE_PASSWORD")
                keyAlias = signingValue("llegeix.keyAlias", "LLEGEIX_KEY_ALIAS")
                keyPassword = signingValue("llegeix.keyPassword", "LLEGEIX_KEY_PASSWORD")
            }
        }
    }

    /**
     * One APK per ABI instead of one carrying all four.
     *
     * Pdfium ships native libraries for every architecture and they dominate the
     * download; a device only ever runs one of them. The universal APK is kept
     * as well, because a sideloaded release needs an option that installs
     * anywhere without the reader having to know their own architecture.
     */
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    bundle {
        // Play would otherwise ship only the device's own locale, and the app
        // switches language itself (createConfigurationContext in AppLocale),
        // so a Catalan string has to be present on an English phone.
        language {
            enableSplit = false
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }

    // MigrationTestHelper loads the exported schemas from the test APK.
    sourceSets {
        getByName("androidTest") {
            assets.directories.add("$projectDir/schemas")
        }
    }
}

// Emit the schema JSON so feature-3 migrations have a baseline to diff against.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Room's schema-bundle serializers are compiled against kotlinx-serialization
// 1.8.x, but a transitive BOM pins -core to 1.7.3 while -json resolves to 1.8.1.
// The split classpath throws AbstractMethodError inside MigrationTestHelper, so
// align them. Test-only: nothing in the app itself uses kotlinx-serialization.
configurations.matching { it.name.contains("AndroidTest") }.configureEach {
    // The -jvm variant is what actually lands on the classpath, so both the
    // alias and the platform artifact have to be pinned.
    resolutionStrategy.force(
        "org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1",
        "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.8.1",
    )
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.pdfiumandroid)
    implementation(libs.mlkit.translate)
    // The bundled Latin recogniser rather than the Play-Services one: reading a
    // scanned page has to work the first time it is asked for, offline, on a
    // phone that has never seen the app before.
    implementation(libs.mlkit.text.recognition)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
