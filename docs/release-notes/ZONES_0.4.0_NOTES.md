# Zones 0.4.0

This release focuses on a cleaner interface and continuity of analytical work.

- Scale values are presented to the user in seconds rather than sample counts. The internal multiscale algorithm still uses integer sample widths derived from the loaded file's median sampling interval. The first scale is generated from the configured minimum peak width.
- `median dt` is shown in milliseconds with one decimal place.
- Scale and K (votes) help text was rewritten around the characteristic-time / multiscale-voting interpretation.
- Baseline-anchor markers were reduced in size to keep the electropherogram visually dominant.
- Peak-fit overlays now show only the migration-time vertical marker; observed-apex and fitted-apex vertical markers were removed.
- The Diagnostics / result parameters pane is hidden from the regular interface.
- N and NGau are displayed as integer plate counts, without scientific notation.
- Quantitation rows with time-domain fit R² < 0.99 are highlighted as a visual quality warning; calculations are unchanged.
- All regular push buttons now receive a tint from the active Time / Charge / Mobility theme.
- Numerical processing settings, selective MMR enable state, and Recalculate charge state persist between sessions. Restore defaults remains available and overwrites the saved settings with the program defaults.

Scientific processing and the signed charge / mobility behavior introduced in 0.3.x are otherwise unchanged.
