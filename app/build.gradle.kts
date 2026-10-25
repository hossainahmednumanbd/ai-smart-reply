plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "bd.app.aismartreply"
    compileSdk = 35
    defaultConfig { applicationId = "bd.app.aismartreply"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "0.1.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { implementation("com.google.android.gms:play-services-auth:21.4.0") }
