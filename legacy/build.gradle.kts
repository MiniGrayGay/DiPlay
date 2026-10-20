plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.shilapi.xcertplay.legacy"
    compileSdk {
        version = release(37)
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
        }
        release {
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
