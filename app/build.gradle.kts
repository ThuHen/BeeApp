
plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
    alias(libs.plugins.google.android.libraries.mapsplatform.secrets.gradle.plugin)

}

android {
    namespace = "com.github.thuhen.beeapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.github.thuhen.beeapp"
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation ("com.google.android.gms:play-services-location:21.3.0")//for location
    implementation ("com.google.android.gms:play-services-maps:19.0.0")
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-database")
    // Full GeoFire library for Realtime Database users
    implementation ("com.firebase:geofire-android:3.2.0")

    // GeoFire utililty functions for Cloud Firestore users who
    // want to implement their own geo solution, see:
    // https://firebase.google.com/docs/firestore/solutions/geoqueries
    implementation ("com.firebase:geofire-android-common:3.2.0")
    implementation ("com.google.android.gms:play-services-location:21.0.1")
    //
    implementation ("com.google.android.libraries.places:places:3.1.0")

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    implementation(libs.play.services.maps)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}