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
val mavenEdgeIdentityVersion: String by project

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

dependencies {
    implementation("com.adobe.marketing.mobile:core:$mavenCoreVersion")
    implementation("com.adobe.marketing.mobile:edgeidentity:$mavenEdgeIdentityVersion")

    testImplementation("com.github.adobe:aepsdk-testutils-android:14d15d8290")

    androidTestImplementation("com.github.adobe:aepsdk-testutils-android:14d15d8290")
    androidTestImplementation("com.adobe.marketing.mobile:edgeconsent:3.0.0-SNAPSHOT")
    {
        exclude(group = "com.adobe.marketing.mobile", module = "edge")
    }
}