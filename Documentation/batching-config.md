# Edge event batching configuration

The Edge extension can coalesce multiple Experience Events into a single `/v1/interact` request.
Which events are eligible is controlled by a single grouped configuration object, `edge.batching`,
whitelisted **by XDM `eventType`**.

The **same format** is used whether the object is delivered via Configuration (remote / Launch /
`MobileCore.updateConfiguration`) under the key `edge.batching`, or bundled in the app's assets as
`ADBMobilEdgeBatchingConfig.json`. When both are present, the Configuration value wins **wholesale**
(the entire object, not a per-key merge). This keeps parsing identical and makes bundled-vs-remote
comparison predictable.

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
