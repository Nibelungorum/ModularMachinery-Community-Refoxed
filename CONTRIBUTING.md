# Contributing To MMCR

Contributions that improve Modular Machinery Community: Refoxed are welcome. Please keep changes focused, reproducible, and compatible with the project's supported Minecraft and NeoForge versions.

## Reporting An Issue

1. Search the [issue tracker](https://github.com/Nibelungorum/ModularMachinery-Community-Refoxed/issues) to confirm that the issue has not already been reported or resolved.
2. Use the latest available MMCR version and determine whether the behavior is already supported by Minecraft or MMCR configuration.
3. Open an issue using an available template when appropriate.
4. Include Minecraft, NeoForge, and MMCR versions; the relevant mod list; logs or a crash report; and steps that reproduce the problem.

Feature proposals should explain the use case and why existing Minecraft, MMCR, or widely used companion-mod functionality is insufficient. Discuss substantial changes before investing in an implementation.

## Submitting Changes

1. Open or identify an issue for non-trivial changes. Describe bugs with reproducible steps and discuss larger changes before implementation.
2. Fork the repository and create a topic branch. Do not work directly on the default branch.
3. Keep commits to logical units and write concise, imperative commit subjects, for example: `fix: preserve fluid hatch contents during reload`.
4. Follow the style of the files you change. Do not submit syntax-only cleanup or unrelated refactoring.
5. Check for whitespace errors before committing:

   ```bash
   git diff --check
   ```

6. Generate data when the changed content requires it, then run the relevant build checks:

   ```bash
   chmod u+x run_data.sh
   ./run_data.sh
   ./gradlew compileJava --no-daemon
   ./gradlew test --no-daemon
   ./gradlew runGameTestServer --no-daemon
   ```

7. In the pull request, describe the behavioral change and the testing performed. Include screenshots for user-interface changes and reproduction steps for bug fixes.

## Documentation Changes

Small documentation-only corrections may be submitted directly when an issue is unnecessary. Keep the pull request narrowly scoped and ensure links, commands, and version references remain accurate.

## Attribution

This guide is adapted from the [Applied Energistics 2 contribution guide](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/master/.github/CONTRIBUTING.md), which is licensed under LGPL-3.0-or-later. See [NOTICE](NOTICE) for the corresponding attribution.
