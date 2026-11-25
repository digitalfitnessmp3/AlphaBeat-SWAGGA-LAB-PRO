plugins {
    id("com.android.application")
}

// Official AlphaBeat library. Public project key only; never a service-role key.
val libraryApi = "https://kjgsdzqyctmguyznmqfm.supabase.co"
val libraryAuth = libraryApi
val libraryKey = "sb_publishable_FY831v8_rYQv6shaVlU55g_omW39uvI"
val libraryGoogleWebClient = providers.gradleProperty("libraryGoogleWebClient").orElse("").get()
require(libraryGoogleWebClient.isEmpty() || libraryGoogleWebClient.matches(Regex("329922968354-[a-z0-9]{32}[.]apps[.]googleusercontent[.]com"))) {
    "Supply the exact official Google Web OAuth client ID."
}
require(libraryGoogleWebClient != "329922968354-qva57sms2uj9uslr7h6q1kmsprci63ca.apps.googleusercontent.com") {
    "QA OAuth client is not permitted."
}

// Closed Alpha testing only. Public rollout requires privacy and end-to-end review.
tasks.configureEach {
    if (name == "bundleRelease" || name == "assembleRelease") {
        doFirst {
            require(providers.gradleProperty("closedAlphaCandidate").orNull == "true" && libraryGoogleWebClient.isNotEmpty()) {
                "Supply the official Web OAuth ID and explicitly select the closed Alpha candidate."
            }
        }
    }
}

android {
    buildFeatures { buildConfig = true }
    namespace = "com.alphabeat.daw"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.alphabeat.daw.swaggalab"
        minSdk = 26
        targetSdk = 36
        versionCode = 25
        versionName = "2.4.1"
        buildConfigField("String", "LIBRARY_API", "\"$libraryApi\"")
        buildConfigField("String", "LIBRARY_AUTH", "\"$libraryAuth\"")
        buildConfigField("String", "LIBRARY_PUBLIC_KEY", "\"$libraryKey\"")
        buildConfigField("String", "LIBRARY_GOOGLE_WEB_CLIENT", "\"$libraryGoogleWebClient\"")
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

 dependencies {
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
}
