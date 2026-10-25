# Transparent media widget — 0.4

The previous widget grew its dark capsule while `centerInside` capped icons at
their intrinsic size. Resizing mostly added empty background.

Version 0.3 removes the capsule. The root, gaps, and resting button surfaces are
transparent; only the icons remain. A transient circular ripple provides feedback.
The center Play / Pause glyph is slightly larger, using original vector artwork.

The group stays compact and centered in wide, shallow widgets. Taller widgets
can grow the buttons and icons together, up to a sensible upper bound. Exact host
sizes use Android's RemoteViews size-map API; legacy ranges provide a fallback.
The minimum is 144 × 48 dp with three 48 dp touch targets. Gaps shrink at the
minimum size before touch targets do.

Media Actions → Widget appearance offers a live preview, white/dark icons,
75–135% icon size, and 0–64 dp spacing, plus automatic full-width spacing. “Apply to widgets” saves these
non-sensitive preferences locally and redraws placed instances. It never sends a
playback command. The preview is not interactive; its sample wallpaper is separate
from the transparent widget.

If a host reports no dimensions, a flexible fallback still applies color and icon
tuning, with custom gaps collapsed to preserve touch areas. Precise grouping and
scaling depend on host size reporting. LockStar may impose its own size limits or
an outer container; the app cannot remove a background that the host itself adds.

## Version 0.4: modern icon choices and more spacing

Three original vector sets are available: **Outline** (the new default, with round
stroke caps/joins), **Rounded** (filled shapes with softened corners), and **Classic**
(the 0.3 artwork). All retain the same three accessible button labels and media actions.
No external icon font, image download, or runtime dependency is introduced.

Manual spacing now reaches 64 dp. Full-width mode keeps square touch targets and
uses the remaining width as equal gaps. That mode ignores the saved manual gap and
disables its slider; disabling automatic mode restores the manual choice. At minimum
width, gaps shrink first to preserve three 48 dp touch targets. The preview uses a
shallow row to show the difference between compact and spread controls.

The five appearance settings persist locally. Existing scale, gap and color settings
are preserved when upgrading from 0.3; a missing/unknown icon style defaults to Outline.

## References and inspiration

* [Android widget sizing](https://developer.android.com/design/ui/mobile/guides/widgets/sizing)
  and [flexible layouts](https://developer.android.com/develop/ui/views/appwidgets/layouts):
  use host dimensions and adaptive layouts instead of fixed artwork in a growing box.
* [Android widget quality](https://developer.android.com/docs/quality-guidelines/widget-quality):
  avoid wasted space and cap growth that adds no value. The centered, transparent
  transport group is a deliberate choice for this compact lock-screen use.
* [N-Deck's app listing](https://play.google.com/store/apps/details?id=com.rszczotka.ndeck):
  reviewed for minimal transport controls and user-tunable appearance. No assets,
  source code, album-art features, or dependencies were copied.

`evidence/widget-preview.png` comes from the actual RemoteViews rendered with
Robolectric native Skia on sample wallpapers. It is not an S25/LockStar screenshot.
Pixel tests verify transparency and icon color/size. Phone-host sizing and secure-lock
interaction still require device testing.
