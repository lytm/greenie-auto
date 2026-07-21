plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget()
    sourceSets {
        androidMain.dependencies {
            implementation(project(":shared"))
            implementation(compose.ui)
            implementation(compose.foundation)
            implementation(compose.material3)
           
            implementation(compose.components.resources)
            implementation("androidx.activity:activity-compose:1.10.1")
        }
    }
}

android {
    namespace = "com.greenie.auto.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.greenie.auto.android"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
