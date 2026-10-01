# Zones 0.3.0

- Charge and mobility now preserve their natural algebraic sign in the frontend and quantitative transformations.
- Optional **Invert charge axis** displays `-Charge (mC)` explicitly, without replacing signed data by magnitudes.
- Mobility values with absolute magnitude above 1000 Ti remain masked rather than truncated.
- Mobility calibration supports three scientific modes: instrumental parameters only (apparent mobility), two effective-mobility standards (effective mobility), and instrumental parameters plus one effective-mobility reference (effective mobility).
- Instrumental calibration accepts capillary i.d. (µm), BGE conductivity (S/m), and separate injection-to-detector distances (cm) for the left and right detectors.
- Peaks can be selected in Peak analysis as mobility references; the migration charge is taken automatically from the selected fitted peak and the user supplies only its known effective mobility.
- Quantitation tables distinguish apparent/effective mobility in their labels.
- N and NGau are displayed in scientific notation with three significant digits; peak-area display uses five significant digits.
- Table cells are centered.
- Baseline correction, spike handling, peak detection, and fitting remain in the acquisition-time domain.
- GPL-3.0-or-later licensing is retained.
