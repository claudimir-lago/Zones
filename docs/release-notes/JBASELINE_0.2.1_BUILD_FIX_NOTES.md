# Zones 0.2.1 — build/test-suite correction

This package keeps the 0.2.1 application code and corrects the Maven test suite that was accidentally left coupled to legacy external files and pre-0.2 algorithms.

Changes to tests only:
- Removed dependency on the external `brena` directory from the oldMinimalistiCE reader test; replaced by a self-contained exact-value fixture.
- Updated error-message assertions to the English UI/reader messages.
- Updated UI assertions (`K adjusted`, `Parameters edited`).
- Updated physical preprocessing expectations to selective MMR semantics: samples are unchanged unless the robust 5-sigma detector actually flags them.
- Baseline physical-default test now uses the project reference sampling interval (0.07620 s), matching the frozen oldMinimalistiCE validation.
- Added an HVL Gaussian-limit/mirror/descriptors unit test.
- Legacy tests tied to the removed local-anchor recovery, skew-normal production fitting, or an external BAH file are explicitly marked `@Disabled` with the reason. They remain in source as historical regression material rather than being silently rewritten to accept new numbers.

No production numerical result was changed merely to satisfy an obsolete test.
