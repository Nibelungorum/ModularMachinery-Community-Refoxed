# Dynamic Controller Client Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make startup-reserved controller blocks and items use a complete server-synchronized `MachineControllerSpec` snapshot without running KubeJS on clients.

**Architecture:** Add a copy-on-write client cache and a clientbound play payload for the full machine-ID-to-spec map. A shared controller model family reads the cache by machine ID; accepted snapshots clear model caches and trigger block/item redraw. Server sync is attached to login, dimension changes, and successful dynamic-content commits.

**Tech Stack:** Java 25, Minecraft 26.1.2, NeoForge play payloads and client model APIs, JUnit 5, Gradle.

## Global Constraints

- Do not add, update, or test KubeJS integration; it has not been migrated to Minecraft 26.1.2.
- Do not generate, distribute, validate, or reload textures, model JSON, language files, or translation keys.
- Texture identifiers remain opaque `MachineControllerSpec` data; missing resources use vanilla missing-texture behavior.
- Controllers are registered at startup; never add/remove Block, BlockItem, BlockEntity, or StateDefinition entries at runtime.
- Retain `FACING`, `ROLL_FACING`, `FORMED`, and `ACTIVE`; never rewrite existing world BlockStates after a spec update.
- Reject dynamic machines without startup-reserved controllers before sending a client snapshot.
- Send complete snapshots, not incremental mutations.

---

## File Structure

- Create: `src/main/java/cn/howxu/mmcr/client/controller/ControllerSpecCache.java` - validated immutable client snapshot, fallback, revision, listeners.
- Create: `src/main/java/cn/howxu/mmcr/internal/network/PktControllerSpecsPayload.java` - bounded payload codec and client handler.
- Create: `src/main/java/cn/howxu/mmcr/internal/network/ControllerSpecSync.java` - server snapshot construction and sending.
- Create: `src/main/java/cn/howxu/mmcr/client/controller/ControllerModelCache.java` - model variant cache keyed by machine ID and spec revision.
- Create: `src/main/java/cn/howxu/mmcr/client/controller/ControllerModelInvalidator.java` - clear model/item caches and mark controller chunks dirty.
- Modify: `src/main/java/cn/howxu/mmcr/Client.java`, `MMCR.java`, `ModBlocks.java`, `ModItems.java`, `ModelGen.java`.
- Modify: `src/main/java/cn/howxu/mmcr/internal/reload/DynamicContentReloadService.java` - reservation validation and successful-commit notification.
- Test: `src/test/java/cn/howxu/mmcr/client/controller/ControllerSpecCacheTest.java`, `src/test/java/cn/howxu/mmcr/internal/network/PktControllerSpecsPayloadTest.java`, and existing `MachineControllerRegistrationTest`/reload tests.

## Public Interfaces

```java
public final class ControllerSpecCache {
    public static Map<Identifier, MachineControllerSpec> snapshot();
    public static MachineControllerSpec specFor(Identifier machineId);
    public static long revision();
    public static boolean replaceSnapshot(Map<Identifier, MachineControllerSpec> snapshot);
    public static void addInvalidationListener(Runnable listener);
}

public final class ControllerSpecSync {
    public static Map<Identifier, MachineControllerSpec> createSnapshot();
    public static void sendTo(ServerPlayer player);
    public static void sendToAll(MinecraftServer server);
}
```

`ModBlocks` adds `public static boolean hasControllerFor(Identifier machineId)`. `ModItems` adds `public static Identifier machineIdForControllerItem(Item item)`.

### Task 1: Expose Startup Reservations

**Files:** `ModBlocks.java`, `ModItems.java`, `MachineControllerRegistrationTest.java`

- [ ] Add a failing test asserting the built-in blast furnace ID has a controller, its BlockItem maps back to `MMCR.id("blast_furnace")`, and `Items.AIR` maps to null.

```java
@Test
void controllerReservationsMapBlockAndItemToMachine() {
    Identifier id = MMCR.id("blast_furnace");
    assertThat(ModBlocks.hasControllerFor(id)).isTrue();
    assertThat(ModItems.machineIdForControllerItem(ModBlocks.controllerFor(id).get().asItem())).isEqualTo(id);
    assertThat(ModItems.machineIdForControllerItem(Items.AIR)).isNull();
}
```

- [ ] Run `./gradlew test --tests cn.howxu.mmcr.registry.MachineControllerRegistrationTest --no-daemon`; expect compilation failure.
- [ ] Implement `ModBlocks.hasControllerFor` by checking the derived controller holder is a `MachineControllerBlock` with the exact ID. Populate an immutable `Item -> Identifier` map in the existing `ModItems` static block-item registration loop.
- [ ] Run the same focused test; expect `BUILD SUCCESSFUL`.
- [ ] Commit with `git add src/main/java/cn/howxu/mmcr/registry/ModBlocks.java src/main/java/cn/howxu/mmcr/registry/ModItems.java src/test/java/cn/howxu/mmcr/registry/MachineControllerRegistrationTest.java && git commit -m "feat: expose controller reservations"`.

### Task 2: Add Client Spec Cache

**Files:** create `client/controller/ControllerSpecCache.java` and `ControllerSpecCacheTest.java`.

- [ ] Add a failing test for valid replacement, invalid null value rollback, listener invocation only on success, and removal fallback to `MachineControllerSpec.defaultsFor(id)`.

```java
@Test
void replacementIsAtomicAndMissingIdsUseDefaults() {
    Identifier id = Identifier.parse("mmcr:dynamic");
    MachineControllerSpec spec = testSpec(id);
    assertThat(ControllerSpecCache.replaceSnapshot(Map.of(id, spec))).isTrue();
    assertThat(ControllerSpecCache.specFor(id)).isEqualTo(spec);
    assertThat(ControllerSpecCache.replaceSnapshot(Collections.singletonMap(id, null))).isFalse();
    assertThat(ControllerSpecCache.specFor(id)).isEqualTo(spec);
    assertThat(ControllerSpecCache.replaceSnapshot(Map.of())).isTrue();
    assertThat(ControllerSpecCache.specFor(id)).isEqualTo(MachineControllerSpec.defaultsFor(id));
}
```

- [ ] Run `./gradlew test --tests cn.howxu.mmcr.client.controller.ControllerSpecCacheTest --no-daemon`; expect compilation failure.
- [ ] Implement a volatile immutable map. Validate IDs, specs, spec IDs and all four texture IDs before `Map.copyOf`; assign, increment revision and notify listeners only after validation. Catch/log listener failures individually.
- [ ] Run the focused test; expect `BUILD SUCCESSFUL`.
- [ ] Commit with `git add src/main/java/cn/howxu/mmcr/client/controller src/test/java/cn/howxu/mmcr/client/controller && git commit -m "feat: cache synced controller specs"`.

### Task 3: Add And Register Snapshot Payload

**Files:** create `PktControllerSpecsPayload.java` and its test; modify `MMCR.java`.

- [ ] Add a failing two-entry `STREAM_CODEC` round-trip test and a handler test proving cache replacement occurs on the client work queue.
- [ ] Run `./gradlew test --tests cn.howxu.mmcr.internal.network.PktControllerSpecsPayloadTest --no-daemon`; expect compilation failure.
- [ ] Implement `TYPE = new Type<>(MMCR.id("controller_specs"))`; encode the full `Identifier -> MachineControllerSpec` map with all eight fields, reject more than 4096 entries, and enqueue `ControllerSpecCache.replaceSnapshot(specs)` in `handle`.
- [ ] Register the payload beside `PktMachineStatePayload` with `playToClient`.
- [ ] Run the focused test; expect `BUILD SUCCESSFUL`.
- [ ] Commit with `git add src/main/java/cn/howxu/mmcr/internal/network/PktControllerSpecsPayload.java src/main/java/cn/howxu/mmcr/MMCR.java src/test/java/cn/howxu/mmcr/internal/network/PktControllerSpecsPayloadTest.java && git commit -m "feat: sync controller spec snapshots"`.

### Task 4: Build And Send Server Snapshots

**Files:** create `ControllerSpecSync.java`; modify `MMCR.java`, `DynamicContentReloadService.java`; extend reload tests.

- [ ] Add a failing test asserting `createSnapshot()` includes merged machines with reserved controllers and that an unreserved dynamic ID fails without replacing the old snapshot.
- [ ] Run `./gradlew test --tests cn.howxu.mmcr.internal.reload.DynamicContentReloadServiceTest --no-daemon`; expect failure.
- [ ] Implement `createSnapshot()` by iterating merged `MachineRegistry.getAll()` in order and including only IDs where `ModBlocks.hasControllerFor(id)`. Validate reservation in the reload service before registry mutation.
- [ ] Send complete snapshots on player login, dimension change, and successful dynamic commit; use `sendTo`/`sendToAll` only on the server thread.
- [ ] Run the focused reload tests; expect `BUILD SUCCESSFUL`.
- [ ] Commit with `git add src/main/java/cn/howxu/mmcr/internal/network/ControllerSpecSync.java src/main/java/cn/howxu/mmcr/MMCR.java src/main/java/cn/howxu/mmcr/internal/reload/DynamicContentReloadService.java src/test/java/cn/howxu/mmcr/internal/reload/DynamicContentReloadServiceTest.java && git commit -m "feat: send controller specs on reload"`.

### Task 5: Implement Shared Dynamic Model And Invalidation

**Files:** create `ControllerModelCache.java`, `ControllerModelInvalidator.java`; modify `Client.java`, `ModelGen.java`, `machine_controller_overlay.json`; extend client cache tests.

- [ ] Add a failing test proving the same ID/spec reuses a model key, while an accepted snapshot replacement changes the key and clears the old cache.
- [ ] Run `./gradlew test --tests cn.howxu.mmcr.client.controller.ControllerSpecCacheTest --no-daemon`; expect compilation failure.
- [ ] Implement model keys as machine ID plus immutable spec and revision. Register cache clear as a `ControllerSpecCache` listener. On the client thread invalidate the model/item manager supported by NeoForge 26.1.2 and mark loaded `MachineControllerBlock` positions for redraw.
- [ ] Change `ModelGen` so controller blockstates use one geometry model family and existing `MachineControllerVariants.full()` rotation dispatch; keep non-controller generation unchanged. Resolve item machine IDs through `ModItems.machineIdForControllerItem`. Do not alter resource contents beyond the geometry-only shared model contract.
- [ ] Run `./gradlew test --tests cn.howxu.mmcr.client.controller.ControllerSpecCacheTest --no-daemon && ./gradlew compileJava --no-daemon`; expect success.
- [ ] Commit with `git add src/main/java/cn/howxu/mmcr/client/controller src/main/java/cn/howxu/mmcr/Client.java src/main/java/cn/howxu/mmcr/datagen/ModelGen.java src/main/resources/assets/mmcr/models/block/machine_controller_overlay.json src/test/java/cn/howxu/mmcr/client/controller && git commit -m "feat: render controllers from synced specs"`.

### Task 6: Verify Boundaries

- [ ] Run `./gradlew test --tests cn.howxu.mmcr.registry.MachineControllerRegistrationTest --tests cn.howxu.mmcr.client.controller.ControllerSpecCacheTest --tests cn.howxu.mmcr.internal.network.PktControllerSpecsPayloadTest --tests cn.howxu.mmcr.internal.reload.DynamicContentReloadServiceTest --no-daemon`; expect success.
- [ ] Run `./gradlew compileJava --no-daemon`; expect success.
- [ ] In the normal development client/server setup, verify login and reconnect snapshot delivery, reload appearance replacement on an existing controller, horizontal/vertical rotation, and deleted-machine default appearance without missing-model crash.
- [ ] Run `git diff --check HEAD~5..HEAD` and inspect the client/network/reload/model diff. Confirm no KubeJS, resource transfer, translation handling, runtime registration mutation, or StateDefinition mutation.
- [ ] If corrections are needed, commit with `git add src/main/java src/test/java src/main/resources && git commit -m "fix: review dynamic controller client sync"`; otherwise do not create an empty commit.

## Self-Review

- Spec coverage: Tasks 1-2 cover startup reservations and atomic client cache; Task 3 covers complete payloads; Task 4 covers login, dimension, reload synchronization and reservation rejection; Task 5 covers shared model resolution and invalidation; Task 6 covers compilation and manual multiplayer checks.
- Placeholder scan: no unfinished markers or unspecified steps remain. Texture resources, translations, KubeJS, and runtime registry mutation are explicitly excluded.
- Type consistency: all tasks use the public cache, payload, sync, and registration lookup interfaces declared above.
