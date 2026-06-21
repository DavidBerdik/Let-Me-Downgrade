plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.berdik.letmedowngrade"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        minSdk = 31
        targetSdk = 36
        versionCode = 11
        versionName = "1.1.0"
        applicationId = "com.berdik.letmedowngrade"
    }

    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
            excludes += "**"
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.activity)
    implementation(libs.activity.compose)
    implementation(libs.appcompat)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.libxposed.service)
    compileOnly(libs.libxposed.api)
    debugImplementation(libs.compose.tooling)
}