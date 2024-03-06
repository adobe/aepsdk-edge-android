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
    id("com.android.application")
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
    namespace = "com.adobe.marketing.mobile.edge.testapp.kotlin"

    defaultConfig {
        applicationId = "com.adobe.marketing.tester.app"
        minSdk = BuildConstants.Versions.MIN_SDK_VERSION
        compileSdk = BuildConstants.Versions.COMPILE_SDK_VERSION
        targetSdk = BuildConstants.Versions.TARGET_SDK_VERSION

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

configurations.configureEach {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}

dependencies {
    implementation(project(":edge"))
    implementation(project(":app-util-xdm"))

    implementation("com.adobe.marketing.mobile:core:$mavenCoreVersion-SNAPSHOT")
    implementation("com.adobe.marketing.mobile:edgeidentity:$mavenEdgeIdentityVersion-SNAPSHOT") {
        exclude(group = "com.adobe.marketing.mobile", module = "core")
    }
    implementation("com.adobe.marketing.mobile:edgeconsent:2.0.0") {
        exclude(group = "com.adobe.marketing.mobile", module = "edge")
    }
    implementation("com.adobe.marketing.mobile:assurance:2.2.1")

    implementation("com.google.code.gson:gson:2.8.9")
}
