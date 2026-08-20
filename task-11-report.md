# Task 11 Report

## Changes

- Kept the three deprecated event bases and the old `MachineDefinitionProvider` signature as external compatibility bridges.
- Documented that canonical startup collection dispatches old providers through the bridge.
- Added assignability coverage for all canonical events and retained the ServiceLoader provider test that implements only the old definition event.
- Updated KubeJS event comments to document `mmcr.startup`, `mmcr.server`, and `event.getAPI()` accurately.

## Reference Checks

- Old event names remain only in deprecated compatibility bases, the explicitly compatibility-only ServiceLoader test/provider method, and migration assertions.
- `ContentRegistrationCoordinator`, MMCR-owned builtins, KubeJS event payloads, and runtime adapters use canonical event types.
- `PublicRegistryBridge` and `PublicBuiltinRuntime.registerRecipes` have no remaining references.
- Direct registry writes remain only in coordinator-owned final commit, intentional KubeJS transaction-outside runtime fallback, and test/GameTest fixtures.

## Verification

- `./gradlew compileJava --no-daemon`: passed.
- Focused public API migration tests: passed.
- Full `./gradlew test --no-daemon`: failed with 44 existing lifecycle/runtime failures, including 3 KubeJS startup assertions and broader snapshot/bootstrap failures.
- `./gradlew runGameTestServer --no-daemon`: failed during mod startup because an existing built-in structure has a conflicting stage-2 predicate.

The full-test and GameTest failures were not caused by the event compatibility/comment changes and were not suppressed or altered.
