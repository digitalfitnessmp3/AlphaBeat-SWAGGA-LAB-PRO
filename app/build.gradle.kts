plugins {
    id("com.android.application")
}

// Separate QA install; explicit trusted test origins only.
val libraryApi = providers.gradleProperty("libraryQaApi").orElse("").get()
val libraryAuth = providers.gradleProperty("libraryQaAuth").orElse("").get()
val libraryKey = providers.gradleProperty("libraryQaPublicKey").orElse("").get()
require(libraryApi.isEmpty() || (libraryApi.matches(Regex("https://[a-zA-Z0-9.-]+")) && libraryApi !in listOf("https://digitalfitnessmp3.com", "https://www.digitalfitnessmp3.com")))
require(libraryAuth.isEmpty() || (libraryAuth.matches(Regex("https://[a-z0-9]+[.]supabase[.]co")) && libraryAuth != "https://kjgsdzqyctmguyznmqfm.supabase.co"))
require(libraryKey.isEmpty() || libraryKey.matches(Regex("[a-zA-Z0-9_.-]+")))

android {
    buildFeatures { buildConfig = true }
    namespace = "com.alphabeat.daw"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.alphabeat.daw.swaggalab.libraryqa"
        minSdk = 26
        targetSdk = 36
        versionCode = 23
        versionName = "2.3.1-LIBRARY-QA"
        buildConfigField("String", "LIBRARY_QA_API", "\"$libraryApi\"")
        buildConfigField("String", "LIBRARY_QA_AUTH", "\"$libraryAuth\"")
        buildConfigField("String", "LIBRARY_QA_PUBLIC_KEY", "\"$libraryKey\"")
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
