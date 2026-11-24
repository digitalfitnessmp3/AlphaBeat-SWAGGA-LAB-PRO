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
        versionCode = 18
        versionName = "2.2.7-QA-32-LIVE"
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
