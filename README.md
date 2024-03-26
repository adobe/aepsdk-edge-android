# Adobe Experience Platform Edge Network Mobile Extension

[![Maven Central](https://img.shields.io/maven-metadata/v.svg?label=edge&logo=android&logoColor=white&metadataUrl=https%3A%2F%2Frepo1.maven.org%2Fmaven2%2Fcom%2Fadobe%2Fmarketing%2Fmobile%2Fedge%2Fmaven-metadata.xml)](https://mvnrepository.com/artifact/com.adobe.marketing.mobile/edge)

## About this project

The Adobe Experience Platform Edge Network mobile extension allows you to send data to the Experience Platform Edge Network from a mobile application. This extension allows you to implement Adobe Experience Cloud capabilities in a more robust way, serve multiple Adobe solutions though one network call, and simultaneously forward this information to Adobe Experience Platform.

The Edge Network mobile extension is an extension for the [Adobe Experience Platform Mobile SDK](https://developer.adobe.com/client-sdks) and requires the Mobile Core and Services extensions for event handling, as well as the Identity for Edge Network extension for handling identities, such as `ECID`.

### Installation

Integrate the Edge Network mobile extension into your app by following the [getting started guide](Documentation/getting-started.md).

### Development

#### Open the project

To open and run the project, open the `code/settings.gradle` file in Android Studio.

#### Run the test application

To configure and run the test app for this project, follow the [getting started guide for the test app](Documentation/getting-started-test-app.md).

#### Code format

This project uses the code formatting tools [Spotless](https://github.com/diffplug/spotless/tree/main/plugin-gradle) with [Prettier](https://prettier.io/). Formatting is applied when the project is built from Gradle and is checked when changes are submitted to the CI build system.

Prettier requires [Node version](https://nodejs.org/en/download/releases/) 10+
To enable the Git pre-commit hook to apply code formatting on each commit, run the following to update the project's git config `core.hooksPath`:
```
make init
```

## Related Projects

| Project                                                                                            | Description                                                  |
| -------------------------------------------------------------------------------------------------- | ------------------------------------------------------------ |
| [Core extensions](https://github.com/adobe/aepsdk-core-android)                                    | The Mobile Core represents the foundation of the Adobe Experience Platform Mobile SDK. |
| [Consent for Edge Network](https://github.com/adobe/aepsdk-edgeconsent-android)          | The Consent for Edge Network extension enables consent preferences collection from your mobile app when using the Experience Platform Mobile SDK and the Edge Network extension. |
| [Lifecycle for Edge Network](https://github.com/adobe/aepsdk-core-android)               | The Lifecycle for Edge Network extension helps collect application Lifecycle metrics and any additional context data provided by the application developer when using the Mobile SDK and the Edge Network extension. |
| [Identity for Edge Network](https://github.com/adobe/aepsdk-edgeidentity-android)        | The Identity for Edge Network extension enables identity management from a mobile app when using the Edge Network extension. |
| [Assurance extension](https://github.com/adobe/aepsdk-assurance-android)           | The Assurance extension enables validation workflows for your Mobile SDK implementation. |

## Documentation

Information about Adobe Experience Platform Edge Network's implementation, API usage, and architecture can be found in the [Documentation](Documentation) directory.

Learn more about Edge Network and all other Mobile SDK extensions in the official [Adobe Experience Platform Mobile SDK documentation](https://developer.adobe.com/client-sdks/documentation/edge-network/).

## Contributing

Contributions are welcomed! Read the [Contributing Guide](./.github/CONTRIBUTING.md) for more information.

## Licensing

This project is licensed under the Apache V2 License. See [LICENSE](LICENSE) for more information.
