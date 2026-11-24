plugins {
    id("com.android.application")
}

android {
    namespace = "com.alphabeat.daw"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.alphabeat.daw.swaggalab.qa224"
        minSdk = 26
        targetSdk = 36
        versionCode = 17
        versionName = "2.2.6-QA-TIMELINE"
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
