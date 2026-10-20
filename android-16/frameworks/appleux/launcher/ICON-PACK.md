# iOS 27 icon pack — which set wins and why

`AppleuxIconPack` maps Android packages to iOS artwork. Every `ios_*.png` in
`appleux/launcher/src/main/res/drawable-nodpi/` comes from one of the sets
below. Keep this file in step with the images: a pack without provenance is how
a wrong icon ships.

## Sources

| Set | Location | Status |
|---|---|---|
| **Screenshot-extracted iOS 27** | `icons/assets/` (118 symbols) | **Primary** |
| Solid-Glass | `design-reference/Solid-Glass` | superseded |
| Delta-Icons-ios | `design-reference/Delta-Icons-ios` | superseded |
| Apple-App-Icons | `design-reference/Apple-App-Icons` | reference only (1024px, clean) |

`icons/` is the newest set: 118 symbols extracted from 50 real iPhone 11
(iOS 27) screenshots by the pipeline in `icons/tools/`, each shipped as native
PNG + SVG + a `@512.png` render.

## Which files are used, and what each one actually is

Icons were reviewed on contact sheets (`icons/qa/*.png`) before use, because
the pipeline's filenames are semantic guesses, not ground truth. Two
mismatches were found and corrected:

| Drawable | Source | What it really is |
|---|---|---|
| `ios_browser` | `apps/chrome` | Google Chrome — correct, Chrome *is* the browser |
| `ios_calendar` | `apps/calendar` | Calendar |
| `ios_camera` | `apps/camera-brown` | Camera |
| `ios_clock` | `glyphs/clock-analog` | Clock face (**not** `apps/compass-dark`, which is the Compass app) |
| `ios_contacts` | `apps/contacts` | Contacts |
| `ios_files` | `apps/files-folder` | Files |
| `ios_mail` | `apps/mail` | Mail |
| `ios_maps` | `apps/maps-arrow` | Maps |
| `ios_messages` | `apps/messenger` | Messages |
| `ios_phone` | `apps/phone` | Phone |
| `ios_photos` | `apps/photos` | Photos |
| `ios_settings` | `settings/settings-gear` | Settings |
| `ios_drive` | *(legacy 264px)* | Google Drive keeps its own brand |
| `ios_gcal` | *(legacy 264px)* | Google Calendar keeps its own brand |
| `ios_google` | *(legacy 264px)* | Google app keeps its own brand |
| `ios_youtube` | *(legacy 192px)* | YouTube keeps its own brand |
| `ios_ytmusic` | *(legacy 264px)* | YouTube Music keeps its own brand |

Google apps are **not** given iOS artwork: there is no iOS equivalent, and
substituting Apple Music for YouTube Music produced two identical red notes on
the dock, which is worse than showing each brand's own mark.

## Rejected from `icons/assets/`

- **`control-center/`** (6 glyphs) — muddy tan colour cast and white smudges
  from screenshot compression (`airplay-audio` has a visible blob;
  `bluetooth-circle`, `globe-circle` and `signal-bars` have ring artifacts).
  Control Center uses the hand-drawn `sf_*` vectors instead.
- **`glyphs/apple-logo`** — Apple trademark; excluded by the no-Apple-assets
  charter.
- **`glyphs/voice-search`, `glyphs/target-green`** — green compression blobs.

## Licensing

None of these sets ships a LICENSE file. Lab use only until provenance is
confirmed. `icons/` is derived from the operator's own device screenshots;
`design-reference/*` packs are third-party and carry no licence.