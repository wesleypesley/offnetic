import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
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
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildFeatures { compose = true }

    composeOptions { kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get() }

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

    kotlinOptions { jvmTarget = "11" }

    signingConfigs {
        create("release") {
            storeFile = file("offnetic-release.jks")
            storePassword = "offnetic123"
            keyAlias = "offnetic"
            keyPassword = "offnetic123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug { isMinifyEnabled = false; isDebuggable = true }
    }
}

// -- libsignal Kotlin 2.1 metadata stripping task --

val strippedLibsignalDir = layout.buildDirectory.dir("intermediates/stripped-libsignal")

val stripLibsignalMetadata by tasks.registering {
    group = "offnetic"
    description = "Strips Kotlin 2.1 metadata from libsignal for KSP 1.9 / kotlinc 1.9 compat"

    val clientConfig = configurations.detachedConfiguration(
        dependencies.create("org.signal:libsignal-client:${libs.versions.libsignal.get()}")
    )
    val androidConfig = configurations.detachedConfiguration(
        dependencies.create("org.signal:libsignal-android:${libs.versions.libsignal.get()}")
    )

    inputs.files(clientConfig)
    inputs.files(androidConfig)
    outputs.dir(strippedLibsignalDir)

    doLast {
        val metadataDesc = "Lkotlin/Metadata;"
        val outDir = strippedLibsignalDir.get().asFile
        outDir.mkdirs()

        val jars = (clientConfig.files + androidConfig.files)
            .filter { it.name.endsWith(".jar") && !it.name.contains("sources") }

        jars.forEach { jar ->
            val outFile = File(outDir, jar.name.replace(".jar", "-stripped.jar"))
            ZipInputStream(jar.inputStream()).use { zis ->
                ZipOutputStream(outFile.outputStream()).use { zos ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name.endsWith(".kotlin_module")) {
                            entry = zis.nextEntry
                            continue
                        }
                        if (entry.name.endsWith(".class") && !entry.isDirectory) {
                            val bytes = zis.readBytes()
                            try {
                                val reader = ClassReader(bytes)
                                val writer = ClassWriter(0)
                                reader.accept(object : ClassVisitor(Opcodes.ASM9, writer) {
                                    override fun visitAnnotation(descriptor: String?, visible: Boolean): org.objectweb.asm.AnnotationVisitor? {
                                        if (descriptor == metadataDesc) return null
                                        return super.visitAnnotation(descriptor, visible)
                                    }
                                }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
                                zos.putNextEntry(ZipEntry(entry.name))
                                zos.write(writer.toByteArray())
                                zos.closeEntry()
                            } catch (_: Exception) {
                                zos.putNextEntry(ZipEntry(entry.name))
                                zos.write(bytes)
                                zos.closeEntry()
                            }
                        } else {
                            zos.putNextEntry(ZipEntry(entry.name))
                            if (!entry.isDirectory) zis.copyTo(zos)
                            zos.closeEntry()
                        }
                        entry = zis.nextEntry
                    }
                }
            }
        }
    }
}

// -- libsignal-client D8 pre-dexing task --

val d8LibsignalOutput = layout.buildDirectory.dir("intermediates/d8-libsignal")

val preDexLibsignal by tasks.registering {
    group = "offnetic"
    description = "Pre-dexes libsignal-client with --global-synthetics-consumer"

    val clientConfig = configurations.detachedConfiguration(
        dependencies.create("org.signal:libsignal-client:${libs.versions.libsignal.get()}")
    )
    val desugarConfig = configurations.detachedConfiguration(
        dependencies.create("com.android.tools:desugar_jdk_libs:${libs.versions.desugar.jdk.get()}")
    )

    inputs.files(clientConfig)
    inputs.files(desugarConfig)
    outputs.dir(d8LibsignalOutput)

    doLast {
        val libsignalJar = clientConfig.filter { it.name == "libsignal-client-${libs.versions.libsignal.get()}.jar" }.single()
        val desugarJar = desugarConfig.filter { it.name.startsWith("desugar_jdk_libs-") && it.name.endsWith(".jar") && !it.name.contains("sources") }.first()
        val sdkDir = android.sdkDirectory.absolutePath
        val buildToolsDir = "$sdkDir/build-tools/35.0.0"
        val androidJar = "$sdkDir/platforms/android-${android.compileSdk}/android.jar"
        val d8Exec = if (File("$buildToolsDir/d8.bat").exists()) "$buildToolsDir/d8.bat" else "$buildToolsDir/d8"

        val outputDir = d8LibsignalOutput.get().asFile
        outputDir.mkdirs()

        exec {
            commandLine(d8Exec, "--lib", androidJar, "--min-api", "28",
                "--output", outputDir.absolutePath,
                libsignalJar.absolutePath)
        }
    }
}

