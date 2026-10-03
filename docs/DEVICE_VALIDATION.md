# Alpha device validation

Record device model, Android version, ABI, provider/network and build commit.
Phone and Android TV checks are distinct. Every row starts unverified.

| Check | Expected result | Result |
| --- | --- | --- |
| Installation | Maffinet and NetFix packages/data remain separate | Pending |
| First launch | Maffinet branding and fork notice appear | Pending |
| VPN permission/start/stop | Consent, foreground notice, complete cleanup | Pending |
| YouTube regression | Known working strategy reaches web/media | Pending |
| Service routing | Installed enabled packages enter allowed routing | Pending |
| Manual selections | Explicit app choices combine with service routing | Pending |
| Domain lists | Built-in/service/user domains reach ByeDPI arguments | Pending |
| User list | Add/edit/remove/toggle/import/export persist after restart | Pending |
| Multi-service probes | Results show each selected service and coverage | Pending |
| Auto | Coverage ranks before latency deterministically | Pending |
| DNS/IPv6 | Existing resolver and IPv6 controls work | Pending |
| Network transition | Wi-Fi/mobile switch reconnects a working tunnel | Pending |
| Background/reboot | Foreground service and optional boot start work | Pending |
| Proxy mode | Existing ByeDPI and Telegram proxy controls work | Pending |
| Strategies | Existing imports/exports and legacy placeholders work | Pending |
| TV | Remote focus, navigation, connect and back work | Pending |

HTTP/TLS probe success alone does not certify full Android application behavior.
