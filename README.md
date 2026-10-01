# Zones

**Zones** is a desktop application for processing and analyzing capillary electrophoresis (CE) data, with emphasis on conventional time-based electropherograms, charge-based electropherograms, and mobility spectra.

The project is written in Java 17 using Swing/FlatLaf for the desktop interface. Its scientific processing pipeline includes baseline correction, peak detection, peak-window construction, and Haarhoff–Van der Linde (HVL) peak fitting.

Zones was initially derived from **jBaseline 0.2.2**. The time-domain preprocessing and peak-analysis pipeline were preserved as the basis for the new charge and mobility representations.

## Current version

**Zones 0.5.2**

At this stage:

- multiscale baseline scales are shown in seconds (internally converted to sample counts using the file median dt);
- analysis parameters, selective MMR and charge-recalculation options persist between sessions, with **Restore defaults** retained;
- Quantitation highlights fits with R² < 0.99 for visual inspection, while leaving all calculations unchanged;
- time-domain preprocessing remains the reference processing domain;
- charge is calculated from synchronized current data by trapezoidal integration and displayed with its natural sign in **mC**; an explicit **Invert charge axis** option displays `-Charge`;
- mobility is displayed with its natural sign in **Ti**, where `1 Ti = 1e-9 m² V⁻¹ s⁻¹`; the axis is identified as apparent or effective according to the calibration mode;
- mobility values with `|μ| > 1000 Ti` are excluded from the display/analysis window rather than truncated;
- mobility calibration supports **instrumental parameters only** (apparent mobility), **two effective-mobility standards** (effective mobility), or **instrumental parameters + one effective-mobility reference** (effective mobility);
- mobility standards are selected directly from **Peak analysis**, so migration charge is obtained automatically from the fitted peak;
- Time, Charge, and Mobility use distinct interface themes to make the active domain visually explicit.
- an **About** dialog reports the program version and provides direct access to the GitHub repository, user manual, scientific reference, and license information.

## Scientific basis

The charge- and mobility-domain implementation is based primarily on:

> E. T. da Costa, D. R. Oliveira, C. L. do Lago, “Qualitative and quantitative aspects of time-, charge-, and mobility-based electropherograms,” *Electrophoresis* 43 (2022) 2363–2376. DOI: 10.1002/elps.202200195.

The current implementation uses trapezoidal numerical integration for charge calculation rather than the right-Riemann implementation used in the original paper.

## User manual

Read the GitHub-rendered [`USER_GUIDE.md`](USER_GUIDE.md). An offline browser version is also included as `USER_GUIDE.html`.

## Windows portable package

Download `Zones-0.5.2-windows-x64.zip` from the [v0.5.2 release](https://github.com/claudimir-lago/Zones/releases/tag/v0.5.2), extract the entire archive, and open `Zones.exe`. Java is included. See [PACKAGING.md](PACKAGING.md) for the build procedure.

## Build and run

Requirements:

- JDK 17 or newer
- Maven 3.9+ recommended

From a terminal:

```bash
mvn clean test
mvn package
mvn exec:java
```

Alternatively, open the directory containing `pom.xml` as a Maven project in NetBeans, select JDK 17 or newer, and use **Clean and Build** followed by **Run Project (F6)**.

Maven downloads the required dependencies on the first build. On Windows, `start_Zones.cmd` can be used after packaging; it launches Zones with `javaw` so a command-prompt window does not remain open.

## Input formats

`.dat` files are auto-detected.

- `minimalistiCE`: five numeric columns — time in minutes, 1st C4D, 2nd C4D, current in µA, charge in mC.
- `oldMinimalistiCE`: three numeric columns — time, 1st C4D, 2nd C4D.

Time must increase strictly.

The 2nd C4D is selected by default when it contains a varying signal. If it is constant/zero, the 1st C4D is selected automatically. “1st” and “2nd” refer to detector order from the injection point, not to the physical left/right position in the instrument. The user can always change the detector.

## Processing chain

1. **Selective MMR outlier processing** — moving median is used only to form a residual; candidate runs above the robust threshold are reconstructed locally.
2. **Multiscale FABC baseline** — baseline estimation based on the validated jBaseline pipeline.
3. **Peak detection** — local prominence, LPNR and BES filtering.
4. **Peak windows** — isolated, same-polarity overlap, or bipolar/composite.
5. **HVL fitting** — Haarhoff–Van der Linde components parameterized as area, `a1`, `a2`, and dimensionless `eta`.
6. **Domain transformation** — peak locations identified in the time domain are mapped to signed charge and calibrated apparent- or effective-mobility coordinates for domain-specific metrics.

## Analysis range

Two vertical cursors on the original electropherogram define the analysis range. They initially span the complete file and can be dragged to exclude experimentally corrupted regions. Baseline correction, detection and fitting operate on the selected time-domain range.

## Results and export

The **Quantitation** table reports peak metrics for the active domain. A TAB-separated export and a detailed ZIP export are available for diagnostics and reproducibility.

## Repository structure

```text
.
├── docs/                    Project documentation and release notes
├── licenses/                Third-party license texts and required sources/notices
├── src/main/                Application source code and resources
├── src/test/                Automated tests and reference fixtures
├── CITATION.cff             Citation metadata
├── CONTRIBUTING.md          Contribution guidelines
├── LICENSE                  GNU GPL version 3
├── README.md                Project overview
├── THIRD-PARTY-NOTICES.md   Third-party attribution and license notices
├── USER_GUIDE.md            GitHub-rendered user guide
├── USER_GUIDE.html          Offline browser user guide
├── start_Zones.cmd          Windows launcher after Maven packaging
└── pom.xml                  Maven project definition
```

## Contributing

Contributions are welcome. Please read [`CONTRIBUTING.md`](CONTRIBUTING.md) before submitting an issue or pull request.

## License

Zones is free software licensed under the **GNU General Public License v3.0 or later (GPL-3.0-or-later)**. See [`LICENSE`](LICENSE).

Third-party libraries and adapted routines retain their respective licenses. See [`THIRD-PARTY-NOTICES.md`](THIRD-PARTY-NOTICES.md) and the [`licenses/`](licenses/) directory.
