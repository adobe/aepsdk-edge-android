# Adobe Experience Platform Edge Network Mobile Extension


## About this project

The Adobe Experience Platform Edge Network mobile extension allows you to send data to the Adobe Edge Network from a mobile application. This extension allows you to implement Adobe Experience Cloud capabilities in a more robust way, serve multiple Adobe solutions though one network call, and simultaneously forward this information to the Adobe Experience Platform.

The Edge Network mobile extension is an extension for the Adobe Experience Platform SDK and requires the Mobile Core and Services extensions for event handling, as well as the Identity for Edge Network extension for handling identities, such as `ECID`.

## Documentation

Information about Adobe Experience Platform Edge Network's implementation, API usage, and architecture can be found in the [Documentation](Documentation) directory.

Learn more about Edge Network and all other Mobile SDK extensions in the official [Adobe Experience Platform Mobile SDK documentation](https://developer.adobe.com/client-sdks/documentation/edge-network/).

## Getting started
### Installation

Integrate the Edge Network extension into your app by including the following in your gradle file's `dependencies`:

```gradle
implementation 'com.adobe.marketing.mobile:core:2.+'
implementation 'com.adobe.marketing.mobile:edge:2.+'
implementation 'com.adobe.marketing.mobile:edgeidentity:2.+'
```
> **Note**  
> See the [current version list](https://developer.adobe.com/client-sdks/documentation/current-sdk-versions) for the latest extension versions to use.

### Development

**Open the project**

To open and run the project, open the `code/settings.gradle` file in Android Studio.

#### Adobe Experience Platform mobile property prerequisites

A mobile property in Adobe Experience Platform is required for the Edge extensions to work properly. For instructions on how to set one up, see the [Edge extension tutorial](Documentation/Tutorials/edge-send-event-tutorial.md).

**Run test application**

1. In the test app, set your `ENVIRONMENT_FILE_ID` in `TestApplication.java`.
   * This ID is available from a mobile property described in the [section above](#adobe-experience-platform-mobile-property-prerequisites).
2. Select the `app` runnable with the desired emulator and run the program.

**Inspect the events with Assurance**

Configure a new Assurance session by setting the Base URL to `testapp://main` and launch Assurance in the test app by running the following command in your terminal:

```bash
$ adb shell am start -W -a  android.intent.action.VIEW -d "testapp://main?adb_validation_sessionid=ADD_YOUR_SESSION_ID_HERE" com.adobe.marketing.edgetestapp
```

Note: replace `ADD_YOUR_SESSION_ID_HERE` with your actual Assurance session identifier.

Once the connection is established and the events list starts getting populated, you can filter the Edge extension events. See the full list of available events [here](https://developer.adobe.com/client-sdks/documentation/edge-network/validation/).

## Related Projects

| Project                                                                              | Description                                                  |
| ------------------------------------------------------------------------------------ | ------------------------------------------------------------ |
| [Core extensions](https://github.com/adobe/aepsdk-core-android)                       | The Mobile Core represents the foundation of Adobe Experience Platform Mobile SDK. |
| [Consent for Edge Network extension](https://github.com/adobe/aepsdk-edgeconsent-android)              | The Consent for Edge Network extension enables consent preferences collection from your mobile app when using Adobe Experience Platform Mobile SDK and the Edge Network extension. |
| [Lifecycle for Edge Network extension](https://github.com/adobe/aepsdk-core-android)                   | The Lifecycle for Edge Network extension helps collect application Lifecycle metrics and any additional context data provided by the application developer when using Adobe Experience Platform Mobile SDK and the Edge Network extension. |
| [Identity for Edge Network extension](https://github.com/adobe/aepsdk-edgeidentity-android)        | The Identity for Edge Network extension enables handling of user identity data from a mobile app when using Adobe Experience Platform Mobile SDK and the Edge Network extension. |
| [Adobe Experience Platform Android sample app](https://github.com/adobe/aepsdk-sample-app-android) | Contains a fully implemented Android sample app using the Adobe Experience Platform Mobile SDK.                 |

## Contributing

Contributions are welcomed! Read the [Contributing Guide](./.github/CONTRIBUTING.md) for more information.

## Licensing

This project is licensed under the Apache V2 License. See [LICENSE](LICENSE) for more information.
