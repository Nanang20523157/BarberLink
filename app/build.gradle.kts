plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-android")
    id("kotlin-parcelize")
    id("com.google.gms.google-services")
    id("androidx.navigation.safeargs")
}

android {
    namespace = "com.example.barberlink"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.barberlink"
        minSdk = 24
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
    buildFeatures {
        viewBinding = true
    }
    //binding
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

}

dependencies {

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.firebase:firebase-firestore:25.0.0")
    implementation("com.google.firebase:firebase-storage:21.0.0")
    implementation("com.google.firebase:firebase-auth:23.0.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("androidx.recyclerview:recyclerview:1.3.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.10")

    // Coroutines Core
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Coroutines Android
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // ImageSlider
    implementation("com.github.denzcoskun:ImageSlideshow:0.1.2")

    // Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // PinView
    implementation("io.github.chaosleung:pinview:1.4.4")

    // CircularImageView
    implementation("de.hdodenhof:circleimageview:3.1.0")

    // Lottie
    implementation("com.airbnb.android:lottie:3.7.0")

    // Gson
    implementation("com.google.code.gson:gson:2.10.1")

    // ImagePicker
    implementation ("com.github.dhaval2404:imagepicker:2.1")

    // Shimmer
    implementation("com.facebook.shimmer:shimmer:0.5.0")

    // RefreshLayout
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // Kotlin extensions for ViewModel and LiveData
    implementation("androidx.fragment:fragment-ktx:1.8.2")

    // YearPickerOnly
    implementation("com.github.demogorgorn:MonthAndYearPicker:1.0.11")

    implementation("com.google.android.gms:play-services-auth:21.2.0")

    implementation("com.github.judemanutd:autostarter:1.1.0")

    implementation("androidx.activity:activity-ktx:1.8.0") // Pastikan versi terbaru (edgeToEdge FullSceen)

    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

    implementation("androidx.work:work-runtime-ktx:2.8.1")

    // Add this line to include the Lifecycle library
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    implementation("androidx.core:core-ktx:1.12.0")

}