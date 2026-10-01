# Contributing to Zones

Thank you for considering a contribution to Zones.

## Before contributing

Please search existing issues before opening a new one. For scientific or numerical changes, describe the expected behavior and, whenever possible, provide a small reproducible dataset or test case that can be shared publicly.

## Development environment

Zones uses Java 17 and Maven.

```bash
mvn clean test
```

All existing tests should pass before a pull request is submitted.

## Scientific changes

Changes to baseline correction, peak detection, peak fitting, charge integration, mobility conversion, or quantitative metrics should include:

- a clear description of the scientific rationale;
- references when the change is based on published methodology;
- automated tests or reference fixtures when practical;
- explicit documentation of any change in units, conventions, or numerical assumptions.

Time-domain preprocessing is currently the reference pipeline. Charge and mobility are derived representations unless a future version explicitly states otherwise.

## Code style

Keep changes focused and avoid unrelated refactoring in the same pull request. Preserve existing package organization and NetBeans `.form` files when editing Swing forms visually.

New source files should identify the project license with:

```text
SPDX-License-Identifier: GPL-3.0-or-later
```

Do not remove copyright or license notices belonging to third-party code.

## Pull requests

A pull request should explain:

1. what changed;
2. why it changed;
3. how it was tested;
4. whether scientific output or file compatibility is affected.

By contributing code to this repository, you agree that your contribution is provided under the project's GPL-3.0-or-later license, except where a file explicitly retains a compatible third-party license.
