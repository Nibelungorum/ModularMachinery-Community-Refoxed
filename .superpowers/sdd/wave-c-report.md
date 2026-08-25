# Wave C Report

## Policy Ownership

- `CapabilityTransferPolicies` owns the built-in item, fluid, and energy policies.
- Each policy selects behavior from the `MachineCapability` identity and operates on its capability-owned `ResourceStorage` or `LongValueStorage`.
- `IOPortBlockEntity` owns side configuration, adaptive delay, candidate-side caching, and lifecycle scheduling only.
- `TransferResult` preserves transferred quantities as `long`; conversions to `int` remain at NeoForge `ResourceHandler` and `EnergyHandler` boundaries.
- Unknown capability types are skipped by policy lookup without an error.

## Snapshot Paths

- `MachineControllerRuntime.publishSnapshot()` publishes one immutable `ControllerRuntimeSnapshot` containing structure, components, capabilities, versions, module state, crafting state, and factory state.
- `ControllerSyncRuntime.machineState()` projects the published runtime into `MachineStateSnapshot`.
- `ControllerSyncRuntime.factoryState()` projects the published runtime into `FactorySnapshot`.
- `ControllerMenuState`, `MachineControllerMenu`, and `FactoryControllerMenu` read controller presentation data through published runtime snapshots and sync projections.
- `PktMachineStatePayload` and `PktFactoryControllerStatePayload` serialize complete machine/factory state snapshots.
- `MachineControllerDataProvider` reads the same snapshot path for Jade data.

## Client/Server Boundary

- Server-side controller runtime state remains authoritative.
- Server packets are built from the final published `ControllerRuntimeSnapshot` and projected immutable snapshots.
- Client packet handlers enqueue work and apply the received state to client runtime/menu state; they do not query live scheduler state for presentation.
- Client-open menus use standalone data slots and synchronized payload values until a block entity snapshot is available.

## Static Verification

- `rg "AutoIOTransferHandlers|instanceof ItemBusBlockEntity|instanceof FluidHatchBlockEntity|instanceof EnergyHatchBlockEntity" src/main/java/cn/howxu/mmcr/internal/autoio src/main/java/cn/howxu/mmcr/internal/menu src/main/java/cn/howxu/mmcr/internal/network src/main/java/cn/howxu/mmcr/compat/jade`: no matches.
- `rg "getMutableEnergyStorage|getMutableFluidStorage|liveComponents|foundModifiers|foundLevels" src/main/java/cn/howxu/mmcr/internal/menu src/main/java/cn/howxu/mmcr/internal/network src/main/java/cn/howxu/mmcr/compat/jade`: no matches.
- `git diff --check`: no output.
- No test, Gradle, compile, GameTest, check, build, or client command was run, per Wave C constraints.

## Cross-Wave Risks

- Tests were intentionally not updated in Wave C. Existing test sources still reference the removed `FactoryControllerSnapshot` type and old packet constructor shapes; Wave E must reconcile those tests with the new API.
- Build and runtime compatibility remain unverified because all Gradle and test commands are prohibited for this wave.
