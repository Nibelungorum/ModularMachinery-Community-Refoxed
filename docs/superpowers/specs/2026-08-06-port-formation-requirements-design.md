# Port Formation Requirements Design

## Background

Current MMCR multiblock formation only validates the block pattern. `StructureMatcher` checks each `BlockArray` entry against the world state, and `MachineControllerBlockEntity.tryFormMachine` forms the machine immediately after a pattern match. Port components are collected afterwards for recipe execution and diagnostics.

MMCE reference behavior is similar: structure formation is driven by `TaggedPositionBlockArray.matches(...)` plus dynamic-pattern checks. Components are collected after formation through `updateComponents()`. MMCE contains a `ComponentRestriction` class with min/max fields, but it is not wired into machine loading or formation in the reference tree.

This feature intentionally adds a new MMCR-side formation rule: machines may declare required port counts. A structure can match its pattern but still remain unformed if its actual ports do not satisfy that machine's requirements.

## Goals

- Let each machine definition declare minimum and optional maximum counts for specific port kinds.
- Run the port-count check as part of formation, after the block pattern matches and before the controller is marked formed.
- Preserve current behavior for machines that do not declare port requirements.
- Keep requirements independent from recipes. A recipe may still fail later because storage contents, energy, output room, or selector tags are insufficient.
- Make failures diagnosable through logs first, with a clean path for future GUI/Jade display.

## Non-Goals

- Do not infer required ports from recipes.
- Do not change `BlockArray` pattern matching semantics.
- Do not require every machine to use input/output item, fluid, and energy ports.
- Do not implement MMCE `ComponentRestriction` compatibility unless a future config importer needs it.
- Do not add broad GUI work in the first implementation pass.

## Recommended Approach

Add an explicit port requirement model to the machine definition layer.

The new model should be separate from `MachineControllerSpec` because it describes the machine structure, not controller UI or behavior. The `Machine` interface should expose requirements with a default empty value, and `DynamicMachine` should carry the value as record state.

Suggested shape:

```java
public record PortRequirementSpec(Map<String, CountRange> requirements) {
    public static PortRequirementSpec none();
    public boolean isEmpty();
    public Optional<Failure> validate(PortCounts counts);
}
```

```java
public record CountRange(int min, OptionalInt max) {}
```

The map key should be the stable `IOPortKind.id()` string, such as `item_input_bus` or `energy_input_hatch`. This avoids serializing Java object identity and keeps the model usable by built-ins, KubeJS, data loading, tests, and future config importers.

## Data Model

### Machine Interface

`Machine` should add a default method:

```java
default PortRequirementSpec portRequirements() {
    return PortRequirementSpec.none();
}
```

This keeps existing machine implementations source-compatible except for the sealed `DynamicMachine` record updates required inside this project.

### DynamicMachine

`DynamicMachine` should gain a `PortRequirementSpec portRequirements` component and overload constructors so existing call sites can remain concise:

```java
public DynamicMachine(Identifier registryName, String localizedName, BlockArray pattern) {
    this(registryName, localizedName, pattern, MachineControllerSpec.defaultsFor(registryName), PortRequirementSpec.none());
}
```

The canonical constructor should reject `null` requirements.

### Count Range Semantics

- `min` defaults to `0`.
- `max` is absent by default, meaning no upper bound.
- `min < 0` is invalid.
- `max < min` is invalid.
- Unknown port keys are configuration errors at definition/load time when possible; if validation cannot resolve them early, formation should fail closed and log the unknown key.

## Formation Flow

Formation should remain anchored at `MachineControllerBlockEntity.tryFormMachine`.

Current flow:

1. Rotate candidate pattern.
2. Match blocks with `StructureMatcher.matchesRotated(...)`.
3. Call `onStructureFormed(...)`.
4. `onStructureFormed(...)` sets formed and calls `updateComponents()`.

New flow:

1. Rotate candidate pattern.
2. Match blocks with `StructureMatcher.matchesRotated(...)`.
3. Collect candidate port counts from the matched rotated pattern without mutating controller state.
4. Validate candidate counts against `candidate.portRequirements()`.
5. If validation fails, leave the controller unformed, store/log the failure, and return `false`.
6. If validation passes, call `onStructureFormed(...)` unchanged or with precomputed counts if useful.

The key design point is to avoid temporarily setting `foundMachine` or `components` before validation. The validation pass should be read-only and should inspect block entities at the matched pattern positions.

## Counting Rules

Only live machine component tiles in the matched pattern should count.

- Iterate over `rotatedPattern.pattern().keySet()`.
- Convert each relative position to world position with `controllerPos.offset(relativePos)`.
- Read the block entity at that world position.
- If it is an `IOPortBlockEntity`, count `port.kind().id()`.
- Ignore non-port machine components for this feature.
- Do not count ports outside the matched pattern.
- Do not count a pattern position whose block entity is missing or not loaded.

This matches the existing component collection boundary while keeping the new feature focused on ports.

## Failure Handling

First implementation should provide server-side diagnostics and leave room for UI integration.

Add a lightweight `lastFormationFailure` field on `MachineControllerBlockEntity`, for example a nullable value object containing:

- candidate machine id
- failed port key
- actual count
- required min
- optional required max
- reason: `MISSING`, `TOO_MANY`, or `UNKNOWN_PORT_KIND`

Behavior:

- Clear `lastFormationFailure` when a structure forms successfully.
- Clear it when the controller resets for unrelated reasons if the current structure no longer matches.
- Log a concise info/debug message when a pattern matched but port requirements failed.
- Do not spam logs every tick; log only when the failure value changes.

Future GUI/Jade work can display this field as “Missing required port: energy_input_hatch 0/1”.

## Built-In Blast Furnace

The built-in blast furnace currently has three `I` positions, each allowing any of six port blocks:

- `item_input_bus`
- `item_output_bus`
- `fluid_input_hatch`
- `fluid_output_hatch`
- `energy_input_hatch`
- `energy_output_hatch`

For the first port-requirement implementation, configure the built-in blast furnace with:

```text
item_input_bus: min 1
item_output_bus: min 1
energy_input_hatch: min 1
```

This makes the default blast furnace require exactly the practical ports needed by the current sample item+energy recipe flow while still allowing future machines to choose different requirements. No max bound should be set initially.

Because the pattern has only three `I` slots, these three minimums imply the default blast furnace must use one of each. If later recipes require fluids by default, the machine pattern or requirements should be expanded deliberately rather than hidden behind automatic inference.

## API and Configuration Surface

Initial implementation can be Java-side only because built-ins are currently Java-defined. However, the model should be shaped so it can later be exposed to KubeJS or JSON/data definitions.

Recommended helper methods:

```java
PortRequirementSpec.builder()
    .min("item_input_bus", 1)
    .min("item_output_bus", 1)
    .min("energy_input_hatch", 1)
    .build();
```

Avoid hardcoding the built-in blast furnace requirements directly inside `MachineControllerBlockEntity`. Requirements must live on the machine definition.

## Interactions With Existing Systems

### Structure Matcher

No changes. It remains responsible for block-state pattern matching only.

### Component Collection

`updateComponents()` remains the authoritative collector for runtime recipe components after formation. The new validation pass may share small helper logic for reading ports, but it should not populate `components` before the machine has passed formation.

### Recipe Execution

No changes. Recipe requirements still validate actual contents and capabilities via `RecipeCraftingContext`.

### Build Command and Multiblock Detector

Build/export tools should continue placing/exporting the pattern. They do not need to enforce port count requirements in the first pass, but documentation and future UX can surface requirements as metadata.

## Testing Plan

Add focused tests around controller formation and pure validation helpers.

Required tests:

- A machine with no port requirements forms exactly as before when the pattern matches.
- A machine with `energy_input_hatch min 1` does not form when the pattern has only non-energy ports.
- The same machine forms when the matched pattern contains one energy input hatch.
- A machine with multiple requirements reports the first missing or excessive port deterministically.
- The built-in blast furnace does not form with three arbitrary ports if they do not include item input, item output, and energy input.
- The built-in blast furnace forms with one item input bus, one item output bus, and one energy input hatch.

Validation should prefer existing lightweight stubs used by `MachineControllerBlockEntityTest` and `DefaultMachinesTest` rather than adding integration-heavy game tests.

## Migration and Compatibility

Machines without `PortRequirementSpec` keep current behavior.

Only the built-in blast furnace should gain requirements in the first pass. If tests or dev fixtures rely on arbitrary `I` ports, update those fixtures to satisfy the new built-in requirements or explicitly use a test machine with no requirements.

No saved data migration is required because requirements are definition-time metadata. Existing worlds may see previously formed blast furnaces become unformed after update if their three `I` slots are not item input, item output, and energy input. This is intentional for the built-in machine and should be mentioned in release notes if shipped to users.

## Implementation Notes

- Prefer a small pure helper for counting/validation so tests do not need a fully ticking controller.
- Keep the controller mutation boundary clear: no `setFormed(true)`, no `foundMachine` assignment, and no `components` mutation until requirements pass.
- Use `IOPortKind.id()` strings consistently; do not compare localized names.
- Keep log messages concise and include controller position, candidate machine id, port id, actual count, and required range.
- Do not add broad UI changes until the server-side rule is stable.

## Acceptance Criteria

- Port requirements are declared on machine definitions.
- Pattern match plus unsatisfied port requirements leaves the controller unformed.
- Pattern match plus satisfied port requirements forms the controller and preserves existing component collection behavior.
- Machines with no port requirements preserve existing behavior.
- Built-in blast furnace requires at least one item input bus, one item output bus, and one energy input hatch.
- Tests cover both passing and failing formation cases.
