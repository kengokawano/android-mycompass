# Compass App TODOs (next improvements)

## Settings & Persistence
- [ ] Persist `useTrueNorth`, `arEnabled`, distance unit, and smoothing level via DataStore (Preferences).
- [ ] Load persisted settings on startup and reflect in UI controls.
- [ ] Remove hardcoded defaults once persistence is in place.

## Orientation Robustness
- [ ] Add display-rotation aware remap: use `Display.getRotation()` and `SensorManager.remapCoordinateSystem` with `AXIS_Z/AXIS_MINUS_Z` plus existing face-down hysteresis.
- [ ] Verify behavior in portrait/landscape, face-up/face-down transitions.

## North Basis (True vs Magnetic)
- [ ] Persist `useTrueNorth` and ensure consistent basis across: azimuth rotation, destination drawing, and list labels.
- [ ] Add quick help text explaining the difference and when to use each.

## i18n/Strings
- [ ] Move all hardcoded strings to `strings.xml` (Japanese/English at minimum).
- [ ] Unify wording (e.g., 精度 良/中/低、北の基準など) and localize Search/Settings labels.

## Search UX (OSM/Nominatim)
- [ ] Show user-facing errors for network failures, timeouts, and zero results.
- [ ] Localize the rate-limit message; consider exponential backoff hints.
- [ ] Consider lightweight response caching for repeated queries.

## Permission & Onboarding
- [ ] In compass screen, surface missing location permission with action to request.
- [ ] Show brief guidance to calibrate (figure-eight) if 精度=低 が続く場合。

## Debug/Diagnostics (dev-only toggle)
- [ ] Optional overlay showing `azimuth`, `declination`, `bearing(true/mag)`, and smoothing level.
- [ ] Gate via `BuildConfig.DEBUG` or hidden settings.

## Performance & Power
- [ ] Throttle location updates when app not visible; stop sensors in background.
- [ ] Consider adaptive update rate based on motion (fused sensor).

## Accuracy Label & Indicator
- [ ] Tune thresholds for 精度 良/中/低; color-code chip (e.g., green/amber/red) with accessible contrast.
- [ ] Optionally show numeric ±° in a tooltip or debug mode.

## Units & Formatting
- [ ] Wire distance unit setting (m/km vs mi) into `formatDistance()`; persist selection.

## Testing
- [ ] Unit tests for `calculateBearing` and `calculateDistance` (known coordinate pairs).
- [ ] Integration sanity: verify bearing mapping to canvas angles (0=N, 90=E) with small rendering tests or math assertions.

## Accessibility
- [ ] Add content descriptions to key visuals; ensure labels readable with large fonts.

## Cleanup & Compliance
- [ ] Remove verbose logs; keep structured logs behind debug flag.
- [ ] Revisit Nominatim usage policy; maintain UA, rate limits; consider alternative provider or server-side proxy for production.
- [ ] Document privacy: location usage and storage in `DEVELOPMENT.md`/README.

## Nice-to-haves
- [ ] User-selectable smoothing presets (キビキビ/標準/滑らか)。
- [ ] Option to pin/show only nearest destination (reduces clutter).
- [ ] Simple calibration wizard entry point.

---
Owner: Mobile
Priority: Settings persistence, rotation remap, i18n, permission UX (in this order)
