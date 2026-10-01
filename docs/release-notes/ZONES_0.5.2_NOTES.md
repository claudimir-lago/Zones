# Zones 0.5.2

## Quantitation display precision

- Returned migration time in the Quantitation table to minutes.
- Replaced fixed decimal-place rules for time, charge, and mobility with adaptive formatting based on the local sampling resolution of the corresponding x-axis near each fitted peak.
- FWHM uses the same local-resolution principle; time-domain FWHM remains reported in seconds.
- The local resolution is estimated robustly from the median spacing of neighboring samples around the fitted migration position.
- The numerical values and scientific calculations are unchanged; this revision affects only how reported values are rounded for display.

## Version

- Updated project metadata and launcher to version 0.5.2.
