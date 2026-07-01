import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.dynamic-feature")
    id("org.jetbrains.kotlin.android")
}

val legacyEngineLibraries = listOf(
    "libluanet_engine_5_0_1.so",
    "libluanet_engine_5_1_1.so",
    "libluanet_engine_5_2_0.so",
    "libluanet_engine_5_3_0.so",
    "libluanet_engine_5_4_1.so",
    "libluanet_engine_5_5_1.so",
    "libluanet_engine_5_6_1.so",
    "libluanet_engine_5_7_0.so",
    "libluanet_engine_5_8_0.so",
    "libluanet_engine_5_9_1.so",
    "libluanet_engine_5_10_0.so",
    "libluanet_engine_5_11_0.so",
    "libluanet_engine_5_12_0.so",
    "libluanet_engine_5_13_0.so",
    "libluanet_engine_5_14_0.so",
    "libluanet_engine_5_15_2.so",
)
val generatedJniDir = layout.buildDirectory.dir("generated/engineLegacyJni")
val nativeSourceDir = layout.projectDirectory.dir("../../engine/build/android-jni/arm64-v8a")

android {
    namespace = "net.novax.luanet.engine_legacy"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets["main"].jniLibs.srcDir(generatedJniDir)

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.register<Copy>("stageLegacyEngineJni") {
    from(nativeSourceDir) {
        include(legacyEngineLibraries)
    }
    into(generatedJniDir.map { it.dir("arm64-v8a") })
}

tasks.register("checkLegacyEngineNativeArtifacts") {
    doLast {
        val dir = nativeSourceDir.asFile
        val missing = legacyEngineLibraries.filterNot { dir.resolve(it).isFile }
        if (missing.isNotEmpty()) {
            throw GradleException("Missing legacy engine native artifacts in ${dir.path}: ${missing.joinToString()}")
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn("checkLegacyEngineNativeArtifacts")
    dependsOn("stageLegacyEngineJni")
}

dependencies {
    implementation(project(":app"))
}
