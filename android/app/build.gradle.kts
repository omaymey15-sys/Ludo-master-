plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace   = "com.ludomasterpro"
    compileSdk  = 35

    defaultConfig {
        applicationId = "com.ludomasterpro"
        minSdk        = 24
        targetSdk     = 35
        versionCode   = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName   = System.getenv("VERSION_NAME") ?: "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            signingConfig     = null   // signé par apksigner en CI
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug { isDebuggable = true }
    }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.13" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions  { jvmTarget = "17" }
    packaging      { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.05.00")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
