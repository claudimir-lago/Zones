# Zones 0.7.0

- Corrected graph-to-Quantitation selection so peak matching is triggered only by a double-click in the Corrected C4D plot.
- Added mobility calibration from the classical voltage/time relation for electropherograms without current or charge data.
- Instrumental mobility calibration now offers two retained bases: **Charge / conductivity** and **Voltage / capillary length**.
- Instrument-parameters-only calibration produces apparent mobility with either basis.
- Instrumental parameters plus one effective-mobility reference produces effective mobility with either basis.
- Two effective-mobility standards require no instrumental parameters and can use migration charge when available or migration time for legacy data.
- Voltage-based mobility is normalized to 25 °C using the aqueous inverse-viscosity model and a user-supplied run temperature.
- Mobility calibration values are preserved when switching strategies and persisted between sessions.
- Export metadata now records the selected mobility basis and both charge-based and voltage-based instrumental parameters.
- Updated application, launcher, documentation and metadata to version 0.7.0.
