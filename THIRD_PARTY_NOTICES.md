# Third-Party Notices

This document separates source-derived content included in this repository from external build, runtime, and optional-integration dependencies. It is an attribution inventory, not a replacement for the license terms distributed by each upstream project.

## Source-Derived Content

| Project | Use in MMCR | License | Source |
| --- | --- | --- | --- |
| Modular Machinery Community Edition | Upstream project from which MMCR is derived | GPL-3.0 | [NovaEngineering-Source/ModularMachinery-Community-Edition](https://github.com/NovaEngineering-Source/ModularMachinery-Community-Edition) |
| LowDragLib2 | Adapted structure-preview source files listed in `NOTICE` | LGPL-3.0-or-later | [Low-Drag-MC/LDLib2](https://github.com/Low-Drag-MC/LDLib2) |
| Applied Energistics 2 | Basis for the adapted contribution guide | LGPL-3.0-or-later | [AppliedEnergistics/Applied-Energistics-2](https://github.com/AppliedEnergistics/Applied-Energistics-2) |

`NOTICE` identifies the affected files and provides the corresponding licensing information.

## External Dependencies

The following projects are resolved by the Gradle build or used as optional runtime integrations. Except where an upstream project is separately distributed with a release, they are not copied into this source repository. Their licensing and redistribution terms remain those supplied by their respective upstreams.

| Project | Role | Upstream |
| --- | --- | --- |
| NeoForge | Required mod-loader API and runtime | [NeoForged](https://neoforged.net/) |
| Just Enough Items (JEI) | Optional recipe-viewer integration | [mezz/JustEnoughItems](https://github.com/mezz/JustEnoughItems) |
| KubeJS | Optional scripting integration | [KubeJS-Mods/KubeJS](https://github.com/KubeJS-Mods/KubeJS) |
| Jade | Optional in-game information integration | [Snownee/Jade](https://github.com/Snownee/Jade) |
| Applied Energistics 2 | API and optional runtime integration | [AppliedEnergistics/Applied-Energistics-2](https://github.com/AppliedEnergistics/Applied-Energistics-2) |
| Rhino | KubeJS runtime dependency used by the development environment | [KubeJS-Mods/Rhino](https://github.com/KubeJS-Mods/Rhino) |
| Athena | Development runtime dependency | [CodingSeraphim/Athena](https://github.com/CodingSeraphim/Athena) |
| GeckoLib | Development runtime dependency | [bernie-g/geckolib](https://github.com/bernie-g/geckolib) |
| spark | Development runtime dependency | [LuckPerms/spark](https://github.com/lucko/spark) |
| Oritech | Development runtime dependency | [oricrew/oritech](https://github.com/oritech-mc/oritech) |
| JUnit Jupiter | Test framework | [junit-team/junit5](https://github.com/junit-team/junit5) |
| AssertJ | Test assertions | [assertj/assertj](https://github.com/assertj/assertj) |

Consult each upstream repository for its current license text. Dependency versions and scopes are defined in `dependencies.gradle`.
