# Adobe Experience Platform Edge Network Mobile Extension


## About this project

The Adobe Experience Platform Edge Network mobile extension allows you to send data to the Adobe  Edge Network from a mobile application. This extension allows you to implement Adobe Experience Cloud capabilities in a more robust way, serve multiple Adobe solutions though one network call, and simultaneously forward this information to the Adobe Experience Platform.

The AEP Edge Network mobile extension is an extension for the [Adobe Experience Platform SDK](https://aep-sdks.gitbook.io) and requires the `AEPCore` and `AEPServices` extensions for event handling, as well as the `AEPEdgeIdentity` extension for retrieving the identities, such as ECID.

To learn more about this extension, read the [Adobe Experience Platform Edge Network](https://aep-sdks.gitbook.io/docs/foundation-extensions/experience-platform-extension) documentation.

### Installation

Integrate the Edge Network extension into your app by including the following in your gradle file's `dependencies`:

```gradle
implementation 'com.adobe.marketing.mobile:edgeidentity:1.+'
implementation 'com.adobe.marketing.mobile:edge:1.+'
implementation 'com.adobe.marketing.mobile:core:1.+'
```

### Development

**Open the project**

To open and run the project, open the `code/settings.gradle` file in Android Studio.

**Data Collection mobile property prerequisites**

The test app needs to be configured with the following edge extensions before it can be used:
- Mobile Core (installed by default)
- [Edge](https://aep-sdks.gitbook.io/docs/foundation-extensions/experience-platform-extension)
- [Edge Identity](https://aep-sdks.gitbook.io/docs/foundation-extensions/identity-for-edge-network)
- [Edge Consent](https://aep-sdks.gitbook.io/docs/foundation-extensions/consent-for-edge-network)
- [Assurance](https://aep-sdks.gitbook.io/docs/foundation-extensions/adobe-experience-platform-assurance)

**Run test application**

1. In the test app, set your `ENVIRONMENT_FILE_ID` in `TestApplication.java`.
2. Select the `app` runnable with the desired emulator and run the program.

**Inspect the events with Assurance**

Configure a new Assurance session by setting the Base URL to `testapp://main` and launch Assurance in the test app by running the following command in your terminal:

```bash
$ adb shell am start -W -a  android.intent.action.VIEW -d "testapp://main?adb_validation_sessionid=ADD_YOUR_SESSION_ID_HERE" com.adobe.marketing.edgetestapp
```

Note: replace ADD_YOUR_SESSION_ID_HERE with your Assurance session identifier.

Once the connection is established and the events list starts getting populated, you can filter the Edge extension events. See full list of available events [here](https://aep-sdks.gitbook.io/docs/foundation-extensions/experience-platform-extension/validation).

## Related Projects

| Project                                                                              | Description                                                  |
| ------------------------------------------------------------------------------------ | ------------------------------------------------------------ |
| [AEPCore Extensions](https://github.com/adobe/aepsdk-core-android)                       | The AEPCore and AEPServices represent the foundation of the Adobe Experience Platform SDK. |
| [AEPConsent Extension](https://github.com/adobe/aepsdk-edgeconsent-android)              | The AEPConsent extension enables consent preferences collection from your mobile app when using the AEP Mobile SDK and the Edge Network extension. |
| [AEPLifecycle Extension](https://github.com/adobe/aepsdk-core-android)                   | The AEPLifecycle extension helps collect application Lifecycle metrics and any additional context data provided by the application developer when using AEP SDK and the AEP Edge Network extension. |
| [AEPEdgeIdentity Extension](https://github.com/adobe/aepsdk-edgeidentity-android)        | The AEPEdgeIdentity extension enables handling of user identity data from a mobile app when using AEP SDK and the AEP Edge Network extension. |
| [AEP SDK Sample App for iOS](https://github.com/adobe/aepsdk-sample-app-ios)         | Contains iOS sample apps for the AEP SDK. Apps are provided for both Objective-C and Swift implementations. |
| [AEP SDK Sample App for Android](https://github.com/adobe/aepsdk-sample-app-android) | Contains Android sample app for the AEP SDK.                 |

## Documentation

Additional documentation for usage and SDK architecture can be found under the [Documentation](Documentation) directory.

## Contributing

Contributions are welcomed! Read the [Contributing Guide](./.github/CONTRIBUTING.md) for more information.

## Licensing

This project is licensed under the Apache V2 License. See [LICENSE](LICENSE) for more information.
