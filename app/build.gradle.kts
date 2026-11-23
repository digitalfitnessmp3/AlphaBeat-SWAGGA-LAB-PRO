plugins {
    id("com.android.application")
}

android {
    namespace = "com.alphabeat.daw"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.alphabeat.daw.swaggalab"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "2.2.2"
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
