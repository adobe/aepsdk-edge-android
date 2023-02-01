/*
  Copyright 2021 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/

package com.adobe.marketing.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.junit.Test;

public class EdgeExtensionVersionTest {

	private static final String GRADLE_PROPERTIES_PATH = "../gradle.properties";
	private static final String PROPERTY_MODULE_VERSION = "moduleVersion";

	@Test
	public void extensionVersion_verifyModuleVersionInPropertiesFile_asEqual() {
		Properties properties = loadProperties(GRADLE_PROPERTIES_PATH);

		assertNotNull(Edge.extensionVersion());
		assertFalse(Edge.extensionVersion().isEmpty());

		String moduleVersion = properties.getProperty(PROPERTY_MODULE_VERSION);
		assertNotNull(moduleVersion);
		assertFalse(moduleVersion.isEmpty());

		assertEquals(
			String.format(
				"Expected version to match in gradle.properties (%s) and extensionVersion API (%s)",
				moduleVersion,
				Edge.extensionVersion()
			),
			moduleVersion,
			Edge.extensionVersion()
		);
	}

	private Properties loadProperties(final String filepath) {
		Properties properties = new Properties();
		InputStream input = null;

		try {
			input = new FileInputStream(filepath);

			properties.load(input);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		return properties;
	}
}
