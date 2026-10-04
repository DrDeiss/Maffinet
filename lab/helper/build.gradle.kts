plugins { alias(libs.plugins.android.application) }
android {
    namespace = "io.maffinet.lab.helper"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.maffinet.lab.helper"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "P01"
    }
    flavorDimensions += "selection"
    productFlavors {
        create("selected") { dimension = "selection"; applicationIdSuffix = ".selected" }
        create("control") { dimension = "selection"; applicationIdSuffix = ".control" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
