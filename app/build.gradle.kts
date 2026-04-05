import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val verifyGonctoolAar by tasks.registering {
    group = "verification"
    description = "Verifies that app/libs/gonctool.aar contains the expected generated gobridge APIs."

    val aarFile = layout.projectDirectory.file("libs/gonctool.aar").asFile
    inputs.file(aarFile)

    doLast {
        require(aarFile.exists()) {
            "Missing ${aarFile.path}. Rebuild gobridge AAR before packaging the app."
        }

        ZipFile(aarFile).use { aarZip ->
            val classesEntry = aarZip.getEntry("classes.jar")
                ?: error("${aarFile.path} does not contain classes.jar")

            val jarBytes = aarZip.getInputStream(classesEntry).readBytes()
            val tempJar = temporaryDir.resolve("classes.jar")
            tempJar.writeBytes(jarBytes)

            ZipFile(tempJar).use { classesZip ->
                require(classesZip.getEntry("gobridge/StatusListener.class") != null) {
                    buildString {
                        append("Stale ${aarFile.path}: gobridge/StatusListener.class is missing. ")
                        append("Android Studio is packaging an outdated gonctool.aar. ")
                        append("Rebuild app/libs/gonctool.aar from the current gobridge sources before assembling the APK.")
                    }
                }
            }
        }
    }
}

android {
    namespace = "cyou.ttdxq.gonctool.android"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "cyou.ttdxq.gonctool.android"
        minSdk = 30
        targetSdk = 36
        versionCode = 13
        versionName = "0.13"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                abiFilters += setOf("arm64-v8a")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    buildToolsVersion = "36.1.0"
    ndkVersion = "29.0.14206865"
}

tasks.named("preBuild") {
    dependsOn(verifyGonctoolAar)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.google.gson)
    implementation(libs.androidx.datastore.preferences)
    implementation(files("libs/gonctool.aar"))
}
