# Edge Network Extension Event Reference<!-- omit in toc -->

## Table of Contents<!-- omit in toc -->
- [Events handled by Edge](#events-handled-by-edge)
  - [Edge request content](#edge-request-content)
  - [Edge device attributes request content](#edge-device-attributes-request-content)
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
  - [Edge device attributes response](#edge-device-attributes-response)

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

### Edge device attributes request content

This internal request carries device attributes (for example, app details, push tokens and timezone) produced by the Edge Identity extension. Edge sends it to the `mobile/device-attributes` endpoint independently of collect consent. It is not an Experience Event and does not use the `interact` request path, the regular Edge hit queue, or the regular Edge response events.

Edge handles this event when Configuration and Identity shared states are available, using the same readiness condition as [Edge request content](#edge-request-content) and [Edge update consent](#edge-update-consent) events. The request is stored on a dedicated persistent queue that is not suspended or cleared by collect-consent changes. Processing of that queue starts after Edge has loaded its persisted state at boot.

Edge dispatches exactly one [Edge device attributes response](#edge-device-attributes-response) for each request that reaches a terminal outcome. Recoverable network failures (no connection could be made, for example because the device is offline, the connection timed out, or HTTP 408, 429, 502, 503, 504 and 507) are retried, honouring an integer `Retry-After` header, and dispatch no response until a terminal outcome is reached.

Before each send attempt, Edge compares the ECID in the identity map captured when the request was queued with the ECID in the latest Edge Identity shared state (the first `ECID` entry; a legacy ECID listed after it is not compared). If the ECIDs differ, the request is not sent and a `dropped` response with reason `identityChanged` is dispatched. If the latest Edge Identity shared state is unavailable, the request is retried later without dispatching a response. These checks also apply to requests that were waiting to be retried or were restored after an app restart.

#### Event dispatched by<!-- omit in toc -->

* Edge Identity extension

#### Event details<!-- omit in toc -->

| Event type | Event source |
| ---------- | ------------ |
| com.adobe.eventType.edgeBypassConsent | com.adobe.eventSource.requestContent |

#### Event data payload definition<!-- omit in toc -->

The event data fields (for example, `app`, `tokens` and `timezone`) are sent as-is in a flat request body. Any `xdm` or `meta` keys provided in the event data are discarded. Edge adds:

* `xdm.implementationDetails` and `xdm.identityMap`, using the identity map available when the request is queued. The device-attributes endpoint accepts exactly one ECID, so only the first (current Edge Identity) ECID is included; additional legacy ECIDs are omitted from this request. Other identity namespaces and the shared identity map are unchanged. This filtering does not apply to ordinary `interact` requests.
* `meta.state.entries`, containing persisted `state:store` values when available.

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

-----

### Edge device attributes response

This event is the terminal result of an [Edge device attributes request content](#edge-device-attributes-request-content) event. It is chained to the request event, so its parent ID is the request event's ID. Its response ID is not set.

Success is decided by the HTTP status code (200 or 202) only; the response body is not processed. Handles in the body, including `state:store` and `locationHint:result`, do not change the Edge store or location hint, and no Edge response, error or content-complete events are dispatched.

#### Event details<!-- omit in toc -->

| Event name | Event type | Event source |
| ---------- | ---------- | ------------ |
| Device Attributes Response | com.adobe.eventType.edgeBypassConsent | com.adobe.eventSource.responseContent |

#### Event data payload definition<!-- omit in toc -->

| Key | Value type | Required | Description |
| --- | ---------- | -------- | ----------- |
| status | `String` | Yes | `success`, `failed` or `dropped`. |
| reason | `String` | No | Set when `status` is not `success`. One of `missingConfig`, `invalidPayload`, `identityChanged`, `queueUnavailable` or `network`. |
| httpStatus | `Integer` | No | The HTTP status code of the Edge Network response, when one was received. |
| requestEventId | `String` | No | Set only when a stored request cannot be decoded. Contains the stored request's ID. In this case the response has no parent ID. |

| status | reason | When |
| ------ | ------ | ---- |
| `success` | | Edge Network returned HTTP 200 or 202. |
| `failed` | `network` | Edge Network returned a non-recoverable HTTP status, including 204, 207, 400, 403 and 500, and `httpStatus` is set. |
| `failed` | `queueUnavailable` | The dedicated queue could not be created or the request could not be queued. |
| `dropped` | `missingConfig` | The Edge configuration ID (datastream) is missing. |
| `dropped` | `invalidPayload` | The request has no data, Identity is unavailable, the request body or URL cannot be built, or a stored request cannot be decoded. |
| `dropped` | `identityChanged` | The ECID in the latest Edge Identity shared state differs from the ECID captured when the request was queued. The request is not sent. |
