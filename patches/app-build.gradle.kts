plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.tubemusic.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tubemusic.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.5"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui:1.11.4")
    implementation("androidx.compose.ui:ui-tooling-preview:1.11.4")
    implementation("androidx.compose.foundation:foundation:1.11.4")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    debugImplementation("androidx.compose.ui:ui-tooling:1.11.4")

    implementation("androidx.media3:media3-exoplayer:1.11.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.0")
    implementation("androidx.media3:media3-session:1.11.0")

    implementation("io.coil-kt.coil3:coil-compose:3.4.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.4.0")
}

val applyRuntimeSourceFixes by tasks.registering {
    doLast {
        val manifest = file("src/main/AndroidManifest.xml")
        if (manifest.exists()) {
            var text = manifest.readText()
            if (!text.contains("usesCleartextTraffic")) {
                text = text.replace(
                    "android:allowBackup=\"true\"",
                    "android:allowBackup=\"true\"\n        android:usesCleartextTraffic=\"true\""
                )
                manifest.writeText(text)
            }
        }

        val radioRepo = file("src/main/java/com/tubemusic/app/data/RadioBrowserRepository.kt")
        if (radioRepo.exists()) {
            var text = radioRepo.readText()
            text = text.replace(
                "if (!stream.startsWith(\"http://\") && !stream.startsWith(\"https://\")) continue",
                "if (!stream.startsWith(\"https://\")) continue"
            )
            text = text.replace("TubeMusic/0.4", "TubeMusic/0.5")
            radioRepo.writeText(text)
        }

        val service = file("src/main/java/com/tubemusic/app/playback/PlaybackService.kt")
        if (service.exists()) {
            var text = service.readText()
            text = text.replace(
                "val exo = ExoPlayer.Builder(this)\n            .setAudioAttributes(AudioAttributes.DEFAULT, true)",
                "val audioAttributes = AudioAttributes.Builder()\n            .setUsage(C.USAGE_MEDIA)\n            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)\n            .build()\n        val exo = ExoPlayer.Builder(this)\n            .setAudioAttributes(audioAttributes, true)"
            )
            service.writeText(text)
        }
    }
}

tasks.named("preBuild") {
    dependsOn(applyRuntimeSourceFixes)
}
