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
        versionCode = 21
        versionName = "2.3.0-QA-LIVE-PHASES"
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
