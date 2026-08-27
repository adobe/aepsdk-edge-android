# Edge event batching configuration

The Edge extension can coalesce multiple Experience Events into a single `/v1/interact` request.
Which events are eligible is controlled by a single grouped configuration object, `edge.batching`,
whitelisted **by XDM `eventType`**.

The **same format** is used whether the object is delivered via Configuration (remote / Launch /
`MobileCore.updateConfiguration`) under the key `edge.batching`, or bundled in the app's assets as
`ADBMobileEdgeBatchingConfig.json`. When both are present, the Configuration value wins **wholesale**
(the entire object, not a per-key merge). This keeps parsing identical and makes bundled-vs-remote
comparison predictable.

Batching is **off by default** — with no `edge.batching` config (or `enabled: false`), every Experience Event is sent in its own request, exactly as before this feature existed.

## How it works

When enabled, the Edge extension's hit queue processes a **window** of queued events per cycle instead of one:

- **Window size** = `min(queued events, maxBatchSize)`. `maxBatchSize` defaults to `10`, must be positive, and is clamped to an internal upper bound of `20` (the Edge Network per-request limit).
- **Eligibility.** Only consecutive events at the front of the queue that (a) are Experience Events (`EventType.EDGE` / `EventSource.REQUEST_CONTENT`), (b) have an `xdm.eventType` on the allow-list (below), and (c) share the head event's datastream configuration (datastream ID and any per-event `datastreamIdOverride` / `datastreamConfigOverride`) are combined into one request. The run truncates at the first event that fails any of these — that event becomes the head of a later cycle. Consent-update and identity-reset events are never batched and are processed individually.
- **The batching window is the network round-trip itself** — there is no debounce timer. While one request is in flight, newly queued events accumulate and coalesce on the next cycle.

### Response outcomes

| Server response | Behavior |
|-----------------|----------|
| `2xx` / `207` | Success — per-event handles/errors are routed to the originating events; batch removed. |
| `429` / `5xx` recoverable / timeout | Whole batch retried in place (honoring `Retry-After`). |
| `400` | Nothing ingested — the batch is drained and each event is re-sent individually so a single bad event can't block the others. |
| other non-recoverable (`403`, `404`, `422`, …) | Batch dropped; each event receives the error. |

### Per-event completion and errors

Even when events are batched into one request, each event's callback is resolved **independently**: response handles and errors are attributed to the specific event they belong to (by the Edge Network's per-event index), and each event's completion fires as its data arrives rather than waiting for the whole batch. To receive per-event errors, use the [`EdgeCallbackWithError`](api-reference.md#sendevent) overload of `Edge.sendEvent`.

## Format

```json
{
  "_meta": { "schemaVersion": 1 },
  "enabled": true,
  "maxBatchSize": 10,
  "wildcards": [
    { "xdmEventType": "media.*", "enabled": false }
  ],
  "edgeMedia": [
    { "xdmEventType": "media.play", "enabled": true }
  ],
  "messaging": [
    { "xdmEventType": "decisioning.propositionInteract", "enabled": true }
  ]
}
```

### Reserved top-level keys

| Key | Meaning |
|-----|---------|
| `enabled` | Master switch. When `false`, no batching occurs regardless of the allow-list. |
| `maxBatchSize` | Max events per request. Coerced to a positive value and clamped to an internal upper bound. |
| `wildcards` | Array of pattern entries (see below). |
| `_meta` | Ignored at parse time; use it for documentation / versioning. |

### Extension groups

**Every other top-level key** is treated as an *extension group*: an array of event objects
`{ "xdmEventType": <string>, "enabled": <bool> }`. Grouping is purely for readability — the SDK
flattens all groups into one allow-list, OR-deduping the same `xdmEventType` across groups. A remote
config may use a single generic group (e.g. `"events"`), the same parser handles it.

### Matching rules (strict allow-list)

An outgoing Experience Event is batchable only if its `xdm.eventType` matches an **enabled** exact
entry or an **enabled** wildcard:

- Exact, case-sensitive match against enabled extension-group entries.
- Wildcards: a trailing `*` (prefix, e.g. `media.*`), a leading `*` (suffix, e.g. `*.propositionFetch`),
  or a bare `*` (match all).
- `enabled: false` simply means "not whitelisted by this entry" (equivalent to omitting it). There is
  **no** exclusion / carve-out: a disabled entry never removes an event another enabled entry or
  wildcard has whitelisted.
- An event with **no** `xdm.eventType` (e.g. a raw `Edge.sendEvent` with no `eventType`) is not
  batchable.

A reference catalog of the `xdm.eventType` values dispatched across AEP Android extensions is
maintained separately (see the Edge batching event catalog).
