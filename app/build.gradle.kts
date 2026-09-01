import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
}

/**
 * Build-time config read from `local.properties` (untracked) or a Gradle property,
 * so no developer's LAN address, deployment URL or keystore password lands in git.
 */
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun buildConfigProperty(name: String): String? =
    localProps.getProperty(name)
        ?: providers.gradleProperty(name).orNull
        ?: System.getenv(name.replace('.', '_').uppercase())

/**
 * Debug base URL. Defaults to the emulator's host alias, which is what a fresh
 * clone needs; set `trackit.api.baseUrl` for a physical device on your LAN.
 */
val debugApiBaseUrl: String =
    buildConfigProperty("trackit.api.baseUrl") ?: "http://10.0.2.2:8787/"

/**
 * Release base URL, deliberately with no default.
 *
 * A release build that silently inherited the debug default would ship pointing at
 * http://10.0.2.2:8787 — unreachable off an emulator, and blocked outright because
 * release builds have no cleartext permission. Every network call would fail with
 * no obvious cause. Failing the build is the honest outcome.
 *
 * Set `trackit.api.baseUrl.release` in local.properties, as a Gradle property, or
 * via the TRACKIT_API_BASEURL_RELEASE environment variable in CI.
 */
val releaseApiBaseUrl: String? = buildConfigProperty("trackit.api.baseUrl.release")

fun validatedReleaseUrl(): String {
    val url = releaseApiBaseUrl
        ?: throw GradleException(
            "trackit.api.baseUrl.release is not set — a release build has no backend URL.\n" +
                "Add it to local.properties, e.g.\n" +
                "  trackit.api.baseUrl.release=https://trackit-api.<subdomain>.workers.dev/\n" +
                "or set TRACKIT_API_BASEURL_RELEASE in the environment."
        )
    if (!url.startsWith("https://")) {
        throw GradleException(
            "trackit.api.baseUrl.release must use https:// (got: $url).\n" +
                "Release builds enforce Android's HTTPS-only policy, so a cleartext URL " +
                "would fail every request at runtime."
        )
    }
    if (!url.endsWith("/")) {
        throw GradleException("trackit.api.baseUrl.release must end with a trailing slash (Retrofit requires it): $url")
    }
    return url
}

