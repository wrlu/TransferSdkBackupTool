plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "net.wrlu.forensictool"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.vivo.easyshare"
        minSdk = 29
        targetSdk = 36
        versionCode = 10000001
        versionName = "1.1.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../platform.vivo.keystore")
            storePassword = "vivostore"
            keyAlias = "vivoapp"
            keyPassword = "vivokey"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
}