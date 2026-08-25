# Wave C Report

## Policy Ownership

- `CapabilityTransferPolicies` owns the built-in item, fluid, and energy policies.
- Each policy selects behavior from the `MachineCapability` identity and operates on its capability-owned `ResourceStorage` or `LongValueStorage`.
- `IOPortBlockEntity` owns side configuration, adaptive delay, candidate-side caching, and lifecycle scheduling only.
- `TransferResult` preserves transferred quantities as `long`; conversions to `int` remain at NeoForge `ResourceHandler` and `EnergyHandler` boundaries.
- Unknown capability types are skipped by policy lookup without an error.

## Snapshot Paths

- `MachineControllerRuntime.publishSnapshot()` publishes one immutable `ControllerRuntimeSnapshot` containing structure, versions, module state, crafting state, factory state, and captured presentation values; live components and capabilities remain owned by `ComponentRuntime` for execution only.
- `publishSnapshot()` captures component identities, capability IDs, long quantities, resource-slot values, energy totals, and primary fluid values into immutable presentation records; presentation consumers do not retain ports, schedulers, or mutable storage.
- `ControllerSyncRuntime.machineState()` projects the published runtime into a complete immutable `MachineStateSnapshot` containing IDs, status, progress, module state, level IDs, and presentation lists.
- `ControllerSyncRuntime.factoryState()` projects the published runtime into a `FactorySnapshot`, including level IDs and immutable factory lane values.
- `ControllerMenuState`, `MachineControllerMenu`, and `FactoryControllerMenu` read controller presentation data through the immutable projections; client menus prefer the received payload snapshot.
- `PktMachineStatePayload` serializes formed/active/progress, found levels, failure, recipe lock, module state, crafting status, structure loading, redstone pause, factory counters, energy totals, and primary fluid values; `PktFactoryControllerStatePayload` also serializes factory level IDs.
- `MachineControllerScreen`, `FactoryControllerScreen`, and `MachineControllerDataProvider` consume the menu/projection lists rather than owner block entities or live components.

## Client/Server Boundary

- Server-side controller runtime state remains authoritative.
- Server packets are built from the final published `ControllerRuntimeSnapshot` and projected immutable snapshots.
- Client packet handlers enqueue work, apply the received state to the client runtime when its controller exists, and always apply the complete machine payload to the open menu when it does not; menu/screen presentation does not query live scheduler state.
- Client-open menus use standalone data slots and synchronized payload values until a block entity snapshot is available.

## Static Verification

- `rg "AutoIOTransferHandlers|instanceof ItemBusBlockEntity|instanceof FluidHatchBlockEntity|instanceof EnergyHatchBlockEntity" src/main/java/cn/howxu/mmcr/internal/autoio src/main/java/cn/howxu/mmcr/internal/menu src/main/java/cn/howxu/mmcr/internal/network src/main/java/cn/howxu/mmcr/compat/jade`: no matches.
- `rg "getMutableEnergyStorage|getMutableFluidStorage|liveComponents|foundModifiers|foundLevels" src/main/java/cn/howxu/mmcr/internal/menu src/main/java/cn/howxu/mmcr/internal/network src/main/java/cn/howxu/mmcr/compat/jade`: no matches.
- `git diff --check`: no output.
- `TransferResult` now rejects inconsistent `successful`/`amount` pairs; machine payload enum decoding validates ordinal bounds.
- No test, Gradle, compile, GameTest, check, build, or client command was run, per Wave C constraints.

## Cross-Wave Risks

- Tests were intentionally not updated in Wave C. Existing test sources still reference the removed `FactoryControllerSnapshot` type and old packet constructor shapes; Wave E must reconcile those tests with the new API.
- Existing test sources also reference the removed menu module-state setter and pre-review payload shape; they remain untouched by the Wave C restriction.
- Existing test sources also reference the removed live component/capability snapshot fields and pre-review `RecipeSearchTask` constructor; they remain untouched by the Wave C restriction.
- Build and runtime compatibility remain unverified because all Gradle and test commands are prohibited for this wave.
