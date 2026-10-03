# Recorded Android interface

These are original, unedited UIAutomation captures from the real Activity on an
AOSP API29 x86_64 Pixel emulator, portrait 1080×1920 with three-button navigation.
Source: `256dfaee5559eaf827e3228805d57653257bc56e`.
[Passing CI run](https://github.com/DrDeiss/Maffinet/actions/runs/37141382387),
2026-10-03. These images document that emulator configuration; physical phone/TV,
landscape, large fonts and other Android versions require separate acceptance.

| Screen | Capture |
| --- | --- |
| Home and required fork marking | [Home](home-api29.png) |
| Data-driven service profiles | [Services](services-api29.png) |
| Auto strategy and HTTP/TLS check controls | [Strategies](strategies-api29.png) |
| Attribution and unchecked update status | [About](about-api29.png) |
| Invalid URL rejected without replacing saved domains | [Domain validation](domain-validation-api29.png) |

The CI `android-validation` artifact contains all 12 captures, including first
launch, welcome, Settings, service state before/after relaunch and saved User
domains. Partly visible neighbouring cards at the scroll viewport edge can be
scrolled into view; the floating navigation has its own reserved space.
