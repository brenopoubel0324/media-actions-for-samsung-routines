# Test report — Media Actions 0.4 experimental

Date: 2026-10-25 (UTC).

**Release decision: experimental proof of concept; MVP acceptance is BLOCKED by
missing S25 test evidence. No Samsung/Android restriction has been established.**
There is no attached Android device or running emulator in the build environment.
All hardware-dependent acceptance tests below are NOT TESTED. Do not interpret a
successful build or host simulation as a passing device test.

## Device and firmware

| Field | Observation |
|---|---|
| Intended device | Samsung Galaxy S25 SM-S931B (from handoff; not inspected) |
| Tested physical device | None available |
| Android / SDK / One UI | User reports “Latest version”; exact version numbers not confirmed on device |
| Build / security patch | Not confirmed |
| Modes and Routines version | Not confirmed |
| Routines+ installed / version | User says it can be installed; installation and version not confirmed |
| LockStar installed / version | Not confirmed; user requests a resizable LockStar widget |
| Physical button and event | Not selected or tested |
| Spotify / other player version | Not confirmed |
| Secure lock / AOD / playback route | Not tested |

Use `tools/device-info.sh` and manually record the trigger before device testing.

## Evidence categories

| Category | Status |
|---|---|
| Verified on real S25 | Nothing yet |
| Verified in Android emulator | Nothing; no emulator used |
| Verified by host tests / Android framework simulation | See build evidence below |
| Not tested | Samsung discovery, LockStar placement/resizing/locked taps, all routine/button/lock/wake cases, real playback, process death, reboot, idle power |
| Blocked by confirmed Samsung/Android restriction | None established; device investigation pending |

## Build and host evidence

| Check | Actual result |
|---|---|
| `./gradlew assembleDebug testDebugUnitTest lintDebug :app:dependencies --configuration debugRuntimeClasspath` | PASS, exit 0; `BUILD SUCCESSFUL` |
| JVM policy tests | PASS: 22 tests, 0 failures/errors/skips |
| Robolectric Android entry-point/adapter tests | PASS: 18 runs (9 cases × API 33 and API 36), 0 failures/errors/skips |
| Robolectric widget tests | PASS: 18 runs (9 cases × API 33 and API 36), 0 failures/errors/skips |
| Geometry/appearance tests | PASS: 9 JVM cases across supported sizes and style extremes, including 64 dp gaps and full-width mode |
| Size reporting, editor and preferences | PASS: 16 runs (8 cases × API 33 and API 36), including legacy preferences and the style/full-width editor |
| Native visual tests | PASS: 5 API 33 Skia cases; transparency, all styles/colors, visible spacing changes and preview export |
| Total host tests | PASS: 88 tests, 0 failures/errors/skips |
| Android lint, warnings treated as errors | PASS: `No issues found.` |
| APK signature (`apksigner verify --verbose --print-certs`) | PASS: one Android debug signer, APK Signature Scheme v2 |
| Packaged manifest inspection | PASS: zero `uses-permission` declarations; two exported Activities; listener not exported and protected by system binding permission; one non-exported widget receiver; no content providers |
| Packaged shortcut XML inspection | PASS: `next_track`, `previous_track`, `toggle_playback`; explicit Activity targets and three distinct allowlisted URIs |
| Packaged widget XML/layout inspection | PASS: transparent root; 3 × 1 default; both resize axes; 144 × 48 dp minimum; 432 × 144 dp requested maximum; no periodic updates |
| Production runtime dependencies | Kotlin stdlib 2.2.21 + JetBrains annotations 13.0; no automation/network/media/UI library |
| APK native libraries | None; no ABI-specific binaries |
| `tools/device-info.sh` shell syntax | PASS (`sh -n`); not run against a phone |
| Connected devices | `adb devices -l` lists no devices |

Builder: Linux x86_64, full OpenJDK 21.0.2 (builder only), Gradle 8.14.6,
Android Gradle Plugin 8.13.2, Kotlin 2.2.21, Android Build Tools 36.0.0.
App: minimum SDK 33, compile/target SDK 36, version `0.4-experimental` (code 4).

The delivered debug APK is **2,554,282 bytes** (~2.44 MiB).
SHA-256: `8a64e66965f7821ef06304d917c3313bd48b8114a484387a21fea6b78ed460f1`.
Debug signing certificate SHA-256:
`21b89aea083ff34ac9c9d300c2e068d62614f9555e52446c484695b4299a941a`.
The Gradle wrapper checks the official 8.14.6 distribution SHA-256.
The signer matches the supplied 0.1, 0.2 and 0.3 APKs, allowing an in-place update.

