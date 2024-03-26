# Adobe Experience Platform Edge Network extension

The Adobe Experience Platform Edge Network mobile extension allows you to send data to the Experience Platform Edge Network from a mobile application.

## Configure the Edge Network extension in Data Collection UI
1. Log into [Adobe Experience Platform Data Collection](https://experience.adobe.com/data-collection).
2. From **Tags**, use a desired existing mobile property or create a new one.
3. In your mobile property, select the **Extensions** tab.
4. On the **Catalog** tab, locate or search for the following extensions, and select **Install**:
   1. Adobe Experience Platform Edge Network
      * Select the **Datastream** you would like to use per environment. Read more about [datastreams](#datastreams) below.
      * Set up the **Domain configuration** by either using the automatically populated domin, or a first party domain mapped to an Adobe-provisioned Edge Network domain. For more information, see [domain configuration](#domain-configuration) below.
   2. Identity (no configuration is required)
5. Set your desired default consent level.
6. Select **Save**.
7. Follow the [publishing process](https://developer.adobe.com/client-sdks/documentation/getting-started/create-a-mobile-property/#publish-the-configuration) to update the SDK configuration.

### Datastreams

If no datastream was previously created, see [Configure datastreams](https://developer.adobe.com/client-sdks/documentation/getting-started/configure-datastreams/) before moving to the next step.

Select the desired datastream per each environment from the corresponding drop-down lists. If your organization uses multiple sandboxes, select the **Sandbox** first, then select the **Datastream** for each environment.

The datastream used by the client-side implementation is one of the following:

- the _Production_ environment configuration when the Data Collection Tags library is published to production (in the **Published** column in the publishing flow).
- the _Staging_ environment configuration when the Data Collection Tags library is published to staging (in the **Submitted** column in the publishing flow).
- the _Development_ environment configuration when the Data Collection Tags library is in development (in the **Development** column in the publishing flow).

### Domain configuration

The value under the **Edge Network domain** field is used for requests to Adobe Experience Platform Edge Network and it usually follows the format `<company>.data.adobedc.net`, where `<company>` is the unique namespace associated to your Adobe organization.

If you have a first-party domain mapped to the Adobe-provisioned Edge Network domain, you can enter it here. For more details about how to configure or maintain a first-party domain, see [Adobe-Managed Certificate Program](https://experienceleague.adobe.com/docs/core-services/interface/administration/ec-cookies/cookies-first-party.html?lang=en#adobe-managed-certificate-program).

> **Note**  
> The domain name is expected to be just the domain without any protocol or trailing slashes. If no domain is provided, by default the `edge.adobedc.net` domain is used.

## Add the Edge Network extension to your app

The Edge Network extension depends on the following extensions:
* [Mobile Core](https://github.com/adobe/aepsdk-core-android)
* [Identity for Edge Network](https://github.com/adobe/aepsdk-edgeidentity-android) (peer dependency for the Edge Network extension)

1. Add the Mobile Core, Identity for Edge Network, and Edge Network extensions to your project using the app's Gradle file:

### Kotlin

```kotlin
implementation(platform("com.adobe.marketing.mobile:sdk-bom:3.+"))
implementation("com.adobe.marketing.mobile:core")
implementation("com.adobe.marketing.mobile:edge")
implementation("com.adobe.marketing.mobile:edgeidentity")
```

### Groovy

```groovy
implementation platform('com.adobe.marketing.mobile:sdk-bom:3.+')
implementation 'com.adobe.marketing.mobile:core'
implementation 'com.adobe.marketing.mobile:edge'
implementation 'com.adobe.marketing.mobile:edgeidentity'
```

> **Warning**  
> Using dynamic dependency versions is not recommended for production apps. Refer to this [page](https://github.com/adobe/aepsdk-core-android/blob/main/Documentation/MobileCore/gradle-dependencies.md) for managing Gradle dependencies.

2. Import the libraries:
#### Java
```java
import com.adobe.marketing.mobile.MobileCore;
import com.adobe.marketing.mobile.Edge;
import com.adobe.marketing.mobile.edge.identity.Identity;
```

#### Kotlin
```kotlin
import com.adobe.marketing.mobile.MobileCore
import com.adobe.marketing.mobile.Edge
import com.adobe.marketing.mobile.edge.identity.Identity
```

3. Register the Edge extensions with Mobile Core:

#### Java
```java
public class MainApp extends Application {

  private final String ENVIRONMENT_FILE_ID = "YOUR_APP_ENVIRONMENT_ID";

	@Override
	public void onCreate() {
		super.onCreate();

		MobileCore.setApplication(this);
		MobileCore.configureWithAppID(ENVIRONMENT_FILE_ID);

		MobileCore.registerExtensions(
			Arrays.asList(Edge.EXTENSION, Identity.EXTENSION),
			o -> Log.d("MainApp", "Adobe Experience Platform Mobile SDK was initialized.")
		);
	}
}
```

#### Kotlin
```kotlin
class MainApp : Application() {

  private var ENVIRONMENT_FILE_ID: String = "YOUR_APP_ENVIRONMENT_ID"

    override fun onCreate() {
        super.onCreate()

        MobileCore.setApplication(this)
        MobileCore.configureWithAppID(ENVIRONMENT_FILE_ID)

        MobileCore.registerExtensions(
          listOf(Edge.EXTENSION, Identity.EXTENSION)
        ) {
          Log.d("MainApp", "Adobe Experience Platform Mobile SDK was initialized")
        }
    }

}
```