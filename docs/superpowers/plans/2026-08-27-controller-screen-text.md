# Controller Screen Text Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a server-registered, controller-instance text API and runtime context for recipe lifecycle callbacks and custom machine ticks, synchronize changed snapshots to clients, and render the result through the shared client layout.

**Architecture:** Keep MMCR's existing parallel, thread, and progress presentation data unchanged. Add a server-side `ControllerScreenTextState` owned by each controller runtime, expose its keyed update/remove handle through a common runtime context, and send only external text lines in a versioned full snapshot. Both controller Screens merge the snapshot after their internal standard lines and delegate wrapping, scrolling, and pagination to the shared client model.

**Tech Stack:** Java records/interfaces, Minecraft `Component` and `ComponentSerialization.STREAM_CODEC`, NeoForge custom payloads, existing KubeJS startup/server event bridges, JUnit/Mockito project tests, and NeoForge GameTest.

**Scope Boundary:** This plan adds the controller-level context and text handle consumed by recipe lifecycle callbacks and custom machine ticks; it does not add those separate recipe/custom-machine callback registration systems. Existing and future callbacks invoke the same handle, while the KubeJS text handler added here provides an immediately testable server-side integration path.

## Global Constraints

- Keep rendering client-only; server code produces/synchronizes text data and never accesses Screen coordinates.
- Register external handlers only through Public API and KubeJS `startup`; do not expose controller text registration from `server_script` and do not serialize JavaScript callbacks.
- Keep MMCR's existing parallel-controller, parallelism, thread, and progress fields private to the internal standard presentation path.
- Use stable namespaced line IDs with keyed upsert and removal; do not use an unkeyed append list for tick-driven updates.
- Use `controller` and `operation` scopes; operation entries clear on recipe completion, failure, or cancellation.
- Sync a complete external text snapshot on revision changes; do not implement a line-operation patch protocol.
- Do not reuse `ControllerSpec.tooltip`, KubeJS `controllerTooltip`, or `InterfaceTooltips` for Screen text.
- Preserve existing user or collaborator changes and do not modify unrelated files.
- Add `@author howxu <dev@howxu.cn>` Javadoc to every new class.
- Do not add dependencies or change Minecraft, NeoForge, Gradle, or Java versions.
- After Java/network changes, run `./gradlew test --no-daemon` and then `./gradlew runGameTestServer --no-daemon` serially.
- Do not run `./gradlew runClient --no-daemon`.

## File Map

### New files

- `src/main/java/cn/howxu/mmcr/api/publicapi/controller/ControllerScreenTextScope.java`: Public scope enum.
- `src/main/java/cn/howxu/mmcr/api/publicapi/controller/ControllerScreenText.java`: Public keyed text-handle contract.
- `src/main/java/cn/howxu/mmcr/api/publicapi/controller/ControllerRuntimeContext.java`: Public controller identity and text-handle context passed to handlers.
- `src/main/java/cn/howxu/mmcr/api/publicapi/controller/ControllerScreenTextHandler.java`: Public functional handler contract.
- `src/main/java/cn/howxu/mmcr/api/publicapi/controller/ControllerScreenTextRegistry.java`: Server-side machine-filtered handler registry.
- `src/main/java/cn/howxu/mmcr/internal/runtime/ControllerScreenTextState.java`: Per-controller ordered state and revision tracking.
- `src/main/java/cn/howxu/mmcr/internal/runtime/ControllerScreenTextSnapshot.java`: Immutable network-ready external text snapshot.
- `src/main/java/cn/howxu/mmcr/internal/network/PktControllerScreenTextPayload.java`: Client-bound position/revision/text payload.
- `src/main/java/cn/howxu/mmcr/client/controller/ControllerScreenTextCache.java`: Client cache keyed by controller position.
- `src/main/java/cn/howxu/mmcr/client/gui/ControllerTextLine.java`: Shared logical line model for standard and external lines.
- `src/main/java/cn/howxu/mmcr/client/gui/ControllerScreenTextComposer.java`: Shared client merge and wrapping helper.
- `src/test/java/cn/howxu/mmcr/internal/runtime/ControllerScreenTextStateTest.java`: State, scope, ordering, and revision tests.
- `src/test/java/cn/howxu/mmcr/api/publicapi/controller/ControllerScreenTextRegistryTest.java`: Registry filtering, order, removal, and error isolation tests.
- `src/test/java/cn/howxu/mmcr/internal/network/PktControllerScreenTextPayloadTest.java`: Payload codec and revision tests.
- `src/test/java/cn/howxu/mmcr/client/controller/ControllerScreenTextCacheTest.java`: Client cache replacement tests.
- `src/test/java/cn/howxu/mmcr/compat/kubejs/ControllerScreenTextKubeJSTest.java`: KubeJS registration and handler bridge tests.

### Modified files

- `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerRuntime.java`: Own and expose controller text state/context.
- `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java`: Invoke registered server handlers, clear scopes, and synchronize changed text snapshots to observers.
- `src/main/java/cn/howxu/mmcr/internal/registration/ModEventRegistration.java`: Register the new client payload.
- `src/main/java/cn/howxu/mmcr/client/gui/AbstractScrollableTextScreen.java`: Centralize logical-to-visual line handling and text-scroll clamping.
- `src/main/java/cn/howxu/mmcr/client/gui/MachineControllerScreen.java`: Produce internal standard lines, merge external lines, and render shared visual lines.
- `src/main/java/cn/howxu/mmcr/client/gui/FactoryControllerScreen.java`: Use the same composition/cache path for factory details.
- `src/main/java/cn/howxu/mmcr/compat/kubejs/MMCRStartupEventJS.java`: Expose startup registration of controller text handlers.
- `src/main/java/cn/howxu/mmcr/compat/kubejs/ControllerScreenTextEventJS.java`: Adapt startup Public API context and append/remove operations to Rhino/KubeJS values.
- `src/main/java/cn/howxu/mmcr/compat/kubejs/MMCREvents.java`: Keep startup/server lifecycle registration connected to one registry.
- `src/test/java/cn/howxu/mmcr/client/gui/MachineControllerScreenTest.java`: Verify external lines follow standard lines and use common layout.
- `src/test/java/cn/howxu/mmcr/client/gui/FactoryControllerScreenTest.java`: Verify external lines and factory viewport layout.

## Task 1: Define Public Contracts and Ordered Runtime State

**Files:**
- Create `ControllerScreenTextScope.java`, `ControllerScreenText.java`, and `ControllerRuntimeContext.java`.
- Create `ControllerScreenTextState.java` and `ControllerScreenTextSnapshot.java`.
- Test `ControllerScreenTextStateTest.java`.

**Interfaces:**
- `ControllerScreenTextScope` exposes `CONTROLLER` and `OPERATION`.
- `ControllerScreenText` exposes:

```java
void append(ControllerScreenTextScope scope, Identifier lineId, Component text);
void remove(ControllerScreenTextScope scope, Identifier lineId);
void clear(ControllerScreenTextScope scope);
```

- `ControllerRuntimeContext` exposes `Identifier machineId()`, `BlockPos controllerPos()`, and `ControllerScreenText screenText()`.
- `ControllerScreenTextHandler` exposes `void apply(ControllerRuntimeContext context)`.
- `ControllerScreenTextState` implements `ControllerScreenText` and exposes `ControllerScreenTextSnapshot snapshot()`, `long revision()`, and `boolean dirty()`/`clearDirty()` for the server runtime.
- `ControllerScreenTextSnapshot` contains the revision and ordered immutable `Line` records with scope, namespaced ID, and `Component`.

- [ ] **Step 1: Write state behavior tests**

Test these exact behaviors in `ControllerScreenTextStateTest`:

