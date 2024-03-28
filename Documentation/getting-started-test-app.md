# Getting started with the test app

## Data Collection mobile property prerequisites

The test app needs to be configured with a Data Collection mobile property with the following extensions before it can be used:

* [Mobile Core](https://github.com/adobe/aepsdk-core-android) (installed by default)
* Edge Network (this extension)
* [Consent for Edge Network](https://github.com/adobe/aepsdk-edgeconsent-android)
* [Identity for Edge Network](https://github.com/adobe/aepsdk-edgeidentity-android)
* [Assurance](https://github.com/adobe/aepsdk-assurance-android)

See [Configure the Edge Network extension in Data Collection UI](getting-started.md#configure-the-edge-network-extension-in-data-collection-ui) for instructions on setting up a mobile property.

## Run test application

1. In the test app, set your `ENVIRONMENT_FILE_ID` in `TestApplication.kt`.
2. Select the `app-kotlin` runnable with the desired emulator and run the program.

## Validation with Assurance

Configure a new Assurance session by setting the Base URL to `testapp://main` and launch Assurance in the test app by running the following command in your terminal:

```bash
$ adb shell am start -W -a  android.intent.action.VIEW -d "testapp://main?adb_validation_sessionid=ADD_YOUR_SESSION_ID_HERE" com.adobe.marketing.mobile.edge.testapp.kotlin
```

> **Note**  
> Replace `ADD_YOUR_SESSION_ID_HERE` with your Assurance session identifier.

Once the connection is established and the events list starts getting populated, you can filter the Edge extension events by typing `Edge` in the `Search Events` search box.

For mode debugging tips, see also the [Event Transactions view](https://experienceleague.adobe.com/en/docs/experience-platform/assurance/view/event-transactions) for Assurance.
