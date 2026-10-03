# Maffinet application, proxy and hosts model

The 2026-10-03 clarification replaces the former service-catalog interface.
Application routing, Telegram, hosts, DNS and strategy targets have independent
configuration. Integrated tests and API29 emulator validation passed for
`34ed3a1`; see
[device validation](DEVICE_VALIDATION.md) for evidence and remaining checks.

## Connection modes and lifecycle

Home has two mode choices: Applications and Telegram. Its common connection
button starts/stops the selected modes and shows each engine's actual state.
Applications uses Android VPN consent and the unchanged transport:

```text
VpnService → TUN → HEV tun2socks → local SOCKS → ByeDPI → Internet
```

Telegram uses the existing `TgProxyService`/`TgProxyController` and separate Rust
MTProto proxy. It can start with Applications disabled, requires no VPN consent,
and retains its existing settings. VPN DNS presets configure the VPN; they do not
configure this separate proxy.

`MaffinetSettingsRepository` owns mode choices and separate desired connection
flags. `ConnectionCoordinator` dispatches each engine, recovery and watchdog.
Actual states come from the VPN and Telegram controllers. The legacy
`service_enabled` is a compatibility mirror, not a recovery authority. A failed
mode must not stop the other running mode. The global STOP clears desired flags
before dispatching asynchronous engine stops. Engine-specific notification STOP
affects that engine's desired state independently.

With boot autostart disabled, recovery discards the previous boot's desired
session and cancels its persisted watchdog work. Activity recreation preserves
the user's economy setting. Startup validation errors are shared with Home even
when a background/autostart entry point requested the modes.

Migration snapshots existing mode preferences once and preserves `selected_apps`,
User hosts and engine preferences. Configuration changes remain locked while
connection requests/resources or a strategy test are active.

## Explicit Android routing

Only packages stored by the user in `selected_apps` are candidates for the VPN
allowlist. `ApplicationRouting` excludes Maffinet and verifies installation at
startup. An empty installed selection is rejected before creating full-device
routing. The picker shows the existing installed-app UI and selected count.
Legacy profile flags never add packages during migration, launch or selection.

## Hosts

`BuiltInDomainLists` defines General with 130 curated domain filters in eight
categories: video/music, social networks, Discord, Telegram, AI/translation,
development/work, Xbox/games and DNS infrastructure. The original eight hostnames
remain first and the legacy named aliases retain their original values.
Categories are read-only names for `{list:ID}` expansion, not application/profile
switches. `DomainListRepository` merges that base
with enabled User domains in stable deduplicated order; its General entry remains
the active aggregate for existing callers.

`DomainParser` validates plain domain names, comments and IDN normalization;
URLs, ports, wildcards, IP literals and command arguments are rejected.
`UserDomainStore` saves normalized domains atomically in a versioned local file.
Invalid edits/imports preserve the prior saved list. Editing replaces the User
list, import merges it, and export writes the saved list. Disabling User preserves
its content. The import parser additionally extracts names from IP/hostname files,
excluding mappings to blocked or local destinations; it does not apply those IPs.
The Hosts screen exposes the categories, User editor, document import/export and
manual HTTPS import. The downloader bounds response size/time and rejects HTML,
failed responses and redirects away from HTTPS. See [source policy](HOSTS_AND_DNS.md).

`ByeDpiArgumentCompiler` injects a native `-H` whitelist in every selective desync
group, constrains processing to TCP and adds an unchanged fallback for other
hosts and UDP. This ByeDPI revision ignores hosts for UDP, so selective mode
does not apply UDP desync. It is not configured with desktop winws arguments.
Explicit Advanced override retains custom whitelist/blacklist/unrestricted
commands. `{domains}` and `{list:general}` expand the active aggregate;
`{list:user}` and old named service aliases remain available in compatible
commands. The inherited `{sni}` value remains fake SNI rather than host selection.

## Independent strategy targets and history

`ProbeTargetRepository` persists a separately validated HTTP/HTTPS address list.
Default addresses cover the prior three known checks, but no service flag or app
choice selects them. Each candidate is tested with the production hosts compiler
through an isolated local SOCKS listener using one immutable configuration snapshot.

The saved fingerprint includes targets, lists/active hosts, host override, relevant
advanced filter values and candidate commands. Changed hosts/targets/filters
invalidate measured matrices and latency history; imported strategy entries remain
available. Snapshot mismatch also prevents automatic application of stale results.
Scoring prioritizes successful target coverage, then latency, then stable candidate
order. These are HTTP/TLS reachability results; app authentication, media and
provider bypass require separate device tests.

Deleting a strategy removes it from both history views and persisted measured
rows; stale actions cannot apply it. An explicit reimport restores the command
without restoring its prior measurements.

The four-ABI HEV binding remains
`io.maffinet.android.core.dpibypass.TProxyService`. Native/provenance and physical
device limits are tracked in [known limitations](KNOWN_LIMITATIONS.md).
