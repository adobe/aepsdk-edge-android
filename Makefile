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

TEST-APP-FOLDER-NAME = app

init:
	git config core.hooksPath .githooks

clean:
	(./code/gradlew -p code clean)

format:
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) spotlessApply)
	(./code/gradlew -p code/$(TEST-APP-FOLDER-NAME) spotlessApply)

format-check:
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) spotlessCheck)
	(./code/gradlew -p code/$(TEST-APP-FOLDER-NAME) spotlessCheck)

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
	(./code/gradlew -p code/upstream-integration-tests connectedDebugAndroidTest -PEDGE_ENVIRONMENT=$(EDGE_ENVIRONMENT) -PEDGE_LOCATION_HINT=$(EDGE_LOCATION_HINT))

javadoc:
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) javadocJar)

assemble-phone:
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) assemblePhone)

assemble-phone-debug:
	(./code/gradlew -p code/${EXTENSION-LIBRARY-FOLDER-NAME}  assemblePhoneDebug)

assemble-phone-release:
	(./code/gradlew -p code/${EXTENSION-LIBRARY-FOLDER-NAME}  assemblePhoneRelease)

assemble-app:
	(./code/gradlew -p code/$(TEST-APP-FOLDER-NAME) assemble)

publish-staging: clean assemble-phone-release
	(./code/gradlew -p code/${EXTENSION-LIBRARY-FOLDER-NAME} publishReleasePublicationToSonatypeRepository)

publish-main: clean assemble-phone-release
	(./code/gradlew -p code/${EXTENSION-LIBRARY-FOLDER-NAME} publishReleasePublicationToSonatypeRepository -Prelease)

# usage: update-version VERSION=9.9.9 CORE-VERSION=8.8.8
# usage: update-version VERSION=9.9.9
update-version:
	@echo "Updating version to $(VERSION), Core version to $(CORE-VERSION)"
	sed -i '' "s/[0-9]*\.[0-9]*\.[0-9]/$(VERSION)/g" ./code/edge/src/main/java/com/adobe/marketing/mobile/EdgeConstants.java
	sed -i '' "s/\(moduleVersion=\)[0-9]*\.[0-9]*\.[0-9]/\1$(VERSION)/g" ./code/gradle.properties
	@if [ -z "$(CORE-VERSION)" ]; then \
		echo "CORE-VERSION was not provided, skipping"; \
	else \
		sed -i '' "s/\(mavenCoreVersion=\)[0-9]*\.[0-9]*\.[0-9]/\1$(CORE-VERSION)/g" ./code/gradle.properties; \
	fi