```java
private ControllerScreenTextState state;

@BeforeEach
void setUp() {
    state = new ControllerScreenTextState();
}

@Test
void sameIdUpdatesInPlaceWithoutDuplicate() {
    state.append(CONTROLLER, id("example:first"), Component.literal("one"));
    state.append(CONTROLLER, id("example:first"), Component.literal("two"));

    assertEquals(List.of(Component.literal("two")), state.snapshot().lines().stream()
            .map(ControllerScreenTextSnapshot.Line::text).toList());
}

@Test
void removeAndScopeClearOnlyAffectRequestedEntries() {
    state.append(CONTROLLER, id("example:controller"), Component.literal("controller"));
    state.append(OPERATION, id("example:operation"), Component.literal("operation"));

    state.clear(OPERATION);

    assertEquals(List.of(id("example:controller")), state.snapshot().lines().stream()
            .map(ControllerScreenTextSnapshot.Line::lineId).toList());
}

@Test
void unchangedUpdateDoesNotAdvanceRevision() {
    state.append(CONTROLLER, id("example:status"), Component.literal("same"));
    long revision = state.revision();
    state.append(CONTROLLER, id("example:status"), Component.literal("same"));

    assertEquals(revision, state.revision());
}
```

- [ ] **Step 2: Run the focused state tests and confirm failure**

Run: `./gradlew test --no-daemon --tests '*ControllerScreenTextStateTest'`

Expected: FAIL because the new contracts and state implementation do not exist yet.

- [ ] **Step 3: Implement the minimal contracts and state**

Use a `LinkedHashMap` keyed by `(scope, lineId)` so first insertion determines order and later updates preserve it. Reject null scopes, IDs, and Components; require a namespace in every `Identifier`; copy all snapshot lists. Increment revision only when an insertion, replacement, removal, or scope clear changes the visible state. Keep the state runtime-only and do not add NBT serialization.

- [ ] **Step 4: Run focused tests and confirm pass**

Run: `./gradlew test --no-daemon --tests '*ControllerScreenTextStateTest'`

Expected: PASS.

- [ ] **Step 5: Commit the core state**

```bash
git add src/main/java/cn/howxu/mmcr/api/publicapi/controller src/main/java/cn/howxu/mmcr/internal/runtime/ControllerScreenTextState.java src/main/java/cn/howxu/mmcr/internal/runtime/ControllerScreenTextSnapshot.java src/test/java/cn/howxu/mmcr/internal/runtime/ControllerScreenTextStateTest.java
git commit -m "feat: add controller screen text state"
```

## Task 2: Add the Server Registry and Controller Runtime Context

**Files:**
- Create `ControllerScreenTextRegistry.java`.
- Modify `MachineControllerRuntime.java`.
- Test `src/test/java/cn/howxu/mmcr/api/publicapi/controller/ControllerScreenTextRegistryTest.java`.

**Interfaces:**
- `ControllerScreenTextRegistry.register(Identifier machineId, ControllerScreenTextHandler handler)` returns a `Registration` with `void unregister()`.
- `ControllerScreenTextRegistry.apply(ControllerRuntimeContext context)` invokes handlers registered for `context.machineId()` in registration order.
- `MachineControllerRuntime.screenText()` returns the instance state.
- `MachineControllerRuntime.runtimeContext()` returns a context backed by the controller position and currently configured machine.
- `MachineControllerRuntime.clearOperationText()` and `clearAllText()` perform lifecycle cleanup.
- Controller text registrations are startup-only and remain active for the server lifetime; no server-script registration or reload path exists.

- [ ] **Step 1: Add registry tests**

Cover machine filtering, registration order, handler failure isolation, and removal of one registration without removing another. Assert the handler receives the expected controller identity and can update the same instance state.

- [ ] **Step 2: Run focused registry tests and confirm failure**

Run: `./gradlew test --no-daemon --tests '*ControllerScreenText*'`

Expected: FAIL because the registry and runtime context are not implemented.

- [ ] **Step 3: Implement the registry and context**

Store handlers by machine ID in insertion-ordered collections. Define the removable `Registration` as a nested public interface with `void unregister()`. Catch `RuntimeException` around each handler, log it through `MMCR.LOG`, and continue. Keep registration mutation on the server thread. Public API registrations are startup-only and remain active for the server lifetime; do not add server-script sources or reload state.

Add the state and context to `MachineControllerRuntime`; construct the context from `MachineControllerBlockEntity.getBlockPos()` and the current configured machine registry name. Do not expose standard presentation fields through `ControllerRuntimeContext`.

- [ ] **Step 4: Run focused tests and confirm pass**

Run: `./gradlew test --no-daemon --tests '*ControllerScreenText*'`

Expected: PASS.

- [ ] **Step 5: Commit the registry integration**

```bash
git add src/main/java/cn/howxu/mmcr/api/publicapi/controller/ControllerScreenTextHandler.java src/main/java/cn/howxu/mmcr/api/publicapi/controller/ControllerScreenTextRegistry.java src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerRuntime.java src/test/java/cn/howxu/mmcr/api/publicapi/controller/ControllerScreenTextRegistryTest.java
git commit -m "feat: integrate controller screen text runtime"
```

## Task 3: Add the Versioned Client Payload and Cache

**Files:**
- Create `PktControllerScreenTextPayload.java`.
- Create `ControllerScreenTextCache.java`.
- Modify `ModEventRegistration.java`.
- Create payload/cache tests listed in the File Map.

**Interfaces:**
- `PktControllerScreenTextPayload(BlockPos controllerPos, long revision, List<ControllerScreenTextSnapshot.Line> lines)`.
- `ControllerScreenTextCache.replace(BlockPos pos, long revision, List<ControllerScreenTextSnapshot.Line> lines)` returns `false` for an older or equal revision and `true` for an accepted newer snapshot.
- `ControllerScreenTextCache.linesAt(BlockPos pos)` returns an immutable ordered list, or `List.of()` when no snapshot exists.
- `ControllerScreenTextCache.clear(BlockPos pos)` removes a controller cache entry.

- [ ] **Step 1: Write payload and cache tests**

Test `Component.translatable("example.progress", Component.literal("75%"))` round-trips through the payload codec, an equal revision is a no-op, older revisions are rejected, and missing positions return no external lines.

- [ ] **Step 2: Run focused network/cache tests and confirm failure**

Run: `./gradlew test --no-daemon --tests '*PktControllerScreenTextPayloadTest' --tests '*ControllerScreenTextCacheTest'`

Expected: FAIL because the payload and cache do not exist.

- [ ] **Step 3: Implement the payload codec**

Use `ComponentSerialization.STREAM_CODEC` for each Component, `Identifier.STREAM_CODEC` for each line ID, and explicit bounds of 1024 lines, 256 characters per line ID, and 64 KiB for the encoded text section. Encode scope, line ID, and Component in snapshot order. Validate position, non-negative revision, namespaces, and duplicate `(scope, lineId)` entries while decoding.

- [ ] **Step 4: Implement client cache replacement**

Keep a client-only map keyed by immutable `BlockPos`. Accept a snapshot only when its revision is newer than the cached revision; treat an equal revision as a no-op; replace the entire line list atomically and notify registered invalidation listeners only for an accepted newer snapshot. Keep cache operations safe when a client world is cleared.

- [ ] **Step 5: Register and handle the payload**

Add the payload to `ModEventRegistration.registerPayloads` with `playToClient`. Its handler must enqueue cache replacement on the client work queue and never touch server runtime objects.

- [ ] **Step 6: Run focused tests and confirm pass**

Run: `./gradlew test --no-daemon --tests '*PktControllerScreenTextPayloadTest' --tests '*ControllerScreenTextCacheTest'`

Expected: PASS.

- [ ] **Step 7: Commit the network layer**

```bash
git add src/main/java/cn/howxu/mmcr/internal/network/PktControllerScreenTextPayload.java src/main/java/cn/howxu/mmcr/client/controller/ControllerScreenTextCache.java src/main/java/cn/howxu/mmcr/internal/registration/ModEventRegistration.java src/test/java/cn/howxu/mmcr/internal/network/PktControllerScreenTextPayloadTest.java src/test/java/cn/howxu/mmcr/client/controller/ControllerScreenTextCacheTest.java
git commit -m "feat: sync controller screen text"
```

## Task 4: Wire Server Updates to Open Controller Menus

**Files:**
- Modify `MachineControllerBlockEntity.java`.
- Verify the existing `MachineControllerMenu.controllerPos()` and `FactoryControllerMenu.controllerPos()` accessors; no menu API change is expected.
- Extend `src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntityTest.java` and `src/test/java/cn/howxu/mmcr/internal/network/PktControllerScreenTextPayloadTest.java` with server update assertions.

