# Zones User Guide

## 1. Starting Zones

Build the project with Java 17+ and Maven (or NetBeans). On Windows, run `start_Zones.cmd` after packaging. The launcher uses `javaw`, so the command-prompt window closes after the application starts.

## 2. Opening data

Use **Open** to load a minimalistiCE-format file. Detector names are based on migration order from the injection point:

- **1st C4D** — first detector reached from the injection point.
- **2nd C4D** — second detector reached from the injection point.

This naming is independent of the physical left/right layout of the instrument.

## 3. Baseline processing

Set the analysis range with the draggable Analysis start/end markers, adjust parameters if needed, then choose **Calculate baseline**.

- **Scale** represents the characteristic time width of structures considered compatible with a CE peak. Zones displays scales in seconds, although the internal algorithm works with sample counts.
- **K (votes)** is the minimum number of scales that must agree that a point belongs to the baseline before it is accepted as baseline.
- Numerical parameters, selective MMR, and charge-recalculation options persist between sessions. **Restore defaults** returns to the program defaults.

## 4. Time, charge, and mobility domains

### Time

The conventional electropherogram uses migration time on the x axis. Baseline processing and peak detection are performed in this domain. In the Quantitation table, migration time is reported in minutes and the number of decimal places is chosen automatically from the local sampling resolution. Time-domain FWHM remains reported in seconds and uses the same adaptive-resolution rule.

### Charge

Charge is obtained by trapezoidal integration of synchronized current data and is displayed in mC with its natural sign. **Invert charge axis** explicitly plots `-Charge (mC)` when desired. The Quantitation table chooses decimal places automatically from the local charge spacing near each fitted peak.

### Mobility

Mobility is displayed in Tiselius (Ti), where `1 Ti = 10^-9 m² V^-1 s^-1`. Values with `|μ| > 1000 Ti` are excluded from the useful display/analysis window. Mobility position and FWHM use an adaptive number of decimal places derived from the local mobility spacing near each fitted peak.

For aqueous systems, BGE conductivity and mobility-reference values may be entered with their original measurement temperatures. Zones normalizes inverse-viscosity-dependent quantities to **25 °C** before mobility calibration. This correction is intended for aqueous systems and should not be treated as universal for non-aqueous or hydro-organic media.

Calibration modes:

1. **Instrument parameters only** — result is **apparent mobility**. The instrumental basis can be either:
   - **Charge / conductivity** — capillary internal diameter, BGE conductivity, and injection-to-detector distance. This is preferred when current/charge data are available.
   - **Voltage / capillary length** — signed applied voltage, total capillary length, injection-to-detector distance, and run temperature. This route also works with legacy electropherograms that contain no current or charge channel.
2. **Two effective-mobility standards** — assign two fitted peaks and their known effective mobilities in the Quantitation table; result is **effective mobility**. Instrumental fields are disabled because the two references define both the scale and EOF correction.
3. **Instrument parameters + one effective-mobility reference** — choose either instrumental basis above and add one fitted reference peak of known effective mobility; result is **effective mobility**.

Values entered for both instrumental bases are retained when switching between them (and persisted between sessions), so the two strategies can be compared without re-entering parameters.

## 5. Peak analysis

Choose **Analyze peaks** after baseline calculation. The Quantitation table reports domain-specific peak position, width, area, plate number and fitting information.

Rows with **R² < 0.99** are highlighted as a visual warning that the fit deserves inspection; the data are not rejected or modified.

For mobility-reference modes, use the editable **Mobility reference**, **Known μeff (Ti)** and **μeff temp (°C)** cells. Zones records both the fitted migration time and, when available, migration charge, and uses the coordinate appropriate to the selected calibration basis.

Selection is bidirectional: double-clicking a Quantitation row zooms the corresponding fit, while **double-clicking** within a fitted component region in the **Corrected C4D** graph highlights the most likely Quantitation row. A single click remains available for normal graph interaction. If no component covers the double-clicked region, Zones warns the user rather than forcing an assignment.

## 6. Exporting results

**Export results** can create either a ZIP archive or individual uncompressed files in a selected folder. It always includes `quantitation.csv`, which starts with the complete processing/calibration metadata needed for reproducibility and then lists time-domain quantitation plus charge and mobility metrics when available. The user can optionally include processed, baseline-corrected electropherograms for the time, charge and mobility domains.

Zones keeps the familiar `.csv` extension, but **all columns are separated by TAB** to avoid ambiguity with regional decimal separators and to improve compatibility with Excel and Origin. **Developer ZIP** remains available separately for detailed internal diagnostics.

## 7. About and scientific basis

The **About** button shows the installed Zones version and opens the local HTML user guide when it is available. It also links to the repository and the main scientific reference:

E. T. da Costa, D. R. Oliveira, C. L. do Lago, “Qualitative and quantitative aspects of time-, charge-, and mobility-based electropherograms,” *Electrophoresis* 43 (2022) 2363–2376. DOI: 10.1002/elps.202200195.

Thermal normalization follows the water-viscosity treatment used in K. J. M. Francisco and C. L. do Lago, *Talanta* 185 (2018) 37–41, with viscosity data from Huber et al., *J. Phys. Chem. Ref. Data* 38 (2009) 101–125.

Zones is distributed under **GPL-3.0-or-later**.