Subsequent user report: running the Samsung routine appears to close the foreground
app. Exact reproduction steps, foreground app, Android/One UI/module versions, and
whether the app merely loses focus or actually restarts are still unconfirmed.
This is an unresolved user-reported issue, not a passing test or a confirmed Samsung
restriction. The Activity-backed shortcut path is a diagnostic lead; no fix is
claimed in this version. Publishing the repository does not change this status.

The policy suite covers exact system targeting, stale hints, playing/paused/ambiguous
selection, denial/revocation, exceptions, supported actions, deliberate transition
handling, no retries/fallback duplicates, repeated invocations, and strict URI parsing.
The Android simulation checks packaged declarations, denied/granted access, no sessions,
state/action mapping into TransportControls, no-UI finishing, recreation suppression,
and settings-screen lifecycle. These tests do not play real audio.

The widget suite measures generic and exact-size controls, including 144 × 48,
156 × 56, 240 × 64, and 360 × 112 dp, and checks
48 dp touch areas and accessibility labels. It checks distinct immutable broadcast
tokens, rendered-button taps, no Activity launch, revoked access/no sessions,
malformed/stale widget requests, immutable fill-in handling, resize/update behavior,
and cancellation of removed widgets' tokens. Robolectric requires a test-only
in-process registration of the same private provider to simulate delivery of explicit
broadcasts that do not match the receiver's manifest update filter. These checks do
not validate Android cold-start delivery, Samsung's widget host, or keyguard policy.

Version 0.3 adds exact host-size variants, compact centering, responsive icon scaling,
and local appearance preferences. Geometry checks exercise width/height and icon/gap
extremes, preserving 48 dp touch targets across supported dimensions. Editor checks
verify that previews do not control media and that preferences save only on Apply.
Hosts that omit dimensions get a flexible fallback with collapsed custom gaps;
icon-size tuning there is approximate rather than exact percentage scaling.

Native pixel tests render the actual RemoteViews at API 33 using Skia. They verify
alpha=0 at outer corners and between separated buttons, no filled panel, white/dark
color changes, and larger glyphs with a larger icon-size setting.
[widget-preview.png](evidence/widget-preview.png) shows Outline, Rounded and Classic at 360 × 56 dp, each
with compact, 64 dp, and automatic full-width spacing on sample wallpaper. It is not a device screenshot.
Button growth is capped at 128 dp. Host maximum-size attributes are hints and may
not be honored by every launcher/LockStar version.

Raw evidence is included in [evidence/](evidence/) with the delivered source: Gradle
output/dependencies, JUnit XML reports, lint XML/text, decoded packaged manifest and
shortcut resources, signature verification, and a machine-readable
[verification summary](evidence/verification-summary.json). Final checks passed after
correcting compile errors, configuring Robolectric's host-JVM access, updating the
Gradle patch version, and adding Android 12+ backup exclusions. There are no remaining
known failing host checks. No physical test result is inferred from these checks.

## Version 0.4 checks

The new geometry tests include 0/24/48/64 dp gaps and manual/automatic modes across
small and large dimensions. Full-width mode is verified to spread controls without
stretching their touch areas and to ignore the disabled manual value. Renderer/editor
tests verify persistence of both new settings, upgrade from 0.3 preferences, and safe
fallback for an unknown style name. Pixel tests prove that all three icon sets render
differently, support dark color, and keep the background transparent. Visible icon
extents increase from compact to 64 dp to full-width at 360 × 56 dp.

The requested manual gap can be reduced when the host gives too little width. Automatic
spacing and precise gaps need valid host dimensions; no-dimension fallback limitations
remain. Device validation of all new styles and spacing is still pending.

## LockStar widget device matrix — new in 0.2

| ID | Required observation | Actual result |
|---|---|---|
| W01 | Media Actions → Media controls appears in the normal home widget picker | NOT TESTED on device |
| W02 | Widget appears in LockStar and can be placed on the lock screen | NOT TESTED |
| W03 | Small and larger sizes fit without clipped controls; record LockStar sizing limits | NOT TESTED in LockStar; host layout measurements pass separately |
| W04 | Each button changes local Spotify playback once, while securely locked, with no authentication prompt or app screen | NOT TESTED |
| W05 | Paused player resumes, Previous follows player semantics, and repeat taps do not duplicate commands | NOT TESTED |
| W06 | Denied/revoked access and no media produce no crash, stray launch, or audio | NOT TESTED on S25; simulated tests pass separately |
| W07 | Tap after ordinary process death; removal/re-addition; reboot before/after first unlock | NOT TESTED on device |
| W08 | Transparency over actual wallpaper; distinguish any host-added frame | NOT TESTED on device; native pixel checks pass |
| W09 | Resize in LockStar; icons grow, grouping remains compact, no clipping | NOT TESTED in LockStar; geometry/native render checks pass |
| W10 | White/dark icons, icon size and spacing; Apply updates placed instances | NOT TESTED on device; editor/preferences/renderer checks pass |
| W11 | Outline/Rounded/Classic, 64 dp manual spacing and automatic full-width mode in LockStar | NOT TESTED on device; host and native checks pass |

