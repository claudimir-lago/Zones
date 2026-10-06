# Zones 0.6.0

## Main changes

- Added aqueous thermal normalization to 25 °C for mobility calibration. BGE conductivity and effective-mobility references can be entered at their source temperatures; Zones uses the water-viscosity model and the inverse-viscosity approximation before calibration.
- Added a source-temperature field for mobility standards directly in Peak analysis.
- Replaced the main diagnostic-oriented export with a user-oriented reproducible export: `quantitation.csv` plus optional processed electropherograms in time, charge and mobility. Files retain the `.csv` extension but always use TAB-separated columns.
- Kept the previous detailed diagnostic archive as a separate **Developer ZIP…** export.
- Added graph-to-table peak selection: clicking a fitted component region in Corrected C4D highlights the most likely Quantitation row; unmatched clicks generate an alert.
- Introduced `zones.core.ZonesEngine` and `RunAnalysis` as a UI-independent façade for the scientific pipeline, preparing the codebase for a future CLI/batch frontend without duplicating algorithms.
- Updated application, launcher, documentation and metadata to version 0.6.0.

## Scientific scope

Thermal normalization is intended for aqueous systems and uses the water-viscosity correlation employed in Francisco and do Lago, Talanta 185 (2018) 37–41 (viscosity source: Huber et al., J. Phys. Chem. Ref. Data 38 (2009) 101–125). It is not presented as a universal correction for non-aqueous or hydro-organic systems.
