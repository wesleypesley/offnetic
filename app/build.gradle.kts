import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.offnetic"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.offnetic"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildFeatures { compose = true }

    packaging {
        resources {
            excludes += setOf(
                "libsignal_jni*.dylib",
                "signal_jni*.dll",
                "libsignal_jni_testing.so"
            )
        }
        jniLibs {
            useLegacyPackaging = true
            excludes += setOf("**/libsignal_jni_testing.so")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }

    signingConfigs {
        create("release") {
            val props = Properties()
            val localPropsFile = rootProject.file("local.properties")
            if (localPropsFile.exists()) props.load(localPropsFile.inputStream())
            storeFile = file(props.getProperty("KEYSTORE_FILE", "offnetic-release.jks"))
            storePassword = props.getProperty("KEYSTORE_PASSWORD", "")
            keyAlias = props.getProperty("KEY_ALIAS", "offnetic")
            keyPassword = props.getProperty("KEY_PASSWORD", "")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug { isMinifyEnabled = false; isDebuggable = true }
    }
}

tasks.matching { it.name == "mergeReleaseNativeLibs" }.configureEach {
    doLast {
        val stripExe = file("${android.sdkDirectory}/ndk/28.2.13676358/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-strip.exe")
        if (!stripExe.exists()) return@doLast
        val libDir = file("${layout.buildDirectory.get()}/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib")
        val abis = listOf("arm64-v8a")
        abis.forEach { abi ->
            val abiDir = file("$libDir/$abi")
            if (abiDir.exists()) {
                abiDir.listFiles()?.filter { it.extension == "so" }?.forEach { lib ->
                    val before = lib.length()
                    exec { commandLine(stripExe.absolutePath, "--strip-debug", lib.absolutePath) }
                    val after = lib.length()
                    println("Stripped ${lib.name}: ${before / 1024}KB → ${after / 1024}KB")
                }
            }
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.navigation.compose)
    implementation(libs.navigation.runtime.ktx)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher.android.db)

    implementation(libs.datastore.preferences)
    implementation(libs.datastore.core)
    implementation(libs.nearby.connections)
    implementation(libs.biometric)
    implementation(libs.timber)

    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.webrtc)
    implementation(libs.zxing.core)
    implementation(libs.secp256k1.jni.android)
    implementation(libs.okhttp)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.libsignal.android)

    testImplementation(libs.junit)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    testImplementation(libs.secp256k1.jni.jvm)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ui.automator)
}
