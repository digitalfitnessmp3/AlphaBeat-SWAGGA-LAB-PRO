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
        versionCode = 23
        versionName = "2.3.1"
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
