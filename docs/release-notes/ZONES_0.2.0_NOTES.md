# Zones 0.2.0

First data-treatment release for transformed electrophoretic domains.

- Baseline compensation, spike treatment, peak detection and HVL fitting remain in acquisition time.
- Charge is calculated by trapezoidal integration and reported as coordinate magnitude |q| in mC. Signed cumulative charge is preserved internally.
- Mobility is reported as coordinate magnitude |mu| in Ti. Values above 1000 Ti are excluded from plots and transformed quantitation.
- Two-standard mobility calibration uses migration-charge magnitudes.
- The Quantitation table follows the active domain: position, fitted-component area and FWHM are recalculated in time, charge or mobility coordinates.
- Charge-domain fitted area is numerically integrated as signal versus |q| (a.u.·mC). Mobility-domain fitted area is numerically integrated as signal versus |mu| (a.u.·Ti).
- Transformed peak metrics are reported only where the selected magnitude coordinate is locally monotonic; folded/non-monotonic transformations are marked unavailable rather than silently double-counted.
- The program warns when current polarity reversal/non-monotonic |q| is detected.
- Plate-number fields remain explicitly identified as time-fit descriptors in transformed-domain quantitation.
