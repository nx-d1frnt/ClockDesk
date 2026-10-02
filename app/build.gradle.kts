plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    kotlin("kapt")
}

fun getGitCommitHash(): String {
    val ciCommit = System.getenv("GITHUB_SHA")
    if (!ciCommit.isNullOrBlank()) {
        return ciCommit.take(7)
    }
    return runCatching {
        ProcessBuilder("git", "rev-parse", "--short", "HEAD")
            .directory(rootDir)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
            .inputStream
            .bufferedReader()
            .readText()
            .trim()
            .ifEmpty { "dev" }
    }.getOrDefault("dev")
}

android {
    namespace = "com.nxd1frnt.clockdesk2"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nxd1frnt.clockdesk2"
        minSdk = 23
        targetSdk = 35
        versionCode = 2000296
        versionName = "2.0.0-rc2"
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("releaseKey") {
            val keystorePath = System.getenv("KEYSTORE_FILE")
            val keystoreFile = if (!keystorePath.isNullOrBlank()) {
                val f = file(keystorePath)
                if (f.exists()) f else file("$rootDir/app/$keystorePath")
            } else null

            if (keystoreFile != null && keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            } else {
                val debugConfig = getByName("debug")
                storeFile = debugConfig.storeFile
                storePassword = debugConfig.storePassword
                keyAlias = debugConfig.keyAlias
                keyPassword = debugConfig.keyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            // Fast development builds without minification
        }
        create("minifiedDebug") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("nightly") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
            isDebuggable = false
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("releaseKey")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            versionNameSuffix = "-nightly-${getGitCommitHash()}"
        }
        getByName("release") {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("releaseKey")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    // Assign higher version code offsets to 64-bit architectures so devices update cleanly
    val abiCodes = mapOf(
        "armeabi-v7a" to 1,
        "x86" to 2,
        "arm64-v8a" to 3,
        "x86_64" to 4
    )

    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val apkOutput = this as? com.android.build.gradle.internal.api.ApkVariantOutputImpl
            val abiFilter = apkOutput?.filters?.find {
                it.filterType == com.android.build.OutputFile.ABI
            }?.identifier

            val appName = "ClockDesk"
            val versionName = variant.versionName
            val buildType = variant.buildType.name
            val abiName = abiFilter ?: "universal"

            val abiCode = abiCodes[abiFilter] ?: 0
            if (abiCode != 0) {
                apkOutput?.versionCodeOverride = (variant.versionCode * 10) + abiCode
            }

            apkOutput?.outputFileName = "${appName}-${buildType}-${abiName}-v${versionName}.apk"
        }
    }
    androidResources{
        generateLocaleConfig = true
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            excludes += "/META-INF/versions/9/OSGI-INF/*"
            excludes += "META-INF/versions/9/OSGI-INF/*"
        }
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.cardview)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.volley)
    implementation(libs.androidx.palette.ktx)
    implementation("com.vanniktech:android-image-cropper:4.3.3")
    implementation("com.github.bumptech.glide:glide:4.15.1")
    kapt("com.github.bumptech.glide:compiler:4.15.1")
    implementation("com.github.skydoves:colorpickerview:2.3.0")
    implementation(libs.bouncycastle.prov)
    implementation(libs.bouncycastle.pkix)
}