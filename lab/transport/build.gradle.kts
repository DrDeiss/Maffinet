plugins { alias(libs.plugins.android.application) }
android {
    namespace = "io.maffinet.lab.transport"
    compileSdk = 36
    ndkVersion = providers.gradleProperty("maffinet.ndkVersion").getOrElse("29.0.14206865")
    defaultConfig {
        applicationId = "io.maffinet.lab.transport"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "P01"
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64") }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DHEV_SOURCE=${rootProject.file(providers.gradleProperty("maffinet.transportSource").getOrElse(".toolchain/transport-source")).absolutePath.replace('\\', '/')}")
                providers.gradleProperty("maffinet.python").orNull?.let {
                    arguments += "-DPython3_EXECUTABLE=${it.replace('\\', '/')}"
                }
            }
        }
    }
    externalNativeBuild { cmake { path = file("native/CMakeLists.txt"); version = "3.22.1" } }
    sourceSets { getByName("main").assets.srcDir("licenses") }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
