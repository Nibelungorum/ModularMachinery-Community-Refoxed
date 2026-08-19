# Third-Party Notices

This document separates source-derived content included in this repository from external build, runtime, and optional-integration dependencies. It is an attribution inventory, not a replacement for the license terms distributed by each upstream project.

## Source-Derived Content

| Project | Use in MMCR | License | Source |
| --- | --- | --- | --- |
| Modular Machinery Community Edition | Upstream project from which MMCR is derived | GPL-3.0 | [NovaEngineering-Source/ModularMachinery-Community-Edition](https://github.com/NovaEngineering-Source/ModularMachinery-Community-Edition) |
| LowDragLib2 | Adapted structure-preview and fluid GUI rendering source files listed in `NOTICE` | LGPL-3.0-or-later | [Low-Drag-MC/LDLib2](https://github.com/Low-Drag-MC/LDLib2) |
| Applied Energistics 2 | Basis for the adapted contribution guide | LGPL-3.0-or-later | [AppliedEnergistics/Applied-Energistics-2](https://github.com/AppliedEnergistics/Applied-Energistics-2) |

`NOTICE` identifies the affected files and provides the corresponding licensing information.

## External Dependencies

Some projects are resolved by the Gradle build or used as optional runtime integrations. Except where an upstream project is separately distributed with a release, they are not copied into this source repository. Their licensing and redistribution terms remain those supplied by their respective upstreams.

Consult each upstream repository for its current license text. Dependency versions and scopes are defined in `dependencies.gradle`.
