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

plugins {
    id("aep-library")
}

val mavenCoreVersion: String by project
val mavenEdgeConsentVersion: String by project
val mavenEdgeIdentityVersion: String by project
val mavenTestUtilsVersion: String by project

aepLibrary {
    namespace = "com.adobe.marketing.mobile.edge"
    enableSpotless = true
    enableSpotlessPrettierForJava = true
    enableDokkaDoc = true

    publishing {
        gitRepoName = "aepsdk-edge-android"
        addCoreDependency(mavenCoreVersion)
        addEdgeIdentityDependency(mavenEdgeIdentityVersion)
    }
}

android {

    sourceSets {
        getByName("main").java.srcDirs(
            "src/main/java",
            "../../core/code/core/src/main/java",
            "../../core/code/core/src/phone/java"
        )
    }
}

dependencies {
//    implementation("com.github.yangyansong-adbe:aepsdk-core-android:use_coroutines_2_IO_dispatcher-SNAPSHOT")
    implementation("androidx.lifecycle:lifecycle-process:2.0.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.3.1")
    implementation("androidx.compose.runtime:runtime:1.4.3")
    implementation("androidx.compose.material:material:1.4.3")
    implementation("androidx.compose.animation:animation:1.4.3")
    implementation("androidx.activity:activity-compose:1.5.0")
    implementation("com.adobe.marketing.mobile:edgeidentity:$mavenEdgeIdentityVersion"){
        exclude("com.adobe.marketing.mobile", "core")
    }


    testImplementation("com.github.adobe:aepsdk-testutils-android:$mavenTestUtilsVersion") {
        exclude("com.adobe.marketing.mobile", "core")
    }

    androidTestImplementation("com.github.adobe:aepsdk-testutils-android:$mavenTestUtilsVersion") {
        exclude("com.adobe.marketing.mobile", "core")
    }
    androidTestImplementation("com.adobe.marketing.mobile:edgeconsent:$mavenEdgeConsentVersion") {
        exclude(group = "com.adobe.marketing.mobile", module = "edge")
        exclude("com.adobe.marketing.mobile", "core")
    }
}