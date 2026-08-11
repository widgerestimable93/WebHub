import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.widgerestimable.webhub"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.widgerestimable.webhub"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "2.0.0"
    }

    signingConfigs {
        create("release") {
            // Signature persistante : mêmes identifiants à chaque build pour
            // éviter le problème de désinstallation forcée lors des mises à
            // jour (même approche que pour CHC Android / EduFlow).
            // Fournis soit via gradle.properties local (KEYSTORE_FILE, ...),
            // soit via variables d'environnement en CI (voir
            // .github/workflows/build-apk.yml).
            val keystoreFile = System.getenv("KEYSTORE_FILE") ?: findLocalProperty("KEYSTORE_FILE")
            if (keystoreFile != null) {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: findLocalProperty("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS") ?: findLocalProperty("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD") ?: findLocalProperty("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
}

fun findLocalProperty(key: String): String? {
    val propsFile = rootProject.file("local.properties")
    if (!propsFile.exists()) return null
    val props = Properties()
    props.load(FileInputStream(propsFile))
    return props.getProperty(key)
}

