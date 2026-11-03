# 1.25.1/code81 verification — 2026-11-04

Release build and lintVital passed. Package com.eidolon.sc.mobile, min26/target35, versionName1.25.1/versionCode81. Original certificate SHA256 d576e97cae45aca6dedb80064e56a2aae97553fdaa8705904b63420b1d4503db verified (v2/v3);16KB alignment passed. Every packaged asset matches owning source; all production Java classes exist and are at least as fresh as the corresponding source. No migration: SQLite/encrypted backup17.

Final full existing test.ps1 suite exited0 after the backup validation change. Schedule generation35, schedule87 and overnight17 checks passed (139 in these three suites). New fake-model cases cover midnight bedtime, unpadded hours, full-width clocks, zero seconds, strict invalid/ambiguous times, exact-field errors, normalization stability, raw noncanonical backup rejection and normalized backup acceptance. Existing truncation-only bounded retry, cancellation, provider quota/network propagation, activities/sleep/bath constraints and encrypted roundtrip passed. Sticker package30 and series21 plus remaining existing offline suites passed.

Actual app HTML/JS in desktop Chrome/fake native bridge: generation failure state/reason, retry confirmation and cancel without generating, previous-day guard, local read error/retry, untouched empty state,3 layouts and dark screenshot passed. Existing03:00 schedule rollover/retained old plan and local update-notice settings/statistics navigation/failure/late replies/text safety passed. Screenshot visually inspected. These do not constitute Android device or real-provider acceptance.

APK SHA256: 9e0b9b0989bc4f7e9256b99dbeae5dbe8b8bac0c948d99380e6f013a88c9d85b
APK Git blob: 36a26c916c72521fcea851ee85cb40a34ed0a6e7
APK bytes: 4007010

No paid API, phone action, real model/ASR/Fish/weather/location request. Screenshot did not retain model response, so exact original invalid clock remains unknown. Old failed rows cannot be reconstructed and require explicit manual regeneration after install. This patch does not resolve all automatic scheduler throughput/background issues. Social/weather/city draft modules remain unintegrated and are excluded. No chat/voice/database rewrite, sticker reimport or signature change. Private build/signing script is excluded from publication. Public build/test paths are portable. Remote publication uses original base tree and checks all15 previous APKs retain identical hash/size; the staging directory holds12 previous APKs, with the older3 already existing remotely.
