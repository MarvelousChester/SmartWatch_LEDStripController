plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.myled"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.myled"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    useLibrary("wear-sdk")
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.navigation)
    implementation(libs.core.splashscreen)
    implementation(libs.coroutines.android)
    implementation(libs.datastore.preferences)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.protolayout)
    implementation(libs.protolayout.expression)
    implementation(libs.protolayout.material3)
    implementation(libs.tiles)
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.wear.tooling.preview)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.tiles.renderer)
    debugImplementation(libs.ui.test.manifest)
    debugImplementation(libs.ui.tooling)
}
