# jBaseline 0.2.2

Focused graphical-state correction after the first functional tests of 0.2.1.

- Peak overlays are now fully removed when the baseline/analysis becomes stale. Dataset count is captured before removal, avoiding premature termination while JFreeChart shrinks the dataset list.
- HVL component curves are displayed in the same vertical reference frame as the fitted sum: `offset + component`.
- Filled component areas are drawn between the local fitted offset and `offset + component`, using `XYDifferenceRenderer`, rather than between zero and the component. This removes the apparent vertical offset in low-amplitude/noisy windows while preserving the fitted numerical areas.
- No scientific fitting or area calculation was changed in this revision; the changes are presentation/state only.