**Interfaces:**
- `MachineControllerBlockEntity.sendControllerScreenText(ServerPlayer player)` sends the current position/revision snapshot.
- `MachineControllerBlockEntity.syncOpenControllerScreenText()` sends only when the state revision differs from the last sent revision.

- [ ] **Step 1: Add tests for open-menu audience and no-op updates**

Verify that a changed line sends to players whose menu points at the controller, a closed menu does not receive per-tick text traffic, and setting the same Component twice does not trigger a second snapshot.

- [ ] **Step 2: Run the focused runtime/network tests and confirm failure**

Run: `./gradlew test --no-daemon --tests '*MachineControllerBlockEntityTest' --tests '*PktControllerScreenTextPayloadTest'`

Expected: FAIL for the new send/revision behavior.

- [ ] **Step 3: Send the current snapshot when either controller menu opens**

Call the new send method from the existing ordinary-controller open path and factory-controller open path. Use the menu's controller position as the client cache key. Send an empty snapshot when external text is cleared so stale client lines disappear.

- [ ] **Step 4: Flush dirty snapshots after runtime callbacks**

In `MachineControllerBlockEntity.tickRuntimeWork`, invoke the registry against the runtime context after machine runtime work has run and before the final publish/sync block. At the end of the same server runtime update path, send the full snapshot once if the text revision changed. Reuse the existing open-menu iteration and controller-position matching used by `syncOpenFactoryControllerMenus`; do not add a packet per `append` call or invoke handlers from client Screen methods.

- [ ] **Step 5: Handle unform and reset**

Clear `OPERATION` entries when active recipe/factory work transitions to inactive, including failure and cancellation paths. Clear all external entries from `resetMachine` and controller unbinding, while keeping `CONTROLLER` entries across ordinary ticks. Increment revision and send an empty snapshot to current observers when the controller is reset or loses its configured machine. Do not add server-script reload handling for controller text.

- [ ] **Step 6: Run focused tests and confirm pass**

Run: `./gradlew test --no-daemon --tests '*MachineControllerBlockEntityTest' --tests '*PktControllerScreenTextPayloadTest'`

Expected: PASS.

- [ ] **Step 7: Commit the server sync integration**

```bash
git add src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntityTest.java src/test/java/cn/howxu/mmcr/internal/network/PktControllerScreenTextPayloadTest.java
git commit -m "feat: update controller text snapshots at runtime"
```

## Task 5: Add Startup Public API and KubeJS Registration Bridges

**Files:**
- Create `ControllerScreenTextEventJS.java`.
- Modify `MMCRStartupEventJS.java` and `MMCREvents.java` only as needed for startup registration.
- Test `ControllerScreenTextKubeJSTest.java` and extend `PluginBindingTest.java` only for the new binding.

**Interfaces:**
- The startup event object exposes a method with this behavior:

```java
void registerControllerScreenText(String machineId, Consumer<ControllerScreenTextEventJS> handler);
```

- `ControllerScreenTextEventJS` exposes machine ID, controller position, and KubeJS-safe operations:

```java
void append(String scope, String lineId, Component text);
void appendTranslatable(String scope, String lineId, String key, Object... args);
void remove(String scope, String lineId);
```

- KubeJS registration is converted to `ControllerScreenTextHandler` and stored in the same registry as Public API handlers.

- [ ] **Step 1: Write bridge tests**

Test that startup registrations reach the common registry, invalid scope/ID input throws a KubeJS-facing error, and `appendTranslatable` creates a translatable Component with all supplied arguments. There is no server-script controller text registration or reload behavior to test.

- [ ] **Step 2: Run focused KubeJS tests and confirm failure**

Run: `./gradlew test --no-daemon --tests '*ControllerScreenTextKubeJSTest' --tests '*PluginBindingTest'`

Expected: FAIL because the new event wrapper and registration methods do not exist.

- [ ] **Step 3: Implement the KubeJS event wrapper**

Convert KubeJS strings to `Identifier` and `ControllerScreenTextScope`, reject null/blank values, construct `Component.translatable(key, args)`, and forward all mutations to the context's text handle. Do not expose internal standard values or packet objects.

- [ ] **Step 4: Connect startup registration**

