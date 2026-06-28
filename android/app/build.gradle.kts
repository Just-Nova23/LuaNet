plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "net.novax.luanet"
    compileSdk = 35

    defaultConfig {
        applicationId = "net.novax.luanet"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        ndk.abiFilters += "arm64-v8a"
        buildConfigField("String", "CONTROL_PLANE_URL", "\"https://api.luanet.novaxhosting.com\"")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("String", "CONTROL_PLANE_URL", "\"http://10.0.2.2:8080\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets["main"].jniLibs.srcDir(layout.projectDirectory.dir("../../engine/build/android-jni"))
    packaging.resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*")
}

val nativeArtifactDir = layout.projectDirectory.dir("../../engine/build/android-jni/arm64-v8a")
val releaseNativeLibraries = listOf(
    "libfrpc.so",
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
    "libluanet_engine_5_16_1.so",
)

tasks.register("checkReleaseNativeArtifacts") {
    doLast {
        val abiDir = nativeArtifactDir.asFile
        val missing = releaseNativeLibraries.filterNot { abiDir.resolve(it).isFile }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Missing release native artifacts in ${abiDir.path}: ${missing.joinToString()}. " +
                    "Run engine/scripts/build-all.sh and engine/scripts/sync-android-artifacts.sh before release packaging.",
            )
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn("checkReleaseNativeArtifacts")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.noties.markwon:core:4.6.2")
    implementation("org.apache.commons:commons-compress:1.27.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }
