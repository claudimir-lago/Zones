# Third-party notices

Zones itself is licensed under GPL-3.0-or-later. The notices below cover third-party
libraries and adapted routines that retain their own licenses. Scientific reference
files and datasets are not relicensed by the Zones software license.

## Runtime libraries

- **FlatLaf 3.7**, FormDev Software / Karl Tauber and contributors.
  https://www.formdev.com/flatlaf/ — Apache License 2.0 (`licenses/Apache-2.0.txt`).
  Unmodified runtime JAR; also retains its own META-INF/LICENSE.
- **EJML 0.44.0**, Peter Abeles and contributors.
  https://github.com/lessthanoptimal/ejml — Apache License 2.0
  (`licenses/Apache-2.0.txt`). Modules: core, ddense, dsparse. Used for sparse
  Cholesky, avoiding a custom numerical factorization.
- **JFreeChart 1.5.6**, David Gilbert and contributors.
  https://github.com/jfree/jfreechart — GNU LGPL 2.1 or later
  (`licenses/LGPL-2.1.txt`; referenced GPL text in `licenses/GPL-2.0.txt`).
  The unmodified library remains a separate, replaceable JAR in `target/lib`.
  Corresponding source is included in `licenses/jfreechart-1.5.6-sources.jar`.
  No library code has been modified. Preserve the source archive, notices and
  license texts with redistributed binaries. Do not prohibit modifications or
  reverse engineering of the application for debugging modifications to this library.

## Adapted scientific routines

`FabcClassifier` ports the relevant numerical behavior of **pybaselines 1.2.1**:
Copyright (c) 2021 Donald Erb, BSD 3-Clause. Full notice and license:
`licenses/pybaselines-BSD-3-Clause.txt`.

The CWT convention in pybaselines includes adaptations from **SciPy**:
Copyright (c) 2001-2002 Enthought, Inc. 2003-2023 SciPy Developers,
BSD 3-Clause. Full notice and license: `licenses/SciPy-BSD-3-Clause.txt`.

`BoundedRobustLeastSquares` adapts the dense, finite-bound trust-region-reflective
algorithm and trust-region helpers from SciPy 1.18.1 `optimize/_lsq/trf.py` and
`common.py`, under the same SciPy BSD 3-Clause license. The supported subset uses
soft-L1 loss, numerical two-point Jacobians, x_scale=1, and EJML SVD. Peak
prominence/width conventions correspond to SciPy `signal.find_peaks/peak_widths`.
EJML ddense is also used for this SVD and the geometric Savitzky-Golay projection.
No additional runtime dependency was introduced by the Test50 update.

Upstream sources:
https://github.com/derb12/pybaselines/tree/v1.2.1
https://github.com/scipy/scipy

## Development-only dependencies

JUnit Jupiter 5.12.2, EPL-2.0: https://github.com/junit-team/junit5
Maven and its Apache plugins, Apache-2.0: https://maven.apache.org/
These testing/build dependencies are not copied into `target/lib`.

Python, NumPy, SciPy, Matplotlib and pybaselines are used only to regenerate
reference fixtures; the Java application does not load them.

The visual styling follows the user's Mark Frontend 1.8 reference. No acquisition,
device discovery, networking or control code from Mark is included or modified.
