import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing key lives outside the repo. Override the location with
// -PkeystoreProperties=/path/to/keystore.properties if needed.
val keystorePropsFile = file(
    (findProperty("keystoreProperties") as String?)
        ?: "${System.getProperty("user.home")}/.config/bravia-launcher/keystore.properties"
)
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "io.github.shhrrtnvr.bravialauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.shhrrtnvr.bravialauncher"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // Pure Kotlin/Java: no native libraries, works on the TV's 32-bit userspace.
    packaging {
        jniLibs.excludes += "**"
    }
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
