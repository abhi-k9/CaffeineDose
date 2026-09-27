import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * Timer logic, free of Android dependencies: it is unit tested on the JVM, and the Android layer only adapts it to
 * the platform (foreground service, wake lock, notification).
 */
plugins {
    alias(libs.plugins.kotlin.jvm)
    // Android lint also analyzes this module, as a dependency of :app.
    alias(libs.plugins.android.lint)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        allWarningsAsErrors = true
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

lint {
    abortOnError = true
    warningsAsErrors = true
}

tasks.test {
    useJUnitPlatform()
}
