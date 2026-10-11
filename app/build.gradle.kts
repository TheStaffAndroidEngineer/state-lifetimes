plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinCompose)
}

android {
    namespace = "staff.android.lifetimes"
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }
    buildToolsVersion = "37.0.0"
    defaultConfig {
        applicationId = "staff.android.lifetimes"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        if (providers.gradleProperty("exercise").isPresent) {
            testInstrumentationRunnerArguments["annotation"] = "staff.android.lifetimes.ExerciseOnly"
        } else {
            testInstrumentationRunnerArguments["notAnnotation"] = "staff.android.lifetimes.ExerciseOnly"
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_26
        targetCompatibility = JavaVersion.VERSION_26
    }
}
kotlin {
    jvmToolchain(27)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_26) }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidxCore)
    implementation(libs.androidxActivity)
    implementation(libs.androidxActivityCompose)
    implementation(libs.androidxLifecycleViewModel)
    implementation(libs.androidxLifecycleRuntime)
    implementation(libs.androidxLifecycleRuntimeCompose)
    implementation(libs.androidxLifecycleSavedState)
    implementation(libs.dataStore)
    implementation(libs.coroutinesAndroid)
    val composeBom = platform(libs.composeBom)
    implementation(composeBom)
    implementation(libs.composeUi)
    implementation(libs.composeFoundation)
    implementation(libs.composeMaterial3)
    implementation(libs.composeToolingPreview)
    debugImplementation(libs.composeTooling)
    debugImplementation(libs.composeTestManifest)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.composeTestJunit4)
    androidTestImplementation(libs.androidxTestJunit)
    androidTestImplementation(libs.androidxTestRunner)
    androidTestImplementation(libs.espressoCore)
}
