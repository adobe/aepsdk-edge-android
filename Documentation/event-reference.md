# Edge Network Extension Event Reference<!-- omit in toc -->

## Table of Contents<!-- omit in toc -->
- [Events handled by Edge](#events-handled-by-edge)
  - [Edge request content](#edge-request-content)
  - [Edge request identity](#edge-request-identity)
  - [Edge update consent](#edge-update-consent)
  - [Edge update identity (event processing)](#edge-update-identity-event-processing)
  - [Edge consent response content](#edge-consent-response-content)
  - [Edge identity reset complete](#edge-identity-reset-complete)
- [Events dispatched by Edge](#events-dispatched-by-edge)
  - [Edge identity response](#edge-identity-response)
  - [Edge content response error](#edge-content-response-error)
  - [Edge event response](#edge-event-response)
  - [Edge state store](#edge-state-store)
  - [Edge location hint result](#edge-location-hint-result)

## Events handled by Edge

The following events are handled by the Edge extension client-side.

### Edge request content

This event is a request to process and deliver an Experience event to Edge Network. This event is captured by the Edge Network extension's event listener in the Event Hub for processing and sent to Edge Network.

If the required `xdm` key is not present in the event data payload, the event is not sent to Edge Network.

#### Event Details<!-- omit in toc -->

| Event type | Event source |
| ---------- | ------------ |
| com.adobe.eventType.edge | com.adobe.eventSource.requestContent |

APIs that create this event:
* [`Edge.sendEvent(ExperienceEvent experienceEvent, EdgeCallback callback)`](api-reference.md#sendevent)

#### Data payload definition<!-- omit in toc -->

| Key | Value type | Mandatory Key | Description |
| --- | ---------- | ------------- | ----------- |
| xdm       | `Map<String, Object>` | Yes      | XDM formatted data; use an `XDMSchema` implementation for a better XDM data ingestion and format control. |
| data      | `Map<String, Object>` | No       | Optional free-form data associated with this event. |
| datasetId | `String`        | No       | Optional custom dataset ID. If not set, the event uses the default Experience dataset ID set in the datastream configuration |

> **Note**  
> Events of this type and source are only processed if the data collection consent status stored in the `collect` property is **not** `n` (no); that is, either yes (`y`) or pending (`p`).

-----

### Edge request identity
This event is a request to get the current location hint being used by the Edge Network extension in requests to the Edge Network. The Edge Network location hint may be used when building the URL for Edge Network requests to hint at the server cluster to use.

#### Event Details<!-- omit in toc -->

| Event type | Event source |
| ---------- | ------------ |
| com.adobe.eventType.edge | com.adobe.eventSource.requestIdentity |

APIs that create this event:
* [`Edge.getLocationHint(AdobeCallback<String> callback)`](api-reference.md#getlocationhint)

#### Data payload definition<!-- omit in toc -->

| Key | Value type | Mandatory Key | Description |
| --- | ---------- | ------------- | ----------- |
| locationHint | `boolean`     | Yes      | Flag used to signal that this event is requesting the current location hint. Property is set to `true` automatically; it is not user modifiable. |

-----

### Edge update consent

This event is a request to process and deliver a Consent update event to Edge Network.

#### Event Details<!-- omit in toc -->

| Event type | Event source |
| ---------- | ------------ |
| com.adobe.eventType.edge   | com.adobe.eventSource.updateConsent  |

APIs that create this event:
* [`Consent.update(Map<String, Object> consents)`](https://github.com/adobe/aepsdk-edgeconsent-android/blob/main/Documentation/api-reference.md#updateConsents)

#### Data payload definition<!-- omit in toc -->

| Key | Value type | Mandatory Key | Description |
| --- | ---------- | ------------- | ----------- |
| consents  | `Map<String, Object>` | Yes      | XDM formatted consent preferences. See the [`Consent.update(consents)`](https://github.com/adobe/aepsdk-edgeconsent-android/blob/main/Documentation/api-reference.md) API reference for how to properly format this property. |

-----

### Edge update identity (event processing)

This event is a request to set the Edge Network location hint used by the Edge Network extension in requests to Edge Network.

> **Warning**  
> Use caution when setting the location hint. Only use valid [location hints defined within the `EdgeNetwork` scope](https://experienceleague.adobe.com/docs/experience-platform/edge-network-server-api/location-hints.html). An invalid location hint value will cause all Edge Network requests to fail with a `404` response code.

#### Event Details<!-- omit in toc -->

| Event type | Event source |
| ---------- | ------------ |
| com.adobe.eventType.edge   | com.adobe.eventSource.updateIdentity  |

APIs that create this event:
* [`Edge.setLocationHint(String hint)`](api-reference.md#setlocationhint)

#### Data payload definition<!-- omit in toc -->

| Key | Value type | Mandatory Key | Description |
| --- | ---------- | ------------- | ----------- |
| locationHint      | `String`      | Yes      | Location hint value. Passing `null` or an empty string (`""`) clears the existing location hint. See  the [list of valid location hints for the `EdgeNetwork` scope](https://experienceleague.adobe.com/docs/experience-platform/edge-network-server-api/location-hints.html). |

-----

### Edge consent response content

This event contains the latest consent preferences synced with the SDK, and is usually updated after the server response was received for a `Consent.update(consents)` request. The Edge Network extension reads the current data collection consent settings stored in the `collect` property and adjusts its internal queueing behavior based on the value as follows:

| Value | Behavior |
| ----- | -------- |
| Yes (`y`) | Hits are sent |
| No (`n`) | Hits are dropped and not sent |
| Pending (`p`) | Hits are queued until `y`/`n` is set; when set, queued events follow the value's behavior |

#### Event Details<!-- omit in toc -->

| Event type | Event source |
| ---------- | ------------ |
| com.adobe.eventType.edgeConsent   | com.adobe.eventSource.responseContent |

#### Data payload definition<!-- omit in toc -->

| Key | Value type | Mandatory Key | Description |
| --- | ---------- | ------------- | ----------- |
| consents  | `Map<String, Object>` | No       | XDM formatted consent preferences containing current collect consent settings. If not specified, defaults to pending (`p`) until the value is updated. |

----- 

### Edge identity reset complete

This event signals that [Identity for Edge Network](https://github.com/adobe/aepsdk-edgeidentity-android) has completed [resetting all identities](https://developer.adobe.com/client-sdks/documentation/identity-for-edge-network/api-reference/#resetidentities) usually following a call to [`MobileCore.resetIdentities()`](https://github.com/adobe/aepsdk-core-android/blob/main/Documentation/MobileCore/api-reference.md). 

When this event is received, the Edge extension queues it up and removes the cached internal `state:store` settings. If other events are queued before this event, those events will be processed first in the order they were received.

#### Event Details<!-- omit in toc -->

| Event type | Event source |
| ---------- | ------------ |
| com.adobe.eventType.edgeIdentity | com.adobe.eventSource.resetComplete |

APIs that create this event:
* [`MobileCore.resetIdentities()`](https://github.com/adobe/aepsdk-core-android/blob/main/Documentation/MobileCore/api-reference.md)

#### Data payload definition<!-- omit in toc -->

This event has no data payload.

-----

## Events dispatched by Edge

The following events are dispatched by the Edge extension client-side.

### Edge identity response

This event is a response to the [Edge request identity event](#edge-request-identity) with data payload containing `locationHint = true` and provides the location hint being used by the Edge Network extension in requests to the Edge Network. The Edge Network location hint may be used when building the URL for Edge Network requests to hint at the server cluster to use.

#### Event Details<!-- omit in toc -->

| Event type | Event source |
| ---------- | ------------ |
| com.adobe.eventType.edge | com.adobe.eventSource.responseIdentity |

#### Data payload definition<!-- omit in toc -->

| Key | Value type | Mandatory Key | Description |
| --- | ---------- | ------------- | ----------- |
| locationHint  | `String` | Yes       | The Edge Network location hint currently set for use when connecting to Edge Network. |

----- 

### Edge content response error

This event is an error response to an originating event. If there are multiple error responses for a given triggering event, separate error event instances will be dispatched for each error. 

#### Event Details<!-- omit in toc -->

| Event type | Event source |
| ---------- | ------------ |
| com.adobe.eventType.edge | com.adobe.eventSource.errorResponseContent |

#### Data payload definition<!-- omit in toc -->

| Key | Value type | Mandatory Key | Description |
| --- | ---------- | ------------- | ----------- |
| requestId  | `String` | Yes       | The ID (`UUID`) of the batched Edge Network request tied to the event that triggered the error response. |
| requestEventId  | `String` | Yes       | The ID (`UUID`) of the event that triggered the error response. |

----- 

### Edge event response

This event is a response to an event.

#### Event Details<!-- omit in toc -->

| Event type | Event source |
| ---------- | ------------ |
| com.adobe.eventType.edge | com.adobe.eventSource.responseContent |

#### Data payload definition<!-- omit in toc -->

This event does not have standard keys.

-----

### Edge state store

This event tells the Edge Network extension to persist the event payload in the data store. This event is constructed using the response fragment from the Edge Network service for a sent XDM Experience Event; Edge Network extension does not modify any values received and constructs a response event with the event source and data payload as-is.

#### Event Details<!-- omit in toc -->

| Event type | Event source |
| ---------- | ------------ |
| com.adobe.eventType.edge | state:store |

#### Data payload definition<!-- omit in toc -->

This event does not have standard keys.

----- 

### Edge location hint result

This event tells the Edge Network extension to persist the location hint to the data store. This event is constructed using the response fragment from the Edge Network service for a sent XDM Experience Event; Edge Network extension does not modify any values received and constructs a response event with the event source and data payload as-is.

#### Event Details<!-- omit in toc -->

| Event type | Event source |
| ---------- | ------------ |
| com.adobe.eventType.edge | locationHint:result |

#### Data payload definition<!-- omit in toc -->

| Key | Value type | Mandatory Key | Description |
| --- | ---------- | ------------- | ----------- |
| scope  | `String` | No       | The scope that the location hint is relevant for, for example `EdgeNetwork`. |
| hint  | `String` | No       | The location hint string. |
| ttlSeconds  | `Integer` | No       | The time period the location hint should be valid for. |
