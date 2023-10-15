plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.kotlin)
}

android {
    namespace = "com.berdik.letmedowngrade"
    compileSdk = 34
    buildToolsVersion = "34.0.0"

    defaultConfig {
        minSdk = 31
        targetSdk = 34
        versionCode = 6
        versionName = "1.0.3"
        applicationId = "com.berdik.letmedowngrade"
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles("proguard-rules.pro")
            signingConfig = signingConfigs["debug"]
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
            excludes += "**"
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.markdownview)
    implementation(libs.libxposed.service)
    compileOnly(libs.libxposed.api)
}