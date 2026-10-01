# Zones 0.1.1

Small frontend refinement of Zones 0.1 after the first usability test.

## Interface changes

- Domain selection now has a dedicated row, so **Time**, **Charge** and **Mobility** remain visible when the main window is reduced.
- Removed the **Recovered anchors (disabled)** and **MMR cleaned** check boxes from the main interface. Their corresponding diagnostic overlays remain disabled internally.
- Removed the **Zoom +** and **Zoom -** buttons. Mouse-wheel and drag zoom remain available; **Reset zoom** is retained.
- The active-domain color now also reaches the FlatLaf window title bar.
- The diagnostics readout and its viewport now receive a light tint matching the active domain.
- The peak-analysis dialog follows the active domain theme as well.

## Scientific behavior

No scientific-processing changes were made in 0.1.1. Baseline and peak-location processing remain in the time domain; charge still uses trapezoidal integration and is displayed in mC; mobility is displayed in Ti.
