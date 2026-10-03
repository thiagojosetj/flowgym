plugins {
    alias(libs.plugins.android.application)
}

android {
    // Neutral technical namespace (ADR-0026): the brand can change without touching packages.
    namespace = "io.github.thiagojosetj.gym"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // Provisional product id. Must be final before the first Play Store upload.
        applicationId = "io.github.thiagojosetj.flowgym"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        javaCompileOptions {
            annotationProcessorOptions {
                // Room exports each schema version as JSON (reviewed in diffs, used by migration tests).
                arguments["room.schemaLocation"] = "$projectDir/schemas"
                arguments["room.incremental"] = "true"
            }
        }
    }

    // Signing for the release build, taken from the environment and never from a file in the
    // repository (the repository is public). Unset environment = unsigned release, which is exactly
    // what this build did before and what a fork or a contributor without the key still gets; only
    // the install on a phone needs a signature, not the gate.
    //
    // The DEBUG build is deliberately left on the SDK's own debug key. Signing it here with a
    // different key would make a locally installed debug build and a downloaded one reject each
    // other - same applicationId, different signature - and the only way out of that is to
    // uninstall, which deletes the training history.
    val releaseKeystore = System.getenv("FLOWGYM_KEYSTORE_FILE")
    val hasReleaseKey = !releaseKeystore.isNullOrBlank() && file(releaseKeystore).exists()

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(releaseKeystore!!)
                storePassword = System.getenv("FLOWGYM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("FLOWGYM_KEY_ALIAS")
                keyPassword = System.getenv("FLOWGYM_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Debug and release builds can be installed side by side.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            if (hasReleaseKey) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true // version name shown in Settings
    }

    sourceSets {
        // Room schema JSON files are read as assets by MigrationTestHelper (device tests).
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    lint {
        checkDependencies = true
        abortOnError = true
        warningsAsErrors = false
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}

tasks.withType<Test>().configureEach {
    // Robolectric (SDK 36+) touches FileDescriptor internals, which JDK 17+ hides by default.
    // Test JVM only: this never affects the app.
    jvmArgs(
        "--add-exports", "java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens", "java.base/java.io=ALL-UNNAMED",
    )
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.coordinatorlayout)
    implementation(libs.androidx.core)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.room.runtime)
    annotationProcessor(libs.androidx.room.compiler)
    implementation(libs.google.material)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.espresso.core)
    testImplementation(libs.androidx.test.espresso.contrib)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
}
