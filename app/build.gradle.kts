plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)
    alias(libs.plugins.ksp)
}


android {
    namespace = "com.example.app_rutas"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.app_rutas"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {


    implementation(libs.play.services.maps.v1810)
    implementation(libs.okhttp)
    implementation(libs.material.v1110)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.org.json)
    implementation(libs.google.android.maps.utils)
    implementation(libs.retrofit)
    implementation(libs.places)

    //implementation ("com.google.android.gms:play-services-tasks:18.0.2")

    implementation (libs.converter.gson)
    implementation (libs.logging.interceptor)
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.database.ktx)
    implementation (libs.firebase.storage)
    implementation (libs.androidx.lifecycle.viewmodel.ktx)
    implementation (libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.volley)
    implementation(libs.cronet.embedded)
    implementation(libs.play.services.maps)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.play.services.location)
    implementation(libs.androidx.fragment.ktx)

    implementation(libs.converter.moshi)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.work.ktx)
    implementation(libs.bcrypt)
    implementation(libs.coil)
    ksp(libs.room.compiler)
}
