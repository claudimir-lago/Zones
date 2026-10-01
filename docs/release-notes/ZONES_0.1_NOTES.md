# Zones 0.1

Initial Zones release, branched from jBaseline 0.2.2.

## Scientific architecture

- Baseline correction, spike handling, peak detection, peak windows and HVL fitting remain on the acquisition time scale. This deliberately preserves the validated jBaseline 0.2.2 processing chain.
- The presentation layer now supports three electrophoretic domains: **Time**, **Charge** and **Mobility**.
- Charge is displayed in **mC** and is recalculated from the current channel with the trapezoidal rule whenever current is available. If current is unavailable but a stored charge column exists, that column is used for display.
- Mobility is displayed in **Tiselius (Ti)**, where `1 Ti = 1e-9 m^2 V^-1 s^-1`.
- Effective-mobility conversion uses the two-internal-standard calibration of Eq. 14 in da Costa, Oliveira and do Lago, Electrophoresis 2022, 43, 2363-2376. The user supplies the migration charge (mC) and effective mobility (Ti) of two standards; Zones solves simultaneously for `k` and EOF mobility.

## Interface

- Domain selectors were added to the main view.
- Time uses the neutral theme inherited from jBaseline; Charge uses an amber/orange contextual theme; Mobility uses a violet/lilac contextual theme.
- Axis labels and cursor readouts always state the active domain and units, so domain identity does not depend on color alone.
- The analysis-range handles remain editable in Time mode because acquisition events and the processing pipeline are defined on the time scale. Their positions are mapped into Charge and Mobility views.
- Peak fit overlays are mapped to the active x-domain for visualization; fitting itself remains in time.

## Scope of 0.1

This first release establishes the domain infrastructure without altering the validated scientific detection/fitting algorithms. Domain-specific quantitative tables (for example charge-domain peak area or mobility-domain width/area reporting) are intentionally left for subsequent versions.
