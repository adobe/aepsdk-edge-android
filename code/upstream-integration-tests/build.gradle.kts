/**
 * Copyright 2024 Adobe. All rights reserved.
 * This file is licensed to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 * OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

import com.adobe.marketing.mobile.gradle.BuildConstants

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.diffplug.spotless")
}

val mavenCoreVersion: String by project
val mavenEdgeIdentityVersion: String by project

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    kotlin {
        target("src/*/java/**/*.kt")
        ktlint(BuildConstants.Versions.KTLINT)
        licenseHeader(BuildConstants.ADOBE_LICENSE_HEADER)
    }
}

android {
    namespace = "com.adobe.marketing.mobile.edge.integration"

    defaultConfig {
        minSdk = BuildConstants.Versions.MIN_SDK_VERSION
        compileSdk = BuildConstants.Versions.COMPILE_SDK_VERSION
        testOptions.targetSdk = BuildConstants.Versions.TARGET_SDK_VERSION

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        // The following argument makes the Android Test Orchestrator run its
        // "pm clear" command after each test invocation. This command ensures
        // that the app's state is completely cleared between tests.
        testInstrumentationRunnerArguments["clearPackageData"] = "true"

        val EDGE_LOCATION_HINT: String by project
        val EDGE_ENVIRONMENT: String by project

        // buildConfigField assignment with default values
        buildConfigField("String", "EDGE_LOCATION_HINT", "\"$EDGE_LOCATION_HINT\"")
        buildConfigField("String", "EDGE_ENVIRONMENT", "\"$EDGE_ENVIRONMENT\"")
    }

    buildTypes {
        getByName(BuildConstants.BuildTypes.RELEASE) {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = BuildConstants.Versions.JAVA_SOURCE_COMPATIBILITY
        targetCompatibility = BuildConstants.Versions.JAVA_TARGET_COMPATIBILITY
    }

    kotlinOptions {
        jvmTarget = BuildConstants.Versions.KOTLIN_JVM_TARGET
        languageVersion = BuildConstants.Versions.KOTLIN_LANGUAGE_VERSION
        apiVersion = BuildConstants.Versions.KOTLIN_API_VERSION
    }

    buildFeatures {
        // Flag enables BuildConfig generation for AGP 8.0+
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":edge"))

    implementation("androidx.core:core-ktx:1.9.0")

    androidTestImplementation("com.github.adobe:aepsdk-testutils-android:4c235ab81f")
    androidTestImplementation("com.adobe.marketing.mobile:core:$mavenCoreVersion")
    androidTestImplementation("com.adobe.marketing.mobile:edgeidentity:$mavenEdgeIdentityVersion")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
    androidTestImplementation("androidx.test:runner:1.4.0")

    androidTestUtil("androidx.test:orchestrator:1.4.1")
}
