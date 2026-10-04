# Recorded Android interface

## Automatic Access, Android 16

The current unedited API36 Google APIs x86_64 Activity captures come from source
`8266a409f085a6e1050a5d54400ff199cb1de207`, passing
[CI run 37199682071](https://github.com/DrDeiss/Maffinet/actions/runs/37199682071).
[Home](automatic-access-api36/03-home.png) shows the common connection button and
application picker; [Strategies](automatic-access-api36/05-strategies.png) shows
the default general automatic mode and retained manual diagnostics.
[SHA256SUMS](automatic-access-api36/SHA256SUMS) records the original image hashes.
These emulator captures do not certify connectivity on the physical phone.

## Earlier independent-mode baseline, Android 10

These are original, unedited UIAutomation captures from the real Activity on an
AOSP API29 x86_64 Pixel emulator, portrait 1080×1920 with three-button navigation.
Source: `34ed3a1d13d55ab8c25f21c6042a930dd85842c5`.
[Passing CI run](https://github.com/DrDeiss/Maffinet/actions/runs/37153332402),
2026-10-03 UTC / 2026-10-04 Moscow. These images document that emulator configuration; physical phone/TV,
landscape, large fonts and other Android versions require separate acceptance.
These development captures use `SHOW_UPSTREAM_ATTRIBUTION=false`; release builds
retain the notice and attribution for the final UI.

| Screen | Capture |
| --- | --- |
| Maffinet Home: Applications/Telegram and large common button | [Home](home-api29.png) |
| Existing installed-app picker with explicit Settings selection | [Applications](independent-modes-api29/13-app-selection.png) |
| Scrollable VPN DNS presets | [DNS picker](independent-modes-api29/14-dns-selection.png) |
| Selected app count and persisted VPN DNS on Home | [Home DNS](independent-modes-api29/15-home-dns.png) |
| Existing standalone Telegram settings | [Telegram settings](independent-modes-api29/16-telegram-settings.png) |
| Actual Telegram listener running with VPN stopped and settings locked | [Telegram running](independent-modes-api29/18-telegram-running.png) |
| Common STOP completed; choices retained, controls unlocked | [Telegram stopped](independent-modes-api29/19-telegram-stopped.png) |
| Persisted Telegram-only choice / both choices off with explanation | [Telegram choice](independent-modes-api29/08-telegram-mode.png), [Both off](independent-modes-api29/09-modes-off.png) |
| General/User controls and expanded eight-domain base | [Hosts](independent-modes-api29/04-hosts.png), [General](independent-modes-api29/17-hosts-general.png) |
| Normalized saved User list / disabled state retained after Activity relaunch | [Saved User](independent-modes-api29/10-domains-saved.png), [Restored User](independent-modes-api29/12-domains-restored.png) |
| Invalid URL rejected without replacing saved domains; import/export controls | [Domain validation](domain-validation-api29.png) |
| Auto strategy and independently configurable HTTP/TLS checking addresses | [Strategies](strategies-api29.png) |
| Current settings routes / Maffinet description and unchecked update status | [Settings](independent-modes-api29/06-settings.png), [About](about-api29.png) |
| First launch and welcome after Activity relaunch | [First launch](independent-modes-api29/01-first-launch.png), [Welcome](independent-modes-api29/02-onboarding-welcome.png) |

The CI `android-validation` artifact `11284907595` contains these 19 captures and
the test/Lint/logcat reports. All 19 original PNGs are also committed under
`independent-modes-api29`; [SHA256SUMS](independent-modes-api29/SHA256SUMS) records
their hashes. Stable Home/Strategies/About/validation filenames contain the same
unaltered current captures. Partly visible neighbouring cards at the scroll
viewport edge can be scrolled into view; the floating navigation has reserved
space. The restored User capture intentionally includes its editing keyboard.

`services-api29.png` is retained only as historical evidence from source
`30ee86a`, run `37144128664`. Services is absent from current primary navigation.