ksp {
    // Destination for the Room schema JSON that `exportSchema = true` emits. These
    // files are committed: they are what makes a migration reviewable, and Room's
    // migration tests read them.
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.trackit.expense"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.trackit.expense"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    /**
     * Release signing, configured only when a keystore is actually available.
     *
     * TrackIt is distributed as a signed APK via GitHub Releases, so the signing key
     * is the user's install identity: sign an update with a different key and Android
     * refuses to install it over the existing app. Keep `trackit-release.jks` and its
     * passwords out of the repo (both are gitignored) and back the keystore up —
     * losing it means no existing install can ever be updated again.
     *
     * Configure in local.properties:
     *   trackit.keystore.path=/absolute/path/to/trackit-release.jks
     *   trackit.keystore.password=...
     *   trackit.key.alias=trackit
     *   trackit.key.password=...
     *
     * or via TRACKIT_KEYSTORE_PATH / TRACKIT_KEYSTORE_PASSWORD / TRACKIT_KEY_ALIAS /
     * TRACKIT_KEY_PASSWORD in CI.
     */
    val keystorePath = buildConfigProperty("trackit.keystore.path")
    val hasReleaseKeystore = keystorePath != null && file(keystorePath).exists()

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = buildConfigProperty("trackit.keystore.password")
                keyAlias = buildConfigProperty("trackit.key.alias")
                keyPassword = buildConfigProperty("trackit.key.password")
                // v1 too: minSdk is 26, and some sideload paths still check it.
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Not validated here: buildTypes is configured on every invocation, so
            // throwing would break assembleDebug too. verifyReleaseConfig does the
            // validation and assembleRelease depends on it, so no release APK is ever
            // produced with this placeholder — which is deliberately unreachable
            // rather than a working-looking default.
            val releaseUrl = releaseApiBaseUrl ?: "https://release-url-not-configured.invalid/"
            buildConfigField("String", "API_BASE_URL", "\"$releaseUrl\"")

            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.warn(
                    "No release keystore configured (trackit.keystore.path) — the release " +
                        "APK will be unsigned and cannot be installed. See app/build.gradle.kts."
                )
            }
        }
        debug {
            isDebuggable = true
            // No applicationIdSuffix here: google-services.json only registers
            // com.trackit.expense, and the Firebase API key is restricted to that
            // package plus signing cert, so a suffixed id breaks Google Sign-In.
            buildConfigField("String", "API_BASE_URL", "\"$debugApiBaseUrl\"")
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
        buildConfig = true
    }

    lint {
        // ComposableStateFlowValueDetector ships a kotlinx-metadata-jvm that only
        // reads Kotlin metadata up to 2.0.0, and this project compiles to 2.1.0, so
        // the detector throws and takes the whole lint run down. The crash is in the
        // detector, not in our code. Re-enable once the Compose lint artifact
        // catches up to the Kotlin version in gradle/libs.versions.toml.
        disable += "StateFlowValueCalledInComposition"
    }

    sourceSets {
        // MigrationTestHelper reads the exported Room schemas from the test APK's
        // assets, so the schemas directory has to be an androidTest asset root.
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
    }

    testOptions {
        unitTests {
            // SmsParser calls android.util.Log, which is a stub in the JVM test
            // runtime and throws "not mocked" by default. Returning defaults lets
            // the parser be unit-tested without a Robolectric or mockk-static shim.
            isReturnDefaultValues = true
        }
    }

    // composeOptions removed: Kotlin 2.x uses kotlin.plugin.compose instead

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // The androidTest variant pulls JUnit 5 transitively, and every one of
            // its jars ships these metadata files. Without excluding them the
            // instrumented test APK cannot be packaged at all.
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/*.kotlin_module"
        }
    }
}

/**
 * Fails a release build whose backend URL is missing or unusable.
 *
 * Deliberately a task rather than a check inside `buildTypes`: that block is
 * configured on every invocation, so throwing there would break `assembleDebug`
 * too. Wired in `afterEvaluate` because AGP registers assembleRelease/bundleRelease
 * after this script is configured, so a `tasks.matching {}` filter set up here never
 * sees them.
 */
val verifyReleaseConfig = tasks.register("verifyReleaseConfig") {
    group = "verification"
    description = "Checks that the release backend URL is set and uses https."
    doLast {
        val url = validatedReleaseUrl()
        logger.lifecycle("Release backend URL: $url")
        if (buildConfigProperty("trackit.keystore.path") == null) {
            logger.warn(
                "WARNING: no release keystore configured (trackit.keystore.path) — " +
                    "this APK will be unsigned and cannot be installed."
            )
        }
    }
}

afterEvaluate {
    listOf("assembleRelease", "bundleRelease").forEach { name ->
        tasks.findByName(name)?.dependsOn(verifyReleaseConfig)
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.material)

    // Compose BOM - manages all Compose versions
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Lifecycle & ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Retrofit & OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.gson)

    // Hilt Dependency Injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Kotlin Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // Accompanist
    implementation(libs.accompanist.permissions)
    implementation(libs.accompanist.systemuicontroller)

    // ShortcutBadger (App Icon Badge)
    implementation(libs.shortcutbadger)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.google.play.services.location)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.google.play.services.auth)
    implementation(libs.kotlinx.coroutines.play.services)

    // Glance – home screen widget
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // ── Unit tests (JVM, no device) ────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.androidx.room.testing)

    // ── Instrumented tests (device/emulator) ───────────────────────────────
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.turbine)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.arch.core.testing)
    kspAndroidTest(libs.hilt.android.compiler)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

