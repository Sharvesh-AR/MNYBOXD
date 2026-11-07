import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import java.time.Duration

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

/**
 * Release signing credentials are read from environment variables or from the
 * user-level ~/.gradle/gradle.properties. They are never committed to the repository.
 *
 *   SPENDLY_KEYSTORE_FILE      absolute path to the .jks/.p12 keystore
 *   SPENDLY_KEYSTORE_PASSWORD  keystore password
 *   SPENDLY_KEY_ALIAS          key alias
 *   SPENDLY_KEY_PASSWORD       key password
 */
fun signingValue(name: String): String? =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() }

val releaseKeystore = signingValue("SPENDLY_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.isFile }
val hasReleaseSigning = releaseKeystore != null &&
    signingValue("SPENDLY_KEYSTORE_PASSWORD") != null &&
    signingValue("SPENDLY_KEY_ALIAS") != null &&
    signingValue("SPENDLY_KEY_PASSWORD") != null

android {
    namespace = "io.github.sharveshar.spendly"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.sharveshar.spendly"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = signingValue("SPENDLY_KEYSTORE_PASSWORD")
                keyAlias = signingValue("SPENDLY_KEY_ALIAS")
                keyPassword = signingValue("SPENDLY_KEY_PASSWORD")
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    sourceSets {
        // Exported Room schemas are read by the Robolectric migration tests from the debug
        // variant's assets. They contain only table definitions and never ship in release builds.
        getByName("debug").assets.directories.add("$projectDir/schemas")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.maxHeapSize = "2g" }
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        lintConfig = file("lint.xml")
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*", "/META-INF/NOTICE*")
        }
    }

    dependenciesInfo {
        // Do not embed the (encrypted) dependency metadata block in the APK.
        includeInApk = false
        includeInBundle = false
    }
}

// Unit/UI tests run against the debug variant only: they need the Room schema assets and
// debug-only sample data, neither of which may ship in release builds. Release code is the
// same Kotlin source; the release APK itself is checked by lintRelease and apksigner.
/*
 * Robolectric's Android runtime jar is resolved by Gradle (checksum-verified via
 * gradle/verification-metadata.xml, cached, retried) and Robolectric runs offline. Left to itself
 * Robolectric downloads the jar over plain HTTP at test time, which CI runners get rate-limited on
 * (HTTP 429 from Maven Central) and which bypasses dependency verification.
 */
val robolectricRuntime: Configuration = configurations.create("robolectricRuntime") { isTransitive = false }
// Kept under the Gradle user home: Robolectric URL-encodes this path and fails on directories
// containing spaces (such as a project folder named "Expense Manager").
val robolectricRuntimeDir = File(gradle.gradleUserHomeDir, "robolectric-runtime/${libs.versions.robolectricAndroidAll.get()}")
val prepareRobolectricRuntime = tasks.register<Sync>("prepareRobolectricRuntime") {
    from(robolectricRuntime)
    into(robolectricRuntimeDir)
}

tasks.withType<Test>().configureEach {
    if (name == "testReleaseUnitTest") enabled = false
    dependsOn(prepareRobolectricRuntime)
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", robolectricRuntimeDir.absolutePath)
    // The whole suite takes about two minutes. Each Robolectric test also has its own limit
    // (HangWatchdog); this is the backstop so a hang fails the build instead of stalling CI.
    timeout.set(Duration.ofMinutes(15))
    // Show the actual assertion and stack trace in the console (and CI log), not just "FAILED".
    testLogging {
        events("failed", "skipped")
        exceptionFormat = TestExceptionFormat.FULL
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite.ktx)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    robolectricRuntime(libs.robolectric.android.all)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.room.testing)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
