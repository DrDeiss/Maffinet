import java.util.Properties
import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.maffinet.android"
    ndkVersion = providers.gradleProperty("maffinet.ndkVersion").getOrElse("30.0.14904198")
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "io.maffinet.android"
        minSdk = 26
        targetSdk = 26
        versionCode = 1
        versionName = "0.1.0-alpha"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = true
        disable.add("ExpiredTargetSdkVersion")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    sourceSets {
        getByName("main") {
            jniLibs.setSrcDirs(listOf("src/main/jniLibs", "src/main/jniLibsRust"))
        }
    }
}

dependencies {
    implementation("net.java.dev.jna:jna:5.14.0@aar")
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.startup:startup-runtime:1.2.0")
    implementation("androidx.lifecycle:lifecycle-service:2.9.4")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    testImplementation(libs.junit)
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// The inherited HEV binaries have a custom JNI ABI whose exact source commit
// is missing upstream. Keep these binaries and their compatibility bridge by
// default. Only run this task after recovering that precise source revision.
tasks.register<Exec>("rebuildHevTunnel") {
    group = "build"
    description = "Rebuild the pinned custom HEV tunnel after recovering its source."
    doFirst {
        val sourceDir = file("src/main/jni/hev-socks5-tunnel")
        if (!sourceDir.resolve("src/hev-jni.c").isFile ||
            !sourceDir.resolve("Android.mk").isFile) {
            throw GradleException(
                "Custom HEV source c26333ae1d9a0e69f1ab567ef0a46094bdfadcf1 is unavailable. " +
                    "Use the bundled binaries, or recover that source before rebuilding. " +
                    "Stock HEV has a different JNI ABI; see docs/NATIVE_PROVENANCE.md."
            )
        }
        val jniSource = sourceDir.resolve("src/hev-jni.c").readText()
        val sourceRevision = providers.exec {
            commandLine("git", "-C", sourceDir.absolutePath, "rev-parse", "HEAD")
        }.standardOutput.asText.get().trim()
        if (sourceRevision != "c26333ae1d9a0e69f1ab567ef0a46094bdfadcf1") {
            throw GradleException("HEV source revision $sourceRevision does not match the inherited custom pin.")
        }
        if (!jniSource.contains("(Ljava/lang/String;IZ)V")) {
            throw GradleException("HEV source does not contain the inherited Smart TV JNI ABI.")
        }
        val properties = Properties().apply {
            val localFile = rootProject.file("local.properties")
            if (localFile.isFile) localFile.inputStream().use { load(it) }
        }
        val sdkDir = properties.getProperty("sdk.dir")
            ?: System.getenv("ANDROID_HOME")
            ?: System.getenv("ANDROID_SDK_ROOT")
            ?: throw GradleException("Set sdk.dir or ANDROID_HOME to rebuild native code.")
        val ndkDir = File(sdkDir, "ndk/${android.ndkVersion}")
        val windows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
        val ndkBuild = File(ndkDir, if (windows) "ndk-build.cmd" else "ndk-build")
        if (!ndkBuild.isFile) throw GradleException("Install NDK ${android.ndkVersion} in $sdkDir.")
        executable = ndkBuild.absolutePath
        args(
            "NDK_PROJECT_PATH=${layout.buildDirectory.get().asFile.absolutePath}/intermediates/ndkBuild",
            "NDK_LIBS_OUT=${projectDir.absolutePath}/src/main/jniLibs",
            "APP_BUILD_SCRIPT=${projectDir.absolutePath}/src/main/jni/Android.mk",
            "NDK_APPLICATION_MK=${projectDir.absolutePath}/src/main/jni/Application.mk"
        )
    }
}

base {
    archivesName.set("Maffinet-0.1.0-alpha")
}
