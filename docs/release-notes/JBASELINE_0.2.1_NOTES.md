# Zones 0.2.1

Maintenance and interaction release based on 0.2.0.

## Changes
- Peak analysis no longer reuses cached candidates when **Analyze / refit** is requested; a complete peak detection is performed from the current baseline snapshot.
- Baseline-affecting parameter changes and Analysis-range changes invalidate and clear peak-analysis state. A generation token prevents an older background calculation from replacing newer state.
- The **Windows** table now contains an editable **Components** column. Editing it refits only that PeakWindow and marks the operation as `USER_EDITED`; baseline and detection are preserved.
- Double-click a row in **Quantitation**, **Components**, or **Windows** to zoom the main electropherogram to the corresponding PeakWindow with a 0.1 min margin.
- Replaced the remaining `Detalhes em points` label by `Details in points`.
- Numeric display/editors in the interface use `.` as decimal separator independently of the operating-system locale. Export still offers `.` or `,`.
- Analysis-range usage hint was expanded.

## Component-count note
The automatic detector remains the starting point. A manual component count is an expert override. When additional components are requested, seeds are selected from the strongest local extrema inside the existing PeakWindow, with fallback interior seeds if necessary. The HVL fit then optimizes all components simultaneously.
