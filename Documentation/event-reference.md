# Edge Network Extension Event Reference<!-- omit in toc -->

## Table of Contents<!-- omit in toc -->
- [Events handled by Edge](#events-handled-by-edge)
  - [Edge request content (event processing)](#edge-request-content-event-processing)
  - [Edge request identity](#edge-request-identity)
  - [Edge update consent](#edge-update-consent)
  - [Edge update identity](#edge-update-identity)
  - [Edge consent response content](#edge-consent-response-content)
  - [Edge identity reset complete](#edge-identity-reset-complete)
- [Events dispatched by Edge](#events-dispatched-by-edge)
  - [Edge request content (event creation)](#edge-request-content-event-creation)
  - [Edge update identity](#edge-update-identity-1)


## Events handled by Edge

The following events are handled by the Edge extension client-side.

### Edge request content (event processing)

This event is a request to process and deliver an Experience event to Edge Network. This event is created when the `Edge.sendEvent(ExperienceEvent)` API is called. This event is captured by the Edge Network extension's event listener in the Event Hub for processing and sent to the Edge Network.

#### Event Details<!-- omit in toc -->

| Event type                 | Event source                         |
| -------------------------- | ------------------------------------ |
| com.adobe.eventType.edge   | com.adobe.eventSource.requestContent |

| API      | Event type         | Event source      |
| -------- | ------------------ | ----------------- |
| [`Edge.sendEvent(experienceEvent,callback)`](api-reference.md#sendevent) | com.adobe.eventType.edge | com.adobe.eventSource.requestContent |

#### Data payload definition<!-- omit in toc -->

| Key       | Value type    | Required | Description           |
| --------- | ------------- | -------- | --------------------- |
| xdm       | `Map<String, Object>` | Yes      | XDM formatted data; use an `XDMSchema` implementation for a better XDM data ingestion and format control. |
| data      | `Map<String, Object>` | No       | Optional free-form data associated with this event. |
| datasetId | `String`        | No       | Optional custom dataset ID. If not set, the event uses the default Experience dataset ID set in the datastream configuration |

> **Note**  
> Events of this type and source are only processed if the data collection consent status stored in the `collect` property is **not** `n` (no); that is, either yes (`y`) or pending (`p`).

-----

### Edge request identity
This event is a request to get the current location hint being used by the Edge Network extension in requests to the Edge Network. The Edge Network location hint may be used when building the URL for Edge Network requests to hint at the server cluster to use.

#### Event Details<!-- omit in toc -->

| API      | Event type         | Event source      |
| -------- | ------------------ | ----------------- |
| [`Edge.getLocationHint(callback)`](api-reference.md#getlocationhint) | com.adobe.eventType.edge | com.adobe.eventSource.updateConsent |

#### Data payload definition<!-- omit in toc -->

| Key          | Value type    | Required | Description                |
| ------------ | ------------- | -------- | -------------------------- |
| locationHint | `boolean`     | Yes      | The Edge Network location hint to use when connecting to  Edge Network. Property is set automatically; it is not user modifiable. |

-----

### Edge update consent

This event is a request to process and deliver a Consent update event to Edge Network.

#### Event Details<!-- omit in toc -->

| API                             | Event type                 | Event source                         |
| ------------------------------- | -------------------------- | ------------------------------------ |
| [`Consent.update(consents)`](https://github.com/adobe/aepsdk-edgeconsent-android/blob/main/Documentation/api-reference.md) | com.adobe.eventType.edge   | com.adobe.eventSource.updateConsent  |

#### Data payload definition<!-- omit in toc -->

| Key       | Value type            | Required | Description           |
| --------- | --------------------- | -------- | --------------------- |
| consents  | `Map<String, Object>` | Yes      | XDM formatted consent preferences. See the [`Consent.update(consents)`](https://github.com/adobe/aepsdk-edgeconsent-android/blob/main/Documentation/api-reference.md) API reference for how to properly format this property. |

-----

### Edge update identity

This event is a request to set the Edge Network location hint used by the Edge Network extension in requests to Edge Network. (how edgy!)

> **Warning**  
> Use caution when setting the location hint. Only use valid [location hints defined within the `EdgeNetwork` scope](https://experienceleague.adobe.com/docs/experience-platform/edge-network-server-api/location-hints.html). An invalid location hint value will cause all Edge Network requests to fail with a `404` response code.

#### Event Details<!-- omit in toc -->

| API                             | Event type                 | Event source                         |
| ------------------------------- | -------------------------- | ------------------------------------ |
| [`Edge.setLocationHint(hint)`](api-reference.md#setlocationhint) | com.adobe.eventType.edge   | com.adobe.eventSource.updateIdentity  |

#### Data payload definition<!-- omit in toc -->

| Key       | Value type    | Required | Description           |
| --------- | ------------- | -------- | --------------------- |
| hint      | `String`      | Yes      | Location hint value. Passing `null` or an empty string (`""`) clears the existing location hint. See  the [list of valid location hints for the `EdgeNetwork` scope](https://experienceleague.adobe.com/docs/experience-platform/edge-network-server-api/location-hints.html). |

-----

### Edge consent response content

This event contains the latest consent preferences synced with the SDK, and is usually updated after the server response was received for a `Consent.update(consents)` request. The Edge Network extension reads the current data collection consent settings stored in the `collect` property and adjusts its internal queueing behavior based on the value as follows:

| Value | Behavior |
| ------ | -------- |
| Yes (`y`) | Hits are sent |
| No (`n`) | Hits are dropped and not sent |
| Pending (`p`) | Hits are queued until `y`/`n` is set; when set, queued events follow the value's behavior |

#### Event Details<!-- omit in toc -->

| Event type                        | Event source                          |
| --------------------------------- | ------------------------------------- |
| com.adobe.eventType.edgeConsent   | com.adobe.eventSource.responseContent |

#### Data payload definition<!-- omit in toc -->

| Key       | Value type    | Required | Description           |
| --------- | ------------- | -------- | --------------------- |
| consents  | `Map<String, Object>` | No       | XDM formatted consent preferences containing current collect consent settings. If not specified, defaults to pending (`p`) until the value is updated. |

----- 

### Edge identity reset complete

This event signals that [Identity for Edge Network](https://github.com/adobe/aepsdk-edgeidentity-android) has completed [resetting all identities](https://developer.adobe.com/client-sdks/documentation/identity-for-edge-network/api-reference/#resetidentities) usually following a call to [`MobileCore.resetIdentities()`](https://github.com/adobe/aepsdk-core-android/blob/main/Documentation/MobileCore/api-reference.md). 

When this event is received, the Edge extension queues it up and removes the cached internal state:store settings. If other events are queued before this event, these events will be processed first in the order they were received.

#### Event Details<!-- omit in toc -->

| API                             | Event type                 | Event source                         |
| ------------------------------- | -------------------------- | ------------------------------------ |
| [`MobileCore.resetIdentities()`](https://github.com/adobe/aepsdk-core-android/blob/main/Documentation/MobileCore/api-reference.md) | com.adobe.eventType.edgeIdentity   | com.adobe.eventSource.resetComplete  |

#### Data payload definition<!-- omit in toc -->

This event has no data payload.

-----

## Events dispatched by Edge

The following events are dispatched by the Edge extension client-side.

### Edge request content (event creation)

This event is a request to process and deliver an Experience event to Edge Network. This event is created when the `Edge.sendEvent(ExperienceEvent)` API is called. It is then sent to the Event Hub where the Edge Network extension's listener captures the event.

#### Event Details<!-- omit in toc -->

| Event type                 | Event source                         |
| -------------------------- | ------------------------------------ |
| com.adobe.eventType.edge   | com.adobe.eventSource.requestContent |

| API      | Event type         | Event source      |
| -------- | ------------------ | ----------------- |
| [`Edge.sendEvent(experienceEvent,callback)`](api-reference.md#sendevent) | com.adobe.eventType.edge | com.adobe.eventSource.requestContent |

#### Data payload definition<!-- omit in toc -->

| Key       | Value type    | Required | Description           |
| --------- | ------------- | -------- | --------------------- |
| xdm       | `Map<String, Object>` | Yes      | XDM formatted data; use an `XDMSchema` implementation for a better XDM data ingestion and format control. |
| data      | `Map<String, Object>` | No       | Optional free-form data associated with this event. |
| datasetId | `String`        | No       | Optional custom dataset ID. If not set, the event uses the default Experience dataset ID set in the datastream configuration |

> **Note**  
> Events of this type and source are only processed if the data collection consent status stored in the `collect` property is **not** `n` (no); that is, either yes (`y`) or pending (`p`).

-----

### Edge update identity

This event is a request to process and deliver an Experience event to Edge Network. This event is created when the `Edge.sendEvent(ExperienceEvent)` API is called. It is then sent to the Event Hub where the Edge Network extension's listener captures the event.

#### Event Details<!-- omit in toc -->

| Event type                 | Event source                         |
| -------------------------- | ------------------------------------ |
| com.adobe.eventType.edge   | com.adobe.eventSource.requestContent |

| API      | Event type         | Event source      |
| -------- | ------------------ | ----------------- |
| [`Edge.sendEvent(experienceEvent,callback)`](api-reference.md#sendevent) | com.adobe.eventType.edge | com.adobe.eventSource.updateIdentity |

#### Data payload definition<!-- omit in toc -->

| Key       | Value type    | Required | Description           |
| --------- | ------------- | -------- | --------------------- |
| locationHint       | `@Nullable String` | Yes      | The Edge Network location hint to use when connecting to  Edge Network. Property is set automatically; it is not user modifiable. implementation for a better XDM data ingestion and format control. |
| data      | `Map<String, Object>` | No       | Optional free-form data associated with this event. |
| datasetId | `String`        | No       | Optional custom dataset ID. If not set, the event uses the default Experience dataset ID set in the datastream configuration |


**Edge.java**
sendEvent
EventType.EDGE
EventSource.REQUEST_CONTENT

setLocationHint
EventType.EDGE,
EventSource.UPDATE_IDENTITY

**EdgeExtension.java**
handleGetLocationHint
EventType.EDGE,
EventSource.RESPONSE_IDENTITY

**NetworkResponseHandler.java**
processResponseOnError
EventType.EDGE,
EventSource.ERROR_RESPONSE_CONTENT

dispatchResponse
EventType.EDGE
programmatic SOURCE