plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "bd.app.aismartreply"
    compileSdk = 36
    defaultConfig { applicationId = "bd.app.aismartreply"; minSdk = 26; targetSdk = 36; versionCode = 5; versionName = "0.3.1" }
    val permanentStore = System.getenv("ANDROID_KEYSTORE_PATH")
    if (!permanentStore.isNullOrBlank()) {
        signingConfigs {
            create("permanentRelease") {
                storeFile = file(permanentStore)
                storeType = "PKCS12"
                storePassword = System.getenv("ANDROID_STORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
        buildTypes {
            getByName("release") { signingConfig = signingConfigs.getByName("permanentRelease") }
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { testImplementation("junit:junit:4.13.2"); implementation("com.google.android.gms:play-services-auth:21.4.0") }