Widget architecture: native RemoteViews, a non-exported AppWidgetProvider, three
explicit immutable broadcast PendingIntents, and the existing one-shot dispatcher.
There is no shortcut/Activity launch on a widget tap. Controls are static; the combined
Play / Pause icon is not a live playback indicator. No media metadata or notification
content is shown. No periodic refresh, observer, wake lock, or added permission.
Appearance settings store only icon size, gap, color, icon style and full-width mode in private SharedPreferences.
Existing backup exclusions cover these preferences. Resizing, preview editing and
applying appearance never send playback commands.

During device testing, record the exact LockStar version, widget-menu availability,
dimensions/resize controls, secure-lock method, prompts, and visible screen behavior.
Test the home-screen widget first, then LockStar. Home-widget success is not proof of
LockStar hosting or locked interaction. A standard `keyguard` category is a host hint,
not a way to place a widget above a secure lock screen or bypass authentication.

## Required S25 acceptance matrix

| ID | Scenario and required observation | Priority | Actual result |
|---|---|---|---|
| T01 | Three shortcuts listed in Samsung's app actions and selectable | P0 | NOT TESTED |
| T02 | App Test Next with Spotify playing; exactly one track change | P0 | NOT TESTED |
| T03 | Routine Next unlocked; exactly one track change | P0 | NOT TESTED |
| T04 | Routine Next securely locked; track changes without unlock prompt | P0 | NOT TESTED |
| T05 | Supported physical button event; exactly one delivery/track change | P0 | NOT TESTED |
| T06 | Locked, display off; explicitly record whether display wakes | P1 | NOT TESTED |
| T07 | Previous and Play/Pause, unlocked and locked, including paused playback | P1 | NOT TESTED |
| T08 | Spotify + another player; intended session receives the command | P1 | NOT TESTED |
| T09 | Revoke notification access; no crash, helpful settings-screen error | P0 | NOT TESTED on S25; host denial/revocation tests are separate |
| T10 | No active media; no crash, audio, or player launch | P0 | NOT TESTED on S25; host no-session tests are separate |
| T11 | Process eviction and force-stop tested separately; record both | P1 | NOT TESTED |
| T12 | Reboot before and after first unlock; record both | P1 | NOT TESTED |
| T13 | Repeated physical presses; no extra commands or stuck state | P1 | NOT TESTED; host one-command tests do not prove trigger delivery |
| T14 | Hours idle; measure battery usage and persistent system binding | P1 | NOT TESTED; source contains no polling/jobs/wake locks |

The handoff's ship gate requires T01–T05, T09, and T10 to pass **on the S25**.

## Lock/trigger outcomes tracked separately

| Outcome | Result |
|---|---|
| A — works while securely locked without credential/biometric prompt | UNVERIFIED |
| B — display stays off | UNVERIFIED |
| C — chosen physical key is supported by Samsung | UNVERIFIED |
| D — works after ordinary background process death | UNVERIFIED |
| E — works when the player is paused | UNVERIFIED |

No lock-screen flags, keyguard dismissal, overlays, accessibility automation, root,
Shizuku, or hidden Samsung interfaces were introduced. The one-shot Activity still
has Activity-launch semantics despite having no display.

## Procedure for the next device session

1. Record device/module versions. Install and open the app, manually grant Notification
   access, and start local Spotify playback. Test Next in the app and launcher first.
2. Confirm Samsung discovers the static Next shortcut before testing other actions.
   If it does not, record the exact menus and firmware rather than marking T01 passed.
3. Configure one supported routine trigger with one action. Record the button/event.
   Run one unlocked trial, then at least ten practical-gap button trials with playback
   on and the phone securely locked, noting prompts, track changes, and display wake.
4. Measure display wake visually or with a second camera. An ADB-launched command does
   not test Samsung invocation; a successful media command does not prove the screen
   remained off. Repeat with AOD and Bluetooth as separate configurations.
5. Test Previous/Play/Pause and paused playback; do not treat a song restart as a
   duplicate without inspecting player semantics. Test two players and unsupported
   actions, revoke access, and test no sessions.
6. Record normal process eviction separately from force-stop. Force-stop restricts
   components intentionally; reopen the app before comparison. Reboot and distinguish
   pre-first-unlock from a later secure lock.
7. Observe battery usage for hours idle and startup/track-change latency. Add actual
   timestamps and observations to the matrix. Redact private media/notification data.

If Routines blocks the Activity behind keyguard or exposes no suitable button event,
record a reproducible failure and keep the MVP blocked. The documented Samsung Side
button in-app-function route is a possible separate experiment (see README), with
no claimed compatibility or lock behavior here. Do not weaken screen security.
