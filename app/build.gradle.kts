import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val versionMajor = 1
val versionMinor = 0
val versionPatch = 4
val versionBuild = 0
val appVersionName = "$versionMajor.$versionMinor.$versionPatch"

/**
 * `M…Mmmppbb`: every component gets its own two digits (more for major), so codes never overlap and always increase,
 * e.g. `1.2.3` → `1020300`. Google Play caps versionCode at 2100000000.
 */
fun versionCodeOf(major: Int, minor: Int, patch: Int, build: Int): Int {
    require(major in 0..2099) { "versionMajor must be in 0..2099, was $major" }
    require(minor in 0..99) { "versionMinor must be in 0..99, was $minor" }
    require(patch in 0..99) { "versionPatch must be in 0..99, was $patch" }
    require(build in 0..99) { "versionBuild must be in 0..99, was $build" }
    return major * 1_000_000 + minor * 10_000 + patch * 100 + build
}

/**
 * Release signing is read from Gradle properties or environment variables, so that no secret is ever committed:
 * `caffeinedose.signing.storeFile` / `CAFFEINEDOSE_SIGNING_STORE_FILE`, and likewise `storePassword`, `keyAlias`, `keyPassword`.
 * Release builds are left unsigned when no store file is configured.
 */
fun signingValue(name: String): String? =
    providers.gradleProperty("caffeinedose.signing.$name")
        .orElse(providers.environmentVariable("CAFFEINEDOSE_SIGNING_" + name.replace(Regex("([A-Z])"), "_$1").uppercase()))
        .orNull
        ?.takeIf { it.isNotBlank() }

fun requiredSigningValue(name: String): String =
    requireNotNull(signingValue(name)) { "Release signing is configured, but caffeinedose.signing.$name is missing" }

android {
    namespace = "io.github.abhik9.caffeinedose"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.abhik9.caffeinedose"
        minSdk = 26
        targetSdk = 37
        versionCode = versionCodeOf(versionMajor, versionMinor, versionPatch, versionBuild)
        versionName = appVersionName
    }

    signingConfigs {
        val storeFile = signingValue("storeFile")
        if (storeFile != null) {
            create("release") {
                this.storeFile = file(storeFile).also { require(it.isFile) { "Release keystore not found: $it" } }
                storePassword = requiredSigningValue("storePassword")
                keyAlias = requiredSigningValue("keyAlias")
                keyPassword = requiredSigningValue("keyPassword")
                // v2 for Android 8.0 to 8.1, v3 since Android 9, which also allows rotating the signing key later.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            // Installable next to the release build.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        checkDependencies = true
        // Dependency updates are handled by Dependabot, not by failing unrelated builds.
        disable += setOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable", "OldTargetApi")
    }

    // Reproducible builds: no Google-encrypted dependency metadata in the outputs (F-Droid / IzzyOnDroid).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources.excludes += setOf("kotlin-tooling-metadata.json", "**/*.kotlin_builtins", "META-INF/*.version")
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        allWarningsAsErrors = true
    }
}

// Used by the release workflow to check that the pushed tag matches the app version.
tasks.register("printVersionName") {
    val versionName = appVersionName
    doLast { println(versionName) }
}

// Used by the release workflow to find the changelog of the app version: fastlane/metadata/android/en-US/changelogs/<code>.txt
tasks.register("printVersionCode") {
    val versionCode = versionCodeOf(versionMajor, versionMinor, versionPatch, versionBuild)
    doLast { println(versionCode) }
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