Add the registration method only to `MMCRStartupEventJS`. Keep these handlers in the persistent startup registry for the server lifetime; do not add a server-script source, token, or reload hook.

- [ ] **Step 5: Verify KubeJS startup binding**

Register the startup event method through the existing `MMCREvents` binding path and invoke the registry handler with a test context. Assert that the handler runs server-side and its output is available to the controller snapshot.

- [ ] **Step 6: Run focused tests and confirm pass**

Run: `./gradlew test --no-daemon --tests '*ControllerScreenTextKubeJSTest' --tests '*PluginBindingTest'`

Expected: PASS.

- [ ] **Step 7: Commit the registration bridges**

```bash
git add src/main/java/cn/howxu/mmcr/compat/kubejs/MMCRStartupEventJS.java src/main/java/cn/howxu/mmcr/compat/kubejs/ControllerScreenTextEventJS.java src/main/java/cn/howxu/mmcr/compat/kubejs/MMCREvents.java src/test/java/cn/howxu/mmcr/compat/kubejs/ControllerScreenTextKubeJSTest.java src/test/java/cn/howxu/mmcr/compat/kubejs/PluginBindingTest.java
git commit -m "feat: expose controller text to kubejs"
```

## Task 6: Unify Client Text Composition and Layout

**Files:**
- Create `ControllerTextLine.java` and `ControllerScreenTextComposer.java`.
- Modify `AbstractScrollableTextScreen.java`, `MachineControllerScreen.java`, and `FactoryControllerScreen.java`.
- Reuse the existing `MachineControllerMenu.controllerPos()` and `FactoryControllerMenu.controllerPos()` accessors.
- Extend `MachineControllerScreenTest.java` and `FactoryControllerScreenTest.java`.

**Interfaces:**
- `ControllerTextLine` stores a Component and the internal render color.
- `ControllerScreenTextComposer.merge(List<ControllerTextLine> standard, List<ControllerScreenTextSnapshot.Line> external)` returns standard lines followed by external lines using the default external color.
- `ControllerScreenTextComposer.wrap(Font font, List<ControllerTextLine> lines, int width)` returns immutable `VisualLine` records containing the wrapped `FormattedCharSequence` and render color.
- Each Screen supplies its standard lines and receives the same external cache data by controller position.

- [ ] **Step 1: Add composition/layout tests**

Cover standard-before-external ordering, external line replacement from the cache, long text wrapping at the ordinary viewport width, factory viewport wrapping, and scroll offset clamping after an external snapshot shrinks.

- [ ] **Step 2: Run focused Screen tests and confirm failure**

Run: `./gradlew test --no-daemon --tests '*MachineControllerScreenTest' --tests '*FactoryControllerScreenTest'`

Expected: FAIL for the new shared line model and external composition assertions.

- [ ] **Step 3: Implement the shared logical line model**

Move the duplicated status-line representation out of the individual Screen classes. Keep standard colors internal. Convert external snapshot lines to the default status color and append them after standard lines.

- [ ] **Step 4: Make the base scroll model consume visual lines**

Add a protected line-provider contract to `AbstractScrollableTextScreen`, calculate `scrollableTextLineCount()` from the wrapped visual-line list, and clamp the scroll offset whenever the list changes. Do not let external lines supply their own viewport, spacing, scale, or coordinates.

- [ ] **Step 5: Update both Screens**

Replace each Screen's direct `detailLines(menu)` count/render duplication with the shared composition path. The ordinary Screen continues to source its standard lines from `MachineControllerMenu`; the factory Screen continues to source its standard lines from `FactoryControllerMenu`. Both append `ControllerScreenTextCache.linesAt(menu.controllerPos())`.

- [ ] **Step 6: Run focused Screen tests and confirm pass**

Run: `./gradlew test --no-daemon --tests '*MachineControllerScreenTest' --tests '*FactoryControllerScreenTest'`

Expected: PASS.

- [ ] **Step 7: Commit the client composition**

