plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "uz.notgis.documate"
    compileSdk = 35

    defaultConfig {
        applicationId = "uz.notgis.documate"
        minSdk = 26 // POI java.time API 26+ da native (desugaring siz)
        targetSdk = 35
        versionCode = 13
        versionName = "0.7.1"
    }

    // CI debug imzo: DEBUG_KEYSTORE bo'lsa shu ishlatiladi (aks holda standart debug kalit).
    val debugKeystorePath: String? = System.getenv("DEBUG_KEYSTORE")
    signingConfigs {
        getByName("debug") {
            if (debugKeystorePath != null) {
                storeFile = file(debugKeystorePath)
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    // Imzo ma'lumotlari faqat muhit o'zgaruvchilarida bo'lsa ishlatiladi (GitHub Secrets).
    val keystorePath: String? = System.getenv("KEYSTORE_FILE")
    signingConfigs {
        create("release") {
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
            excludes += "META-INF/*.kotlin_module"
            excludes += "META-INF/*.properties"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/versions/**"
            excludes += "META-INF/services/javax.annotation.processing.Processor"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // PDF parol + matn qidirish (offline, Tom-Roush forki).
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // Eski Office formatlari (.doc/.xls/.ppt) — offline, Apache POI.
    // Yangi formatlar (docx/xlsx) WebView (mammoth/SheetJS) + yengil parserda qoladi.
    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-scratchpad:5.2.5")

    testImplementation("junit:junit:4.13.2")
}
