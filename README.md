# Modular Machinery Community: Refoxed

**Currently, this mod is still under development !**
**It's not approved to widely use this mod until these lines are been removed !**

Modular Machinery Community: Refoxed (MMCR) is an **unofficial** NeoForge port and continuation of [Modular Machinery Community Edition](https://github.com/NovaEngineering-Source/ModularMachinery-Community-Edition) for newer Minecraft versions. It provides configurable multiblock machines, recipe processing, ports, controllers, a lot of DIY interfaces and integration of popular mods.

The transplantation license can be viewed [Here](https://github.com/NovaEngineering-Source/ModularMachinery-Community-Edition/issues/204).

## Downloads

Release artifacts are published on the [GitHub releases page](https://github.com/Nibelungorum/ModularMachinery-Community-Refoxed/releases), [CurseForge](https://www.curseforge.com/minecraft/mc-mods/modular-machinery-community-refoxed) and [Modrinth](https://modrinth.com/mod/modular-machinery-community-refoxed).

## Distribution

Distribution of this project is governed by the GNU General Public License version 3.0 (GPL-3.0).

You are permitted to include MMCR in your own ModPack freely, provided that your ModPack as a whole complies with the terms of GPL-3.0.

If you choose to fork MMCR and redistribute it in any modified form, you must retain all original copyright notices, disclaimers, and other author attributions in their entirety, and you must clearly indicate any modifications you have made to the original code.

No modified version may be implied as being endorsed by the original author without separate written permission.

## Installation

1. Install NeoForge for Minecraft.
2. Download the MMCR release JAR.
3. Place the JAR in the instance or server's `mods` directory.

MMCR has no additional required mod dependencies beyond NeoForge. 

Install interoperable mods separately to use their optional integrations.

## Reporting Issues

Report bugs and feature requests through the [issue tracker](https://github.com/Nibelungorum/ModularMachinery-Community-Refoxed/issues). Before opening an issue, search existing reports and reproduce the problem with the latest available version where possible.

Include the Minecraft and NeoForge versions, MMCR version, installed mods, logs or crash reports, and clear reproduction steps. Reports without enough information to investigate may be closed.

## Development

**JDK 25 is suggested**

### Building

```bash
chmod u+x run_data.sh
./run_data.sh
gradle build --no-daemon
```

The built JARs are written to `build/libs`.

### Test

Run the full validation sequence before submitting significant changes:

```bash
gradle test --no-daemon
gradle runGameTestServer --no-daemon
```

### KubeJS Development

MMCR **fully** supports `KubeJS` modifications, you can see `example` folder and learn how to use `KubeJS` to build your machine

The API usage and some examples are available on the MMCR wiki.

### Java API Development

The API is published to [HowXu's Maven repository](https://maven.howxu.cn/#/cn/howxu/ModularMachinery-Community-Refoxed) as part of release builds.

The API usage and some examples are available on the MMCR wiki.

## Contributing

Contributions are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening an issue or pull request.

## License And Notices

MMCR is licensed under the [GNU General Public License v3.0](LICENSE). It is a derivative work of **Modular Machinery Community Edition** and includes adapted **LowDragLib2** code under its original LGPL-3.0-or-later terms.

See [NOTICE](NOTICE) for source-derived attribution and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for the dependency and attribution inventory.

## Contact

- Email: [dev@howxu.cn](mailto:dev@howxu.cn)
- Source and issues: [GitHub](https://github.com/Nibelungorum/ModularMachinery-Community-Refoxed)
