# Zones 0.2.0 development notes

This update starts from the user-provided post-Codex project and preserves its existing structure.

Implemented in this revision:
- English user-facing frontend text and diagnostics.
- Selective MMR preprocessing: 5 robust sigma candidate threshold and quadratic 2+2 reconstruction; untouched samples remain unchanged.
- Multiscale FABC with the former local green-anchor recovery disabled.
- Validated default `num_std = 3.0`; physical scale reference aligned to the validation electropherogram so the default scales reproduce `[24,47,71,94,132,188,264,377]` at `dt = 0.07620 s`.
- LPNR + BES (`BES >= 3`) peak selection.
- HVL fitting using `(area, a1, a2, eta)`, `|eta| <= 40`, apex-aligned eta/a2 multistart, and final component ordering by fitted apex.
- `a1` is reported as Migration time; fitted apex remains complementary.
- Effective plate number `N = a1^2 / M2` and Gaussian plate number `NGau = (a1/a2)^2`.
- Quantitation table prioritizing migration time, area, and N; optional NGau/FWHM/R2 columns.
- TAB-separated quantitation export with point or comma decimal separator.
- Original and corrected C4D plot visibility controls.
- Fitted component areas shown with 20% transparency.
- Baseline anchors rendered above signal/baseline layers.
- Parameter/range edits mark displayed results as stale.
- Detector 2/right is the default when variable; detector 1/left is selected automatically if detector 2 is constant.
- Numeric spinner editors allow correction after temporarily invalid text.
- Opening/selecting a new file clears previous peak overlays and derived results.
- Two draggable analysis-range cursors on the original plot; the selected range is sliced before preprocessing/baseline/detection/fitting.

Validation on `brena/metais1806_1_novo12026.06.18_15h25m45s (1).dat`, right C4D:
- selective MMR changes 17 samples; maximum correction 130.0949 a.u.; all other samples are unchanged;
- FABC final anchors: 4554;
- local-prominence candidates: 1166;
- LPNR+BES selected candidates: 10;
- PeakWindows: 6.

Known methodological item intentionally not automated here: W4 can support an additional hidden component, but automatic component-count selection has not yet been established robustly. The code therefore does not hard-code a W4-specific third component. A later UI/manual component-editing mechanism is preferable to a dataset-specific rule.
