#
# Copyright 2019 Adobe. All rights reserved.
# This file is licensed to you under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License. You may obtain a copy
# of the License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed under
# the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
# OF ANY KIND, either express or implied. See the License for the specific language
# governing permissions and limitations under the License.
#

EXTENSION-LIBRARY-FOLDER-NAME = edge

TEST-APP-FOLDER-NAME-JAVA = app
TEST-APP-FOLDER-NAME-KOTLIN = app-kotlin

init:
	git config core.hooksPath .githooks

clean:
	(./code/gradlew -p code clean)

format:
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) spotlessApply)
	(./code/gradlew -p code/$(TEST-APP-FOLDER-NAME-JAVA) spotlessApply)
	(./code/gradlew -p code/$(TEST-APP-FOLDER-NAME-KOTLIN) spotlessApply)

checkformat:
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) spotlessCheck)
	(./code/gradlew -p code/$(TEST-APP-FOLDER-NAME-JAVA) spotlessCheck)
	(./code/gradlew -p code/$(TEST-APP-FOLDER-NAME-KOTLIN) spotlessCheck)

format-license:
	(./code/gradlew -p code licenseFormat)

lint:
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) lint)

unit-test:
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) testPhoneDebugUnitTest)

unit-test-coverage:
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) createPhoneDebugUnitTestCoverageReport)

functional-test:
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) uninstallPhoneDebugAndroidTest)
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) connectedPhoneDebugAndroidTest)

functional-test-coverage:
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) uninstallPhoneDebugAndroidTest)
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) createPhoneDebugAndroidTestCoverageReport)

upstream-integration-test:
	(./code/gradlew -p code/upstream-integration-tests uninstallDebugAndroidTest)
	(./code/gradlew -p code/upstream-integration-tests connectedDebugAndroidTest -PTAGS_MOBILE_PROPERTY_IDD=$(TAGS_MOBILE_PROPERTY_ID) -PEDGE_LOCATION_HINT=$(EDGE_LOCATION_HINT)) --info

javadoc:
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) javadocJar)

assemble-phone:
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) assemblePhone)

assemble-phone-debug:
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME)  assemblePhoneDebug)

assemble-phone-release:
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME)  assemblePhoneRelease)

assemble-app:
	(./code/gradlew -p code/$(TEST-APP-FOLDER-NAME-JAVA) assemble)
	(./code/gradlew -p code/$(TEST-APP-FOLDER-NAME-KOTLIN) assemble)

ci-publish-maven-local-jitpack:
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) publishReleasePublicationToMavenLocal -Pjitpack  -x signReleasePublication)

ci-publish-staging:
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) publishReleasePublicationToSonatypeRepository)

ci-publish:
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) publishReleasePublicationToSonatypeRepository -Prelease)
