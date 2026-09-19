plugins {
    id("com.android.library")
}

android {
    namespace = "me.devsaki.hentoid.webp_encoder"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        namespace = "me.devsaki.hentoid.webp_encoder"
        minSdk = 26
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.appcompat:appcompat:1.7.1")

    implementation("com.jakewharton.timber:timber:5.0.1")
}