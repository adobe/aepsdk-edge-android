# Adobe Experience Platform Edge Network Extension Android API Usage

## Prerequisites

Refer to the [Getting Started Guide](getting-started.md).

## API reference

- [extensionVersion](#extensionversion)
- [getLocationHint](#getLocationHint)
- [registerExtension](#registerextension)
- [resetIdentities](#resetidentities)
- [sendEvent](#sendevent)
- [setLocationHint](#setlocationhint)
- [Public Classes](#public-classes)
   - [XDM Schema](#xdm-schema)
   - [EdgeEventHandle](#edgeeventhandle)
   - [ExperienceEvent](#experienceevent)

------

### extensionVersion

The extensionVersion() API returns the version of the Edge Network extension.

#### Java

##### Syntax
```java
public static String extensionVersion()
```

##### Example
```java
String extensionVersion = Edge.extensionVersion();
```

#### Kotlin

##### Syntax
```kotlin
fun extensionVersion(): String
```

##### Examples
```kotlin
val extensionVersion = EdgeBridge.extensionVersion()
```

------

### getLocationHint

Gets the Edge Network location hint used in requests to the Edge Network. The location hint may be used when building the URL for Edge Network requests to hint at the server cluster to use.

#### Java

##### Syntax
```java
public static void getLocationHint(final AdobeCallback<String> callback)
```
* `callback` is invoked with the location hint. The location hint value may be `null` if the location hint expired or was not set. The callback may be invoked on a different thread. If `AdobeCallbackWithError` is provided, the default timeout is 1000ms and the `fail` method is called if the operation times out or an unexpected error occurs.

##### Example
```java
Edge.getLocationHint(new AdobeCallbackWithError<String>() {
    @Override
    public void call(final String hint) {
        // Handle the hint here
    }

    @Override
    public void fail(AdobeError adobeError) {
        // Handle the error here
    }
});
```

#### Kotlin

##### Syntax
```kotlin
fun getLocationHint(callback: AdobeCallback<String>)
```

##### Example
```kotlin
Edge.getLocationHint(object: AdobeCallbackWithError<String> {
    override fun call(hint: String) {
      // Handle the hint here
    }
    override fun fail(error: AdobeError?) {
      // Handle the error here
    }
})
```

------

### registerExtension

Registers the Edge Network extension with the Mobile Core extension.

> **Warning**  
> Deprecated as of 2.0.0. Use the [MobileCore.registerExtensions API](https://github.com/adobe/aepsdk-core-android/blob/main/Documentation/MobileCore/api-reference.md) instead.


#### Java

##### Syntax
```java
public static void registerExtension()
```

##### Example
```java
import com.adobe.marketing.mobile.Edge;

...
Edge.registerExtension();
```

#### Kotlin

##### Syntax
```kotlin
fun registerExtension()
```
##### Example
```kotlin
import com.adobe.marketing.mobile.Edge

...

Edge.registerExtension()
```

------

### resetIdentities

Resets current state of the Edge extension and clears previously cached content related to current identity, if any.

See [MobileCore.resetIdentities](https://github.com/adobe/aepsdk-core-android/blob/main/Documentation/MobileCore/api-reference.md) for more details.

------

### sendEvent

Sends an Experience event to the Adobe Experience Platform Edge Network.

#### Java

##### Syntax
```java
public static void sendEvent(final ExperienceEvent experienceEvent, final EdgeCallback callback);
```

* `experienceEvent` is the XDM [Experience Event](#experienceevent) sent to the Edge Network.
* `callback` is an optional callback invoked when the request is complete and returns the associated [EdgeEventHandle](#edgeeventhandle)(s) received from the Edge Network. It may be invoked on a different thread.

##### Example
```java
// Create experience event from Map
Map<String, Object> xdmData = new HashMap<>();
xdmData.put("eventType", "SampleXDMEvent");
xdmData.put("sample", "data");
        
ExperienceEvent experienceEvent = new ExperienceEvent.Builder()
  .setXdmSchema(xdmData)
  .build();
```
```java
// Example 1 - send the experience event without handling the Edge Network response
Edge.sendEvent(experienceEvent, null);
```
```java
// Example 2 - send the experience event and handle the Edge Network response onComplete
Edge.sendEvent(experienceEvent, new EdgeCallback() {
  @Override
  public void onComplete(final List<EdgeEventHandle> handles) {
    // Handle the Edge Network response 
  }
});
```
#### Kotlin

##### Syntax
```kotlin
fun sendEvent(experienceEvent: ExperienceEvent, callback: EdgeCallback?)
```

#### Example
```kotlin
// Create experience event from Map
val xdmData = mutableMapOf<String, Any>()
xdmData["eventType"] = "SampleXDMEvent"
xdmData["sample"] = "data"

val experienceEvent = ExperienceEvent.Builder()
  .setXdmSchema(xdmData)
  .build()
```
```kotlin
// Example 1 - send the experience event without handling the Edge Network response
Edge.sendEvent(experienceEvent, null)
```
```java
// Example 2 - send the experience event and handle the Edge Network response onComplete
Edge.sendEvent(experienceEvent) {
  // Handle the Edge Network response 
}
```

------

### setLocationHint

Sets the Edge Network location hint used in requests to the Edge Network. Passing `null` or an empty string clears the existing location hint. Edge Network responses may overwrite the location hint to a new value when necessary to manage network traffic.

> **Warning**  
> Use caution when setting the location hint. Only use location hints for the **EdgeNetwork** scope. An incorrect location hint value will cause all Edge Network requests to fail with a `404` response code.

#### Java

##### Syntax
```java
public static void setLocationHint(final String hint)
```
- `hint` the Edge Network location hint to use when connecting to the Edge Network.

##### Example
```java
Edge.setLocationHint(hint);
```

#### Kotlin

##### Syntax
```kotlin
fun setLocationHint(hint: String?)
```

##### Example
```kotlin
Edge.setLocationHint(hint)
```

------

## Public Classes

### XDM Schema

The Edge extension provides the `Schema` and `Property` interfaces that can be used to define the classes associated with your XDM schema in Adobe Experience Platform.

#### Java
```java
/**
 * The interface that represents an Experience XDM event data schema.
 */
public interface Schema {

    /**
     * Returns the version of this schema as defined in the Adobe Experience Platform.
     * @return the version of this schema.
     */
    String getSchemaVersion();

    /**
     * Returns the identifier for this schema as defined in the Adobe Experience Platform.
     * The identifier is a URI where this schema is defined.
     * @return the URI identifier for this schema.
     */
    String getSchemaIdentifier();

    /**
     * Returns the identifier for this dataset as defined in the Adobe Experience Platform.
     * @return the dataset ID
     */
    String getDatasetIdentifier();

    /**
     * Serialize this {@code Schema} object to a map with the same format as its XDM schema.
     * @return the XDM-formatted map of this {@code Schema} object.
     */
    Map<String, Object> serializeToXdm();
}
```

By implementing the `Property` interface, you can define complex properties for your XDM `Schema`. A complex property is defined as not being a primitive type, `String`, or `Date`.

#### Java
```java
public interface Property {

    /**
     * Serialize this {@code Property} object to a map with the same format as its XDM schema.
     * @return XDM-formatted map of this {@code Property} object.
     */
    Map<String, Object> serializeToXdm();
}
```

When defining your custom XDM schema(s), implement these interfaces to ensure that the Edge extension successfully serializes the provided data before sending it to Edge Network.

------

### EdgeEventHandle

The `EdgeEventHandle` is a response fragment from Edge Network for a sent XDM Experience Event. One event can receive none, one or multiple `EdgeEventHandle`(s) as response.
Use this class when calling the [sendEvent](#sendevent) API with `EdgeCallback`.

#### Java
```java
/**
 * The {@link EdgeEventHandle} is a response fragment from Adobe Experience Edge Service for a sent XDM Experience Event.
 * One event can receive none, one or multiple {@link EdgeEventHandle}(s) as response.
 */
public class EdgeEventHandle {
  /**
     * @return the payload type or null if not found in the {@link JSONObject} response
     */
  public String getType() {...}

  /**
     * @return the event payload values for this {@link EdgeEventHandle} or null if not found in the {@link JSONObject} response
     */
  public List<Map<String, Object>> getPayload() {...}
}
```

### ExperienceEvent

Experience Event is the event to be sent to Adobe Experience Platform Edge Network. The XDM data is required for any Experience Event being sent using the Edge extension.

#### Java
```java
public final class ExperienceEvent {

  public static class Builder {
    ...

    public Builder() {
      ...
    }

    /**
      * Sets free form data associated with this event to be passed to Adobe Experience Edge.
      *
      * @param data free form data, JSON like types are accepted
      * @return instance of current builder
      * @throws UnsupportedOperationException if this instance was already built
      */
    public Builder setData(final Map<String, Object> data) {...}

    /**
      * Solution specific XDM event data for this event.
      *
      * @param xdm {@link Schema} information
      * @return instance of current builder
      * @throws UnsupportedOperationException if this instance was already built
      */
    public Builder setXdmSchema(final Schema xdm) {...}

    /**
      * Solution specific XDM event data and dataset identifier for this event.
      *
      * @param xdm {@code Map<String, Object>} of raw XDM schema data
      * @param datasetIdentifier The Experience Platform dataset identifier where this event is sent.
      *                          If not provided, the default dataset defined in the configuration ID is used
      * @return instance of current builder
      * @throws UnsupportedOperationException if this instance was already built
      */
    public Builder setXdmSchema(final Map<String, Object> xdm, final String datasetIdentifier) {...}

    /**
      * Solution specific XDM event data for this event, passed as raw mapping of keys and
      * Object values.
      *
      * @param xdm {@code Map<String, Object>} of raw XDM schema data
      * @return instance of current builder
      * @throws UnsupportedOperationException if this instance was already built
      */
    public Builder setXdmSchema(final Map<String, Object> xdm) {...}

    /**
      * Builds and returns a new instance of {@code ExperienceEvent}.
      *
      * @return a new instance of {@code ExperienceEvent} or null if one of the required parameters is missing
      * @throws UnsupportedOperationException if this instance was already built
      */
    public ExperienceEvent build() {...}
  }

  public Map<String, Object> getData() {...}

  public Map<String, Object> getXdmSchema() {...} 
}  
```

#### Java

#### Examples
Example 1: Set both the XDM and freeform data of an `ExperienceEvent`.
```java
// Set freeform data of the Experience Event instance
Map<String, Object> xdmData = new HashMap<>();
xdmData.put("eventType", "SampleXDMEvent");
xdmData.put("sample", "data");

Map<String, Object> data = new HashMap<>();
data.put("free", "form");
data.put("data", "example");

ExperienceEvent experienceEvent = new ExperienceEvent.Builder()
  .setXdmSchema(xdmData)
  .setData(data)
  .build();
```
Example 2: Create an `ExperienceEvent` event instance using a class that implements the `Schema` interface.
```java
// Create Experience Event from XDM Schema implementations
public class XDMSchemaExample implements com.adobe.marketing.mobile.xdm.Schema {
  private String eventType;
  private String otherField;
      ...

  public String getEventType() {
    return this.eventType;
  }

  public void setEventType(final String newValue) {
    this.eventType = newValue;
  }

  public String getOtherField() {
    return this.otherField;
  }

  public void setOtherField(final String newValue) {
    this.otherField = newValue;
  }
}

// Create Experience Event from class implementing Schema
XDMSchemaExample xdmData = new XDMSchemaExample();
xdmData.setEventType("SampleXDMEvent");
xdmData.setOtherField("OtherFieldValue");

ExperienceEvent experienceEvent = new ExperienceEvent.Builder()
  .setXdmSchema(xdmData)
  .build();
```
Example 3: Set a custom destination Dataset ID when creating an `ExperienceEvent` instance.
```java
Map<String, Object> xdmData = new HashMap<>();
xdmData.put("eventType", "SampleXDMEvent");
xdmData.put("sample", "data");

// Set the destination Dataset identifier of the Experience Event
ExperienceEvent experienceEvent = new ExperienceEvent.Builder()
  .setXdmSchema(xdmData, "datasetIdExample")
  .build();
```

#### Kotlin

#### Examples
Example 1: Set both the XDM and freeform data of an `ExperienceEvent`.
```kotlin
// Set freeform data of the Experience Event instance
val xdmData = mutableMapOf<String, Any>()
xdmData["eventType"] = "SampleXDMEvent"
xdmData["sample"] = "data"

val data = mutableMapOf<String, Any>()
data["free"] = "form"
data["data"] = "example"

val experienceEvent = ExperienceEvent.Builder()
  .setXdmSchema(xdmData)
  .setData(data)
  .build()
```

Example 2: Create an `ExperienceEvent` event instance using a class that implements the `Schema` interface.
```kotlin
// Create Experience Event from XDM Schema implementations
class XDMSchemaExample : Schema {
    var eventType: String? = null
    var otherField: String? = null

    ...
}

// Create Experience Event from class implementing Schema
val xdmData = XDMSchemaExample()
xdmData.eventType = "SampleXDMEvent"
xdmData.otherField = "OtherFieldValue"

val experienceEvent = ExperienceEvent.Builder()
  .setXdmSchema(xdmData)
  .build()
```

Example 3: Set a custom destination Dataset ID when creating an `ExperienceEvent` instance.
```kotlin
val xdmData = mutableMapOf<String, Any>()
xdmData["eventType"] = "SampleXDMEvent"
xdmData["sample"] = "data"

// Set the destination Dataset identifier of the Experience Event
val experienceEvent = ExperienceEvent.Builder()
  .setXdmSchema(xdmData, "datasetIdExample")
  .build()
```