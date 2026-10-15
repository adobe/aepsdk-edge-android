# Edge Network Extension Event Reference<!-- omit in toc -->

## Table of Contents<!-- omit in toc -->
- [Events handled by Edge](#events-handled-by-edge)
  - [Edge request content](#edge-request-content)
  - [Edge bypass-consent request content](#edge-bypass-consent-request-content)
  - [Edge request identity](#edge-request-identity)
  - [Edge update consent](#edge-update-consent)
  - [Edge update identity](#edge-update-identity)
  - [Edge consent response content](#edge-consent-response-content)
  - [Edge identity reset complete](#edge-identity-reset-complete)
- [Events dispatched by Edge](#events-dispatched-by-edge)
  - [Edge identity response](#edge-identity-response)
  - [Edge error response content](#edge-error-response-content)
  - [Edge response content](#edge-response-content)
  - [Edge state store](#edge-state-store)
  - [Edge location hint result](#edge-location-hint-result)
  - [Edge content complete](#edge-content-complete)

## Events handled by Edge

The following events are handled by the Edge extension client-side.

### Edge request content

This event is a request to process and deliver an Experience event to Edge Network. This event is captured by the Edge Network extension's event listener in the Event Hub for processing and sent to Edge Network.

If the required `xdm` key is not present in the event data payload, the event is not sent to Edge Network. To learn more about Experience Data Model (XDM), please read the [XDM system overview](https://experienceleague.adobe.com/docs/experience-platform/xdm/home.html)​.

#### Event dispatched by<!-- omit in toc -->
* [`Edge.sendEvent(ExperienceEvent experienceEvent, EdgeCallback callback)`](api-reference.md#sendevent)

#### Event details<!-- omit in toc -->

| Event type | Event source |
| ---------- | ------------ |
| com.adobe.eventType.edge | com.adobe.eventSource.requestContent |

#### Event data payload definition<!-- omit in toc -->

| Key | Value type | Required | Description |
| --- | ---------- | -------- | ----------- |
| xdm | <code>Map<String,&nbsp;Object></code> | Yes | XDM formatted data; use an `XDMSchema` implementation for better XDM data ingestion and data format control. |
| data | <code>Map<String,&nbsp;Object></code> | No | Optional free-form data associated with this event. |
| config | <code>Map<String,&nbsp;Object></code> | No | Optional config settings. Find the available keys for `config` below.|
| datasetId | `String` | No | Optional custom dataset ID. If not set, the event uses the default Experience dataset ID set in the datastream configuration. |
| request | <code>Map<String,&nbsp;Object></code> | No | Optional request parameters. Find the available keys for `request` below. |

config

| Key | Value type | Required | Description |
| --- | ---------- | -------- | ----------- |
| datastreamIdOverride | `String` | No | Optional datastream identifier used to override the default datastream identifier set in the Edge configuration. |
| datastreamConfigOverride | <code>Map<String,&nbsp;Object></code> | No | Optional datastream configuration used to override individual settings from the default datastream configuration. |

request

| Key | Value type | Required | Description |
| --- | ---------- | -------- | ----------- |
| path | `String` | No | Optional path to be used for the Edge request. |
| sendCompletion | `Boolean` | No | Optional flag to determine if a "complete" event is requested. |

> **Note**
> Events of this type and source are only processed if the data collection consent status stored in the `collect` property is **not** `n` (no); that is, either `y` (yes) or `p` (pending).

-----

### Edge bypass-consent request content

This internal request carries device/profile operational data produced by an Edge Identity operational-data rule. Edge sends it to the dedicated `mobile/device-attributes` endpoint using a separate persistent queue that is not suspended or cleared by collect-consent changes. It is not an Experience Event and is not processed by the regular `interact` path.

#### Event details<!-- omit in toc -->

| Event type | Event source |
| ---------- | ------------ |
| com.adobe.eventType.edgeBypassConsent | com.adobe.eventSource.requestContent |

#### Event data payload definition<!-- omit in toc -->

The operational-data fields (for example, `app`, `tokens`, and `timezone`) are passed through to the request body. Edge snapshots the identity map when queuing the event and adds it with SDK implementation details under `xdm`, and includes persisted `state:store` values under `meta.state` when available. Configuration and Identity shared states must be available before Edge queues the event. Sending and retrying queued requests uses the stored identity snapshot without comparing it with the latest ECID; a later identity reset does not by itself drop the request.

Persistence preserves explicit null attributes and the original event metadata, including its identifier, timestamp, parent ID, response ID, and event-history mask.

#### Terminal results<!-- omit in toc -->

Edge dispatches terminal results with type `com.adobe.eventType.edgeBypassConsent` and source `com.adobe.eventSource.responseContent`. Results contain `status` (`success`, `failed`, or `dropped`), an optional `reason`, and an optional `httpStatus`. Retryable attempts do not dispatch a terminal result.

Pending Configuration or Identity shared states normally defer handling. If Identity is unexpectedly unavailable during request handling, Edge dispatches `status: dropped` with `reason: invalidPayload`, without queuing the request.

When the original request can be decoded, the result is chained to that request using its parent ID, not its response ID. If a persisted bypass-queue record cannot be decoded, Edge removes it and dispatches `status: dropped`, `reason: invalidPayload`, and `requestEventId` containing the persisted record's identifier. This result has no parent ID or response ID because the original event is unavailable. Undecodable records on the ordinary queue do not dispatch bypass results.

#### Network response handling<!-- omit in toc -->

All HTTP 2xx responses are treated as acceptance. When a response body is present, Edge processes it through the existing non-streaming response handler before dispatching the terminal bypass result. Supported handles can update the location hint, persist `state:store` values, and dispatch normal Edge response events. Existing identity-reset suppression of stored-state and location-hint updates still applies. Empty or malformed response bodies do not turn an accepted HTTP response into a bypass failure.

Permanent network errors are forwarded to the normal error handler before the bypass failure result. Retryable attempts produce no terminal bypass result and remove their waiting-event registration; a subsequent attempt registers the original event under a new request ID.

-----

### Edge request identity

This event is a request to get the current location hint being used by the Edge Network extension in requests to the Edge Network. The Edge Network location hint may be used when building the URL for Edge Network requests to hint at the server cluster to use.

#### Event dispatched by<!-- omit in toc -->
* [`Edge.getLocationHint(AdobeCallback<String> callback)`](api-reference.md#getlocationhint)

#### Event details<!-- omit in toc -->

| Event type | Event source |
| ---------- | ------------ |
| com.adobe.eventType.edge | com.adobe.eventSource.requestIdentity |

#### Event data payload definition<!-- omit in toc -->

| Key | Value type | Required | Description |
| --- | ---------- | -------- | ----------- |
| locationHint | `boolean` | Yes | Flag used to signal that this event is requesting the current location hint. Property is set to `true` automatically; it is not user modifiable. |

-----

### Edge update consent

This event is a request to process and deliver a Consent update event to Edge Network.

#### Event dispatched by<!-- omit in toc -->
* [`Consent.update(Map<String, Object> consents)`](https://github.com/adobe/aepsdk-edgeconsent-android/blob/main/Documentation/api-reference.md#updateConsents)

#### Event details<!-- omit in toc -->

| Event type | Event source |
| ---------- | ------------ |
| com.adobe.eventType.edge | com.adobe.eventSource.updateConsent |

#### Event data payload definition<!-- omit in toc -->

| Key | Value type | Required | Description |
| --- | ---------- | -------- | ----------- |
| consents | <code>Map<String,&nbsp;Object></code> | Yes | XDM formatted consent preferences. See the [`Consent.update(consents)`](https://github.com/adobe/aepsdk-edgeconsent-android/blob/main/Documentation/api-reference.md#updateConsents) API reference for how to properly format this property. |

-----

### Edge update identity

This event is a request to set the Edge Network location hint used by the Edge Network extension in requests to Edge Network.

> **Warning**
> Use caution when setting the location hint. Only use valid [location hints defined within the `EdgeNetwork` scope](https://experienceleague.adobe.com/docs/experience-platform/edge-network-server-api/location-hints.html). An invalid location hint value will cause all Edge Network requests to fail with a `404` response code.

#### Event dispatched by<!-- omit in toc -->
* [`Edge.setLocationHint(String hint)`](api-reference.md#setlocationhint)

#### Event details<!-- omit in toc -->

| Event type | Event source |
| ---------- | ------------ |
| com.adobe.eventType.edge | com.adobe.eventSource.updateIdentity |

#### Event data payload definition<!-- omit in toc -->

| Key | Value type | Required | Description |
| --- | ---------- | ------------- | ----------- |
| locationHint | `String` | Yes | Location hint value. Passing `null` or an empty string (`""`) clears the existing location hint. See the [list of valid location hints for the `EdgeNetwork` scope](https://experienceleague.adobe.com/docs/experience-platform/edge-network-server-api/location-hints.html). |

-----

### Edge consent response content

This event contains the latest consent preferences synced with the SDK. The Edge Network extension reads the current data collection consent settings stored in the `collect` property and adjusts its internal queueing behavior based on the value as follows:

| Value | Description | Behavior |
| ----- | ----------- | -------- |
| `y` | Yes | Hits are sent |
| `n` | No | Hits are dropped and not sent |
| `p` | Pending | Hits are queued until `y`/`n` is set; when set, queued events follow the value's behavior |

#### Event dispatched by<!-- omit in toc -->
* [`Consent.update(Map<String, Object> consents)`](https://github.com/adobe/aepsdk-edgeconsent-android/blob/main/Documentation/api-reference.md#updateConsents)

#### Event details<!-- omit in toc -->

| Event type | Event source |
| ---------- | ------------ |
| com.adobe.eventType.edgeConsent | com.adobe.eventSource.responseContent |

#### Event data payload definition<!-- omit in toc -->

| Key | Value type | Required | Description |
| --- | ---------- | -------- | ----------- |
| consents | <code>Map<String,&nbsp;Object></code> | No | XDM formatted consent preferences containing current collect consent settings. If not specified, defaults to `p` (pending) until the value is updated. |

-----

### Edge identity reset complete

This event signals that [Identity for Edge Network](https://github.com/adobe/aepsdk-edgeidentity-android) has completed [resetting all identities](https://developer.adobe.com/client-sdks/documentation/identity-for-edge-network/api-reference/#resetidentities) usually following a call to [`MobileCore.resetIdentities()`](https://github.com/adobe/aepsdk-core-android/blob/main/Documentation/MobileCore/api-reference.md).

When this event is received, the Edge extension queues it up and removes the cached internal `state:store` settings. If other events are queued before this event, those events will be processed first in the order they were received.

#### Event dispatched by<!-- omit in toc -->
* [`MobileCore.resetIdentities()`](https://github.com/adobe/aepsdk-core-android/blob/main/Documentation/MobileCore/api-reference.md)

#### Event details<!-- omit in toc -->

| Event type | Event source |
| ---------- | ------------ |
| com.adobe.eventType.edgeIdentity | com.adobe.eventSource.resetComplete |

#### Event data payload definition<!-- omit in toc -->

This event has no data payload.

-----

## Events dispatched by Edge

The following events are dispatched by the Edge extension client-side.

### Edge identity response

This event is a response to the [Edge request identity event](#edge-request-identity) with data payload containing `locationHint = true` and provides the location hint being used by the Edge Network extension in requests to the Edge Network. The Edge Network location hint may be used when building the URL for Edge Network requests to hint at the server cluster to use.

#### Event details<!-- omit in toc -->

| Event type | Event source |
| ---------- | ------------ |
| com.adobe.eventType.edge | com.adobe.eventSource.responseIdentity |

#### Event data payload definition<!-- omit in toc -->

| Key | Value type | Required | Description |
| --- | ---------- | -------- | ----------- |
| locationHint | `String` | Yes | The Edge Network location hint currently set for use when connecting to Edge Network. |

-----

### Edge error response content

This event is an error response to an originating event. If there are multiple error responses for a given triggering event, separate error event instances will be dispatched for each error.

#### Event details<!-- omit in toc -->

| Event type | Event source |
| ---------- | ------------ |
| com.adobe.eventType.edge | com.adobe.eventSource.errorResponseContent |

#### Event data payload definition<!-- omit in toc -->

| Key | Value type | Required | Description |
| --- | ---------- | -------- | ----------- |
| requestId | `String` | Yes | The ID (`UUID`) of the batched Edge Network request tied to the event that triggered the error response. |
| requestEventId | `String` | Yes | The ID (`UUID`) of the event that triggered the error response. |

-----

### Edge response content

This event is a response to an [Edge request content](#edge-request-content) event. This event is constructed using the response fragment from the Edge Network service for a sent XDM Experience Event; Edge Network extension does not modify any values received and constructs a response event with the event source and data payload as-is. This event is only dispatched if the response fragment doesn't define a type, otherwise an event using the response type is dispatched such as a [state:store](#edge-state-store) or [locationHint:result](#edge-location-hint-result).

#### Event details<!-- omit in toc -->

| Event type | Event source |
| ---------- | ------------ |
| com.adobe.eventType.edge | com.adobe.eventSource.responseContent |

#### Event data payload definition<!-- omit in toc -->

This event does not have standard keys.

-----

### Edge state store

This event tells the Edge Network extension to persist the event payload to the data store. This event is constructed using the response fragment from the Edge Network service for a sent XDM Experience Event; Edge Network extension does not modify any values received and constructs a response event with the event source and data payload as-is.

#### Event details<!-- omit in toc -->

| Event type | Event source |
| ---------- | ------------ |
| com.adobe.eventType.edge | state:store |

#### Event data payload definition<!-- omit in toc -->

This event does not have standard keys.

-----

### Edge location hint result

This event tells the Edge Network extension to persist the location hint to the data store. This event is constructed using the response fragment from the Edge Network service for a sent XDM Experience Event; Edge Network extension does not modify any values received and constructs a response event with the event source and data payload as-is.

#### Event details<!-- omit in toc -->

| Event type | Event source |
| ---------- | ------------ |
| com.adobe.eventType.edge | locationHint:result |

#### Event data payload definition<!-- omit in toc -->

| Key | Value type | Required | Description |
| --- | ---------- | -------- | ----------- |
| scope | `String` | No | The scope that the location hint applies to, for example `EdgeNetwork`. |
| hint | `String` | No | The location hint string. |
| ttlSeconds | `int` | No | The time period the location hint should be valid for. |

-----

### Edge content complete

This event is a response to an [Edge request content](#edge-request-content) event and is sent when the Edge Network request is complete. This event is only dispatched when requested by the request content event when the `request` payload object contains the property `sendCompletion` with boolean value `true`.

#### Event details<!-- omit in toc -->

| Event type | Event source |
| ---------- | ------------ |
| com.adobe.eventType.edge | com.adobe.eventSource.contentComplete |

#### Event data payload definition<!-- omit in toc -->

| Key | Value type | Required | Description |
| --- | ---------- | -------- | ----------- |
| requestId | `String` | Yes | The ID (`UUID`) of the batched Edge Network request tied to the event that requested the completion response. |
