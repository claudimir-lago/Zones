# Zones 0.5.2 User Guide

## 1. Starting Zones

For the Windows portable package, extract the entire ZIP and open `Zones.exe`. Java is included.

Build the project with Java 17+ and Maven (or NetBeans). On Windows, run `start_Zones.cmd` after packaging. The launcher uses `javaw`, so the command-prompt window closes after the application starts.

## 2. Opening data

Use **Open .dat…** to load a minimalistiCE-format file. Detector names are based on migration order from the injection point:

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

Calibration modes:

1. **Instrument parameters only** — capillary internal diameter, BGE conductivity, and injection-to-detector distance; result is **apparent mobility**. Distances are entered separately for the 1st and 2nd C4D.
2. **Two effective-mobility standards** — assign two fitted peaks and their known effective mobilities in the Quantitation table; result is **effective mobility**.
3. **Instrument parameters + one effective-mobility reference** — instrumental parameters plus one fitted reference peak of known effective mobility; result is **effective mobility**.

## 5. Peak analysis

Choose **Analyze peaks…** after baseline calculation. The Quantitation table reports domain-specific peak position, width, area, plate number and fitting information.

Rows with **R² < 0.99** are highlighted as a visual warning that the fit deserves inspection; the data are not rejected or modified.

For mobility-reference modes, use the editable **Mobility reference** and **Known μeff (Ti)** cells. Migration charge is taken automatically from the fitted peak.

## 6. About and scientific basis

The **About…** button shows the installed Zones version and opens the local HTML user guide when it is available. It also links to the repository and the main scientific reference:

E. T. da Costa, D. R. Oliveira, C. L. do Lago, “Qualitative and quantitative aspects of time-, charge-, and mobility-based electropherograms,” *Electrophoresis* 43 (2022) 2363–2376. DOI: 10.1002/elps.202200195.

Zones is distributed under **GPL-3.0-or-later**.
