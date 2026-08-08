# Port Tier Requirements Design

## Goal

Allow multiblock machines to accept interface ports from any registered tier by default, and optionally require minimum tiers per IO category during structure formation.

## Context

IO ports are modeled as `IOPortKind` values registered by `PortKinds`. Item buses, fluid hatches, and energy hatches each expose their size through `ItemBusSize`, `FluidHatchSize`, or `EnergyHatchSize`. Current multiblock formation checks only block predicates and port counts; a machine can require `energy_input_hatch` by id, but it cannot express that any energy input hatch of a minimum tier should satisfy formation.

The requested behavior is formation-only. Recipe execution and component lookup should remain unchanged for this iteration.

## Chosen Approach

Add a new machine-level API, `PortTierRequirementSpec`, separate from existing `PortRequirementSpec`.

This keeps count requirements and tier requirements independent:

- `PortRequirementSpec` continues to count exact port kind ids.
- `PortTierRequirementSpec` validates the actual `IOPortKind` instances found inside the matched multiblock.
- Machines that do not opt in keep existing behavior through `PortTierRequirementSpec.none()`.

## API Shape

`Machine` gains a default method:

```java
default PortTierRequirementSpec portTierRequirements() {
    return PortTierRequirementSpec.none();
}
```

`DynamicMachine` gains a `PortTierRequirementSpec portTierRequirements` record field. Existing constructors should keep compiling by forwarding `PortTierRequirementSpec.none()`.

`PortTierRequirementSpec` provides a builder with category-specific methods:

```java
PortTierRequirementSpec.builder()
        .anyItemInput()
        .minItemInput(ItemBusSize.NORMAL)
        .minFluidOutput(FluidHatchSize.HUGE)
        .minEnergyInput(EnergyHatchSize.LUDICROUS)
        .build();
```

`any*` means at least one port matching that capability category and direction exists at any tier. `min*` means at least one matching port exists with size ordinal greater than or equal to the requested enum value.

## Formation Validation

`MachineControllerBlockEntity` should collect `IOPortKind` values from the matched structure positions and call:

```java
foundMachine.portTierRequirements().validate(kinds)
```

Validation runs after block-pattern matching and existing port-count validation. If it fails, formation fails and the failure remains observable through the existing formation-failure path. The failure message should include a stable requirement id such as `energy_input_hatch>=ludicrous` so wrench/debug output can explain why the machine rejected the structure.

Validation must run both when initially forming a structure and when a cached formed structure is rechecked.

## Matching All Tiers In Patterns

Default machines should stop hardcoding only the normal port blocks in pattern predicates where the intent is “any tier of this port family”. Add a small helper to build a `BlockPredicate.AnyOf` from registered `PortKinds` by IO category and direction. This helper can live in `DefaultMachines` for now because the immediate need is built-in test machines, and exposing a broader pattern predicate API can be deferred until KubeJS needs it.

## Default Machine Test Matrix

Update built-in machines to exercise mixed tier combinations in game:

- Blast furnace: `energy input >= LUDICROUS`, `item input >= NORMAL`; other IO slots may use any tier.
- Alloy furnace: `item input >= REINFORCED`, `energy input >= BIG`.
- Cracker: `fluid output >= HUGE`, `energy input >= REINFORCED`, `item input >= NORMAL`.
- Reactor: `energy output >= ULTIMATE`, `fluid input >= BIG`, `fluid output >= LUDICROUS`.

These combinations are intentionally varied so in-game testing covers item, fluid, and energy categories as well as input and output directions.

## Testing

Add unit tests for `PortTierRequirementSpec` covering:

- empty spec accepts empty ports;
- `any*` accepts any matching tier;
- `min*` rejects lower tiers;
- `min*` accepts exact and higher tiers;
- wrong direction or wrong capability category does not satisfy a requirement.

Add controller formation tests covering:

- a machine with a minimum energy tier rejects a normal energy hatch;
- the same machine forms with a high enough energy hatch;
- cached formed structure revalidation fails after replacing a high-tier hatch with a lower-tier hatch.

Add default machine tests that verify each built-in machine has the intended tier requirements.

## Non-Goals

- Do not change recipe search or component aggregation.
- Do not add KubeJS builder methods in this iteration unless implementation requires touching that surface to preserve compatibility.
- Do not redesign `PortRequirementSpec` or existing exact-id count semantics.
