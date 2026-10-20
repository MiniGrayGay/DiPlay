plugins {
    alias(libs.plugins.android.application)
}

val platformKeystoreFile = file("platform.p12")

android {
    namespace = "com.shilapi.xcertplay.legacy"
    compileSdk {
        version = release(37)
    }

    if (platformKeystoreFile.exists()) {
        signingConfigs {
            create("platform") {
                storeFile = platformKeystoreFile
                storePassword = "android"
                keyAlias = "platform"
                keyPassword = "android"
            }
        }
    }

    defaultConfig {
        applicationId = "com.shihab.diplay.legacy"
        minSdk = 19
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-legacy-test"
        multiDexEnabled = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".test"
            versionNameSuffix = "-test"
            if (platformKeystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("platform")
            }
        }
        release {
            if (platformKeystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("platform")
            }
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation("androidx.multidex:multidex:2.0.1")
}