```bash
git add src/main/java/cn/howxu/mmcr/client/gui/AbstractScrollableTextScreen.java src/main/java/cn/howxu/mmcr/client/gui/MachineControllerScreen.java src/main/java/cn/howxu/mmcr/client/gui/FactoryControllerScreen.java src/main/java/cn/howxu/mmcr/client/gui/ControllerTextLine.java src/main/java/cn/howxu/mmcr/client/gui/ControllerScreenTextComposer.java src/main/java/cn/howxu/mmcr/client/controller/ControllerScreenTextCache.java src/test/java/cn/howxu/mmcr/client/gui
git commit -m "feat: compose controller screen text"
```

## Task 7: Validate Runtime Lifecycle and Full Integration

**Files:**
- Add or extend `src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntityTest.java`.
- Add or extend `src/gametest/java/cn/howxu/mmcr/ControllerTickGameTest.java` and `src/gametest/java/cn/howxu/mmcr/MultiFactoryControllerGameTest.java`.
- Review all changed files from Tasks 1-6; do not modify unrelated code.

**Interfaces:**
- A test controller runtime can call `context.screenText().append(OPERATION, id, component)` and observe the line in the next payload.
- A custom tick can update a controller-scoped line repeatedly without duplicate entries.

- [ ] **Step 1: Add runtime lifecycle tests**

Test operation text removal after normal completion, failure, and cancellation; controller text removal on reset; custom tick updates; unchanged snapshots not resent; and a handler exception not interrupting the runtime update.

- [ ] **Step 2: Add GameTest coverage**

Use the existing controller fixtures to form a controller, invoke the server-side runtime update path, and assert the instance text snapshot changes and is cleared on reset. For the factory fixture, assert the same external snapshot is available while the factory menu is active. Do not assert raw capacity numbers; assert behavior such as line replacement, cleanup, and update propagation.

- [ ] **Step 3: Run focused tests**

Run: `./gradlew test --no-daemon --tests '*ControllerScreenText*' --tests '*MachineControllerBlockEntityTest' --tests '*MachineControllerScreenTest' --tests '*FactoryControllerScreenTest'`

Expected: PASS.

- [ ] **Step 4: Run the required full test suite serially**

Run first: `./gradlew test --no-daemon`

Expected: PASS.

After the test task exits, run: `./gradlew runGameTestServer --no-daemon`

Expected: PASS.

- [ ] **Step 5: Inspect final diff and status**

Run:

```bash
rtk git status --short
rtk git diff --check HEAD~7..HEAD
rtk git log --oneline -10
```

Confirm only the planned source, test, design, and plan files changed; confirm no build output, cache, log, IDE, `docs` upload, or `.superpowers` file was added outside the explicitly committed design/plan documents.

- [ ] **Step 6: Commit integration verification**

```bash
git add src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntityTest.java src/gametest/java/cn/howxu/mmcr/ControllerTickGameTest.java src/gametest/java/cn/howxu/mmcr/MultiFactoryControllerGameTest.java
git commit -m "test: cover controller screen text lifecycle"
```

## Spec Coverage Review

- Startup registration and server-only callback execution: Tasks 2 and 5.
- Common controller runtime context for recipe and custom tick callbacks: Task 2.
- Stable line IDs, keyed upsert, removal, and scopes: Task 1.
- Runtime dirty tracking and per-controller state: Tasks 1, 2, and 4.
- Versioned full snapshot synchronization: Tasks 3 and 4.
- Existing standard fields kept private and on existing payloads: Task 6.
- Unified ordinary/factory client composition and pagination: Task 6.
- Error isolation and payload bounds: Tasks 2, 3, and 5.
- Controller text has no server-script reload support: Tasks 2, 4, and 5.
- Unit, network, Screen, and GameTest validation: Task 7.

## Plan Self-Review

- No implementation task depends on a client callback being serialized from a script.
- Public API names and method signatures are consistent across the registry, runtime context, and KubeJS wrapper.
- `ControllerScreenTextState` owns only external lines; standard runtime fields remain in the existing snapshots and Menu code.
- Full snapshot replacement handles add, update, remove, and reset without a patch-order protocol.
- Text wrapping is performed client-side after receiving logical Components, so server and client font differences do not corrupt pagination.
- The plan intentionally does not implement recipe lifecycle or custom-machine registration APIs that are not yet present; it provides the shared controller context and text handle those future callbacks consume. Those functions are registered at startup only and never reload from `server_script`.
