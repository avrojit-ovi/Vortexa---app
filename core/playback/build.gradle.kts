plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.vortexa.core.playback"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 } }

dependencies {
    implementation(project(":core:common"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
