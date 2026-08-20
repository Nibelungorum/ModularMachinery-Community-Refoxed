# Task 11 Report

## Changes

- Kept the three deprecated event bases as external compatibility bridges.
- Made both `MachineDefinitionProvider.register` signatures default methods: canonical registration bridges one-way to the deprecated signature, whose default is an explicit no-op, so canonical-only providers compile and old-only providers remain usable without recursion.
- Added tests for a provider implementing only the canonical signature and for a deprecated listener receiving the actual canonical definition event; retained the ServiceLoader provider test that implements only the old signature.
- Updated KubeJS event comments to document `mmcr.startup`, `mmcr.server`, and `event.getAPI()` accurately.

## Reference Checks

- Old event names remain only in deprecated compatibility bases, the explicitly compatibility-only ServiceLoader test/provider method, and migration assertions.
- `ContentRegistrationCoordinator`, MMCR-owned builtins, KubeJS event payloads, and runtime adapters use canonical event types.
- `PublicRegistryBridge` and `PublicBuiltinRuntime.registerRecipes` have no remaining references.
- Direct registry writes remain only in coordinator-owned final commit, intentional KubeJS transaction-outside runtime fallback, and test/GameTest fixtures.

## Verification

- `./gradlew compileJava --no-daemon`: passed.
- Focused `PublicEventSubscribersTest`: passed, including canonical-only provider and deprecated-listener bridge coverage.
- Full `./gradlew test --no-daemon`: failed with 250 tests completed and 98 existing lifecycle/runtime initialization failures, including broad snapshot/bootstrap failures.
- `./gradlew runGameTestServer --no-daemon`: failed during mod startup because an existing built-in structure has a conflicting stage-2 predicate at `-1, -1, 0`.

The full-test and GameTest failures were not caused by the event compatibility/comment changes and were not suppressed or altered.
