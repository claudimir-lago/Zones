# Zones 0.3.1

Revision focused on mobility-reference entry in **Peak analysis**.

- The **Quantitation** table now contains editable `Mobility reference` and `Known μeff (Ti)` columns.
- For **Two effective-mobility standards**, rows can be assigned as `Ref 1` or `Ref 2`.
- For **Instrument parameters + one effective-mobility reference**, one row can be assigned as `Reference`.
- **Instrument parameters only** keeps these fields non-editable because no peak reference is required.
- Migration charge is obtained automatically from the selected fitted peak; the user enters only the known effective mobility.
- Assigning a role/value updates the mobility calibration automatically; changing a row back to `—` removes that reference.
- The previous separate “Use selected peak as mobility reference…” button was removed to avoid duplicate workflows.
- All scientific processing and the signed charge/mobility behavior of 0.3.0 are preserved.
