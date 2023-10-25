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
BUILD-ASSEMBLE-LOCATION = ./ci/assemble
ROOT_DIR=$(shell git rev-parse --show-toplevel)

PROJECT_NAME = $(shell cat $(ROOT_DIR)/code/gradle.properties | grep "moduleProjectName" | cut -d'=' -f2)
AAR_NAME = $(shell cat $(ROOT_DIR)/code/gradle.properties | grep "moduleAARName" | cut -d'=' -f2)
MODULE_NAME = $(shell cat $(ROOT_DIR)/code/gradle.properties | grep "moduleName" | cut -d'=' -f2)
LIB_VERSION = $(shell cat $(ROOT_DIR)/code/gradle.properties | grep "moduleVersion" | cut -d'=' -f2)
SOURCE_FILE_DIR =  $(ROOT_DIR)/code/$(PROJECT_NAME)
AAR_FILE_DIR =  $(ROOT_DIR)/code/$(PROJECT_NAME)/build/outputs/aar

init:
	git config core.hooksPath .githooks

clean:
	(rm -rf ci)
	(rm -rf $(AAR_FILE_DIR))
	(./code/gradlew -p code clean)

create-ci: clean
	(mkdir -p ci)

format:
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) spotlessApply)
	(./code/gradlew -p code/$(TEST-APP-FOLDER-NAME) spotlessApply)

format-check:
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) spotlessCheck)
	(./code/gradlew -p code/$(TEST-APP-FOLDER-NAME) spotlessCheck)

format-license:
	(./code/gradlew -p code licenseFormat)

lint-check:
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) lint)


ci-build: create-ci
	(mkdir -p ci/assemble)

	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) assemblePhone)
	(mv $(AAR_FILE_DIR)/$(EXTENSION-LIBRARY-FOLDER-NAME)-phone-release.aar  $(AAR_FILE_DIR)/$(MODULE_NAME)-release-$(LIB_VERSION).aar)
	(cp -r ./code/$(EXTENSION-LIBRARY-FOLDER-NAME)/build $(BUILD-ASSEMBLE-LOCATION))

ci-build-app:
	(./code/gradlew -p code/$(TEST-APP-FOLDER-NAME) assemble)

ci-unit-test: create-ci
	(mkdir -p ci/unit-test)
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) platformUnitTestJacocoReport)
	(cp -r ./code/$(EXTENSION-LIBRARY-FOLDER-NAME)/build ./ci/unit-test/)

ci-functional-test: create-ci
	(mkdir -p ci/functional-test)
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) uninstallPhoneDebugAndroidTest)
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) connectedPhoneDebugAndroidTest platformFunctionalTestJacocoReport)
	(cp -r ./code/$(EXTENSION-LIBRARY-FOLDER-NAME)/build ./ci/functional-test)

ci-upstream-integration-test:
	(./code/gradlew -p code/upstream-integration-tests uninstallDebugAndroidTest)
	(./code/gradlew -p code/upstream-integration-tests connectedDebugAndroidTest -PEDGE_ENVIRONMENT=$(EDGE_ENVIRONMENT) -PEDGE_LOCATION_HINT=$(EDGE_LOCATION_HINT))

ci-javadoc: create-ci
	(mkdir -p ci/javadoc)
	(./code/gradlew -p code/$(EXTENSION-LIBRARY-FOLDER-NAME) javadocJar)
	(cp -r ./code/$(EXTENSION-LIBRARY-FOLDER-NAME)/build ./ci/javadoc)

ci-generate-library-debug:
		(./code/gradlew -p code/${EXTENSION-LIBRARY-FOLDER-NAME}  assemblePhoneDebug)

ci-generate-library-release:
		(./code/gradlew -p code/${EXTENSION-LIBRARY-FOLDER-NAME}  assemblePhoneRelease)

build-release:
	(./code/gradlew -p code/${EXTENSION-LIBRARY-FOLDER-NAME} clean lint assemblePhoneRelease)

ci-publish-staging: clean build-release
	(./code/gradlew -p code/${EXTENSION-LIBRARY-FOLDER-NAME} publishReleasePublicationToSonatypeRepository)

ci-publish-main: clean build-release
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
