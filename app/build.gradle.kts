plugins {
    id("com.android.application")
}

android {
    namespace = "biz.shikuro.codmroottutorial"
    compileSdk = 35

    defaultConfig {
        applicationId = "biz.shikuro.codmroottutorial"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

