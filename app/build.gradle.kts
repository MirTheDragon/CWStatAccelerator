plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.cwstataccelerator"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.cwstataccelerator"
        minSdk = 19
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
    }
}

// Post-build APK rename task
tasks.register("renameApk") {
    doLast {
        val buildDir = project.layout.buildDirectory.dir("outputs/apk").get().asFile
        buildDir.walkTopDown().forEach { file ->
            if (file.isFile && file.extension == "apk") {
                val buildType = if (file.parentFile.name.contains("debug", ignoreCase = true)) "debug" else "release"
                val versionCode = android.defaultConfig.versionCode
                val versionName = android.defaultConfig.versionName
                val newFileName = "CWStatAccelerator_${buildType}_v${versionCode}_${versionName}.apk"

                // Rename the file
                val renamedFile = File(file.parentFile, newFileName)
                file.renameTo(renamedFile)
                println("Renamed ${file.name} to ${renamedFile.name}")
            }
        }
    }
}

// Hook the rename task after the build
tasks.named("build") {
    finalizedBy("renameApk")
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
