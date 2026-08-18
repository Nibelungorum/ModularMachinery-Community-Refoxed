# Modular Machinery Community: Refoxed

**Currently, this mod is still under development!**

Modular Machinery Community: Refoxed (MMCR) is an **unofficial** NeoForge port and continuation of [Modular Machinery Community Edition](https://github.com/NovaEngineering-Source/ModularMachinery-Community-Edition) for newer Minecraft versions. It provides configurable multiblock machines, recipe processing, ports, controllers, and integration of popular mods like JEI and KubeJS.

The transplantation license can be viewed [Here](https://github.com/NovaEngineering-Source/ModularMachinery-Community-Edition/issues/204).

## Compatibility

- Minecraft `26.1.2`
- NeoForge `26.1.2.84` or newer within the supported Minecraft version
- Java 25 for development

Optional integrations are available for JEI, KubeJS, and Jade.

## Downloads

Release artifacts are published on the [GitHub releases page](https://github.com/Nibelungorum/ModularMachinery-Community-Refoxed/releases), [CurseForge](https://www.curseforge.com/minecraft/mc-mods/modular-machinery-community-refoxed) and [Modrinth](https://modrinth.com/mod/modular-machinery-community-refoxed).

## Installation

1. Install NeoForge for Minecraft `26.1.2`.
2. Download the MMCR release JAR.
3. Place the JAR in the instance or server's `mods` directory.

MMCR has no additional required mod dependencies beyond NeoForge. Install JEI, KubeJS, or Jade separately to use their optional integrations.

## Reporting Issues

Report bugs and feature requests through the [issue tracker](https://github.com/Nibelungorum/ModularMachinery-Community-Refoxed/issues). Before opening an issue, search existing reports and reproduce the problem with the latest available version where possible.

Include the Minecraft and NeoForge versions, MMCR version, installed mods, logs or crash reports, and clear reproduction steps. Reports without enough information to investigate may be closed.

## Building

```bash
chmod u+x run_data.sh
./run_data.sh
./gradlew build --no-daemon
```

The built JARs are written to `build/libs`. Run the full validation sequence before submitting significant changes:

```bash
./gradlew test --no-daemon
./gradlew runGameTestServer --no-daemon
```

The API is published to [HowXu's Maven repository](https://maven.howxu.cn/#/cn/howxu) as part of release builds.

## Contributing

Contributions are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening an issue or pull request.

## License And Notices

MMCR is licensed under the [GNU General Public License v3.0](LICENSE). It is a derivative work of Modular Machinery Community Edition and includes adapted LowDragLib2 code under its original LGPL-3.0-or-later terms. See [NOTICE](NOTICE) for source-derived attribution and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for the dependency and attribution inventory.

## Contact

- Email: [dev@howxu.cn](mailto:dev@howxu.cn)
- Source and issues: [GitHub](https://github.com/Nibelungorum/ModularMachinery-Community-Refoxed)
