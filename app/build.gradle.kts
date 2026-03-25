plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.therapy_app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.therapy_app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // ✅ Kotlin 2.2.0 uses the OLD DSL
    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        // Leave empty — AGP provides the Compose Compiler automatically.
    }
}

dependencies {

    // Core AndroidX
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // Activity Compose
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.04.01"))

    // Jetpack Compose dependencies
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // ConstraintLayout
    implementation("androidx.constraintlayout:constraintlayout-compose:1.0.1")

    // AppCompat
    implementation("androidx.appcompat:appcompat:1.6.1")

    // Material Components
    implementation("com.google.android.material:material:1.11.0")

    // Activity (non-compose)
    implementation("androidx.activity:activity:1.8.2")

    // Firebase (via BOM)
    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-analytics")

    // Testing
    testImplementation("junit:junit:4.13.2")

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    androidTestImplementation(platform("androidx.compose:compose-bom:2024.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
