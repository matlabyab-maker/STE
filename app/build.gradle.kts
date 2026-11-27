plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ste.editor"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ste.editor"
        minSdk = 23
        targetSdk = 35
        versionCode = 3
        versionName = "1.2"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.emoji2:emoji2:1.6.0")
    implementation("androidx.emoji2:emoji2-views:1.6.0")
    implementation("androidx.emoji2:emoji2-bundled:1.6.0")
}
