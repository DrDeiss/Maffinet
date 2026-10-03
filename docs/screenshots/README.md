# Recorded Android interface

These are original, unedited UIAutomation captures from the real Activity on an
AOSP API29 x86_64 Pixel emulator, portrait 1080×1920 with three-button navigation.
Source: `30ee86a15a6ed889f719a24b10a2577bcb5d6e6b`.
[Passing CI run](https://github.com/DrDeiss/Maffinet/actions/runs/37144128664),
2026-10-03. These images document that emulator configuration; physical phone/TV,
landscape, large fonts and other Android versions require separate acceptance.
These development captures use `SHOW_UPSTREAM_ATTRIBUTION=false`; release builds
retain the notice and attribution for the final UI.

| Screen | Capture |
| --- | --- |
| Maffinet Home and connection/service controls | [Home](home-api29.png) |
| Data-driven service profiles | [Services](services-api29.png) |
| Auto strategy and HTTP/TLS check controls | [Strategies](strategies-api29.png) |
| Maffinet description and unchecked update status | [About](about-api29.png) |
| Invalid URL rejected without replacing saved domains | [Domain validation](domain-validation-api29.png) |

The CI `android-validation` artifact contains all 12 captures, including first
launch, welcome, Settings, service state before/after relaunch and saved User
domains. Partly visible neighbouring cards at the scroll viewport edge can be
scrolled into view; the floating navigation has its own reserved space.