// -- Wire pre-dexed DEX into mergeProjectDex --

val copyLibsignalDexDebug by tasks.registering {
    group = "offnetic"
    description = "Copies pre-dexed libsignal DEX into merge dir"
    dependsOn(preDexLibsignal)

    val mergeDir = project.layout.buildDirectory.dir("intermediates/dex/debug/mergeProjectDexDebug/0")

    inputs.files(d8LibsignalOutput)

    doLast {
        val d8Out = d8LibsignalOutput.get().asFile
        val merge = mergeDir.get().asFile
        merge.mkdirs()
        d8Out.listFiles()?.filter { it.extension == "dex" }?.forEach { dex ->
            File(merge, "libsignal_${dex.name}").outputStream().use { out ->
                dex.inputStream().use { it.copyTo(out) }
            }
        }
    }
}

val copyLibsignalDexRelease by tasks.registering {
    group = "offnetic"
    description = "Copies pre-dexed libsignal DEX into release merge dir"
    dependsOn(preDexLibsignal)

    val mergeDir = project.layout.buildDirectory.dir("intermediates/dex/release/mergeDexRelease")

    inputs.files(d8LibsignalOutput)

    doLast {
        val d8Out = d8LibsignalOutput.get().asFile
        val merge = mergeDir.get().asFile
        merge.mkdirs()
        d8Out.listFiles()?.filter { it.extension == "dex" }?.forEach { dex ->
            File(merge, "libsignal_${dex.name}").outputStream().use { out ->
                dex.inputStream().use { it.copyTo(out) }
            }
        }
    }
}

afterEvaluate {
    tasks.named("mergeProjectDexDebug") {
        dependsOn(copyLibsignalDexDebug)
    }
    tasks.named("mergeDexRelease") {
        dependsOn(copyLibsignalDexRelease)
    }
}

// -- Dependencies --

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
    implementation("androidx.multidex:multidex:2.0.1")

    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.webrtc)
    implementation(libs.zxing.core)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    compileOnly(fileTree(strippedLibsignalDir) {
        include("libsignal-client-*-stripped.jar")
        builtBy(stripLibsignalMetadata)
    })

    runtimeOnly(libs.libsignal.android) {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains.kotlinx")
    }

    testImplementation(libs.junit)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ui.automator)
}

tasks.register("stripLibsignalDebug") {
    doLast {
        val stripExe = "C:/Users/Admin/AppData/Local/Android/Sdk/ndk/28.2.13676358/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-strip.exe"
        if (!file(stripExe).exists()) {
            println("llvm-strip not found — skipping")
            return@doLast
        }
        val libDir = file("build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib")
        listOf("arm64-v8a").forEach { abi ->
            val lib = file("$libDir/$abi/libsignal_jni.so")
            if (lib.exists()) {
                val before = lib.length()
                try {
                    exec {
                        commandLine(stripExe, "--strip-debug", lib.absolutePath)
                    }
                    val after = lib.length()
                    println("Stripped libsignal_jni.so: ${before / (1024*1024)}MB → ${after / (1024*1024)}MB")
                } catch (e: Exception) {
                    println("Strip failed: ${e.message}")
                }
            }
        }
    }
}

tasks.matching { it.name == "mergeReleaseNativeLibs" }.configureEach {
    finalizedBy("stripLibsignalDebug")
}

