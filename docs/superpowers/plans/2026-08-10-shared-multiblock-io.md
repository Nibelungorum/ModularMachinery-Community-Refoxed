# Shared Multiblock IO Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow formed multiblocks to safely share declared IO ports, with atomic formation claims and fair, transactional resource scheduling across controllers and factory lanes.

**Architecture:** Introduce a server-world claim registry which owns component-to-controller relationships and derives connected shared-IO resource domains. Controllers atomically claim stateful components before becoming formed; IO ports retain every owner for rendering. A level-end coordinator batches start, tick, and output requests per domain, validates structure/domain versions on the server thread, and grants requests in rotating lane order so shared handlers are mutated only once per transaction.

**Tech Stack:** Java 21, NeoForge 26.1.2, Minecraft `BlockEntity` capabilities, JUnit 5/AssertJ unit tests, NeoForge GameTest, Gradle.

## Global Constraints

- Do not change Minecraft, NeoForge, Gradle, or existing dependency versions.
- Do not run `./gradlew runClient --no-daemon`.
- Keep all real item, fluid, and energy handler mutation on the server thread.
- Do not create claims for ordinary casing or other stateless structure blocks.
- Existing controllers without shared IO must retain their current start, progress, and factory-parallel behavior.
- New Java classes require `@author howxu <dev@howxu.cn>` Javadoc.
- Use the existing `structureVersion` to invalidate controller-local recipe work; add domain generation validation for shared work.

---

## File Structure

| File | Responsibility |
| --- | --- |
| `src/main/java/cn/howxu/mmcr/internal/multiblock/ComponentClaimPolicy.java` | Declares whether a stateful component is exclusive or shared/serialized. |
| `src/main/java/cn/howxu/mmcr/internal/multiblock/StructureClaimRegistry.java` | Per-server-level component claims, atomic validation/commit/release, resource-domain connectivity, and domain generations. |
| `src/main/java/cn/howxu/mmcr/internal/multiblock/SharedIoCoordinator.java` | Batches start/tick/finish requests and resolves each domain fairly at level-end. |
| `src/main/java/cn/howxu/mmcr/api/recipe/MachineComponentTile.java` | Supplies a component's claim policy without coupling API callers to block-entity subclasses. |
| `src/main/java/cn/howxu/mmcr/internal/tile/IOPortBlockEntity.java` | Uses shared/serialized policy and stores a deterministic set of linked controller positions for formed rendering. |
| `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java` | Collects claimable component positions, atomically claims before formation, releases on reset/removal, and submits recipe work to the coordinator. |
| `src/main/java/cn/howxu/mmcr/internal/recipe/RecipeThread.java` | Holds pending coordinator requests and applies successful start/tick/finish results only after validation. |
| `src/main/java/cn/howxu/mmcr/internal/recipe/FactoryRecipeThread.java` | Adds a stable factory lane key used by fair scheduling. |
| `src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContext.java` | Exposes transactional start/tick/output operations used only by coordinator resolution. |
| `src/main/java/cn/howxu/mmcr/internal/event/SharedIoEvents.java` | Registers level-end coordinator resolution and lifecycle cleanup. |
| `src/main/java/cn/howxu/mmcr/MMCR.java` | Registers the new event handlers alongside existing common event handlers. |
| `src/test/java/cn/howxu/mmcr/internal/multiblock/StructureClaimRegistryTest.java` | Claim, release, connected-domain, generation, and exclusive conflict coverage. |
| `src/test/java/cn/howxu/mmcr/internal/multiblock/SharedIoCoordinatorTest.java` | Deterministic fairness, partial start, stale request, and bounded allocation coverage. |
| `src/test/java/cn/howxu/mmcr/internal/tile/MachinePortAppearanceTest.java` | Multi-owner port appearance and owner-specific unlink coverage. |
| `src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntityTest.java` | Formation conflict and shared-port lifecycle coverage. |
| `src/test/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContextTest.java` | Shared handler transaction coverage for items, fluids, energy, and blocked outputs. |
| `src/gametest/java/cn/howxu/mmcr/SharedMultiblockIoGameTest.java` | In-world two-controller shared-port regression coverage. |

### Task 1: Add Atomic Component Claims And Resource Domains

**Files:**
- Create: `src/main/java/cn/howxu/mmcr/internal/multiblock/ComponentClaimPolicy.java`
- Create: `src/main/java/cn/howxu/mmcr/internal/multiblock/StructureClaimRegistry.java`
- Create: `src/test/java/cn/howxu/mmcr/internal/multiblock/StructureClaimRegistryTest.java`

**Interfaces:**
- Consumes: immutable `BlockPos` controller/component coordinates.
- Produces: `ComponentClaimPolicy`, `StructureClaimRegistry.Claim`, `StructureClaimRegistry.ClaimResult`, `StructureClaimRegistry.ResourceDomain`, `claim`, `release`, `domainFor`, and `generationFor`.

- [ ] **Step 1: Write failing registry tests for exclusive rejection and all-or-nothing claim commit**

```java
@Test
void exclusiveConflictDoesNotLeavePartialClaims() {
    BlockPos first = new BlockPos(0, 64, 0);
    BlockPos second = new BlockPos(4, 64, 0);
    BlockPos freePort = new BlockPos(1, 64, 0);
    BlockPos occupiedScheduler = new BlockPos(2, 64, 0);
    StructureClaimRegistry registry = new StructureClaimRegistry();

    assertThat(registry.claim(first, List.of(
            new Claim(freePort, ComponentClaimPolicy.SHARED_SERIALIZED),
            new Claim(occupiedScheduler, ComponentClaimPolicy.EXCLUSIVE)))).isEqualTo(ClaimResult.success());

    ClaimResult result = registry.claim(second, List.of(
            new Claim(freePort, ComponentClaimPolicy.SHARED_SERIALIZED),
            new Claim(occupiedScheduler, ComponentClaimPolicy.EXCLUSIVE)));

    assertThat(result.accepted()).isFalse();
    assertThat(result.conflict().componentPos()).isEqualTo(occupiedScheduler);
    assertThat(registry.ownersOf(freePort)).containsExactly(first);
    assertThat(registry.ownersOf(occupiedScheduler)).containsExactly(first);
}

@Test
void sharedClaimsMergeTransitiveControllersIntoOneDomainAndReleaseSplitsIt() {
    BlockPos a = new BlockPos(0, 64, 0);
    BlockPos b = new BlockPos(10, 64, 0);
    BlockPos c = new BlockPos(20, 64, 0);
    BlockPos p = new BlockPos(5, 64, 0);
    BlockPos q = new BlockPos(15, 64, 0);
    StructureClaimRegistry registry = new StructureClaimRegistry();

    registry.claim(a, List.of(new Claim(p, ComponentClaimPolicy.SHARED_SERIALIZED)));
    registry.claim(b, List.of(new Claim(p, ComponentClaimPolicy.SHARED_SERIALIZED), new Claim(q, ComponentClaimPolicy.SHARED_SERIALIZED)));
    registry.claim(c, List.of(new Claim(q, ComponentClaimPolicy.SHARED_SERIALIZED)));

    assertThat(registry.domainFor(a)).isEqualTo(registry.domainFor(b));
    assertThat(registry.domainFor(b)).isEqualTo(registry.domainFor(c));
    long beforeRelease = registry.generationFor(a);

    registry.release(b);

    assertThat(registry.domainFor(a)).isNotEqualTo(registry.domainFor(c));
    assertThat(registry.generationFor(a)).isGreaterThan(beforeRelease);
}
```

- [ ] **Step 2: Run the registry test to verify it fails**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.multiblock.StructureClaimRegistryTest --no-daemon`

Expected: compilation failure because `ComponentClaimPolicy` and `StructureClaimRegistry` do not exist.

- [ ] **Step 3: Implement policy and a deterministic in-memory registry**

```java
/**
 * Ownership behavior for a stateful multiblock component.
 *
 * @author howxu <dev@howxu.cn>
 */
public enum ComponentClaimPolicy {
    EXCLUSIVE,
    SHARED_SERIALIZED
}
```

```java
/**
 * Server-thread ownership graph for formed multiblock components.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class StructureClaimRegistry {
    private final Map<BlockPos, Set<BlockPos>> ownersByComponent = new HashMap<>();
    private final Map<BlockPos, Set<Claim>> claimsByController = new HashMap<>();
    private final Map<BlockPos, ResourceDomain> domainsByController = new HashMap<>();
    private long nextDomainId;
    private long nextGeneration;

    public ClaimResult claim(BlockPos controllerPos, List<Claim> requested) {
        List<Claim> normalized = requested.stream().distinct().toList();
        for (Claim claim : normalized) {
            Set<BlockPos> owners = ownersByComponent.getOrDefault(claim.componentPos(), Set.of());
            if (claim.policy() == ComponentClaimPolicy.EXCLUSIVE
                    && owners.stream().anyMatch(owner -> !owner.equals(controllerPos))) {
                return ClaimResult.conflict(claim.componentPos(), owners.iterator().next());
            }
        }
        release(controllerPos);
        claimsByController.put(controllerPos.immutable(), new HashSet<>(normalized));
        for (Claim claim : normalized) {
            ownersByComponent.computeIfAbsent(claim.componentPos().immutable(), ignored -> new HashSet<>())
                    .add(controllerPos.immutable());
        }
        rebuildDomains();
        return ClaimResult.success();
    }

    public void release(BlockPos controllerPos) {
        Set<Claim> previous = claimsByController.remove(controllerPos);
        if (previous == null) return;
        for (Claim claim : previous) {
            Set<BlockPos> owners = ownersByComponent.get(claim.componentPos());
            if (owners == null) continue;
            owners.remove(controllerPos);
            if (owners.isEmpty()) ownersByComponent.remove(claim.componentPos());
        }
        rebuildDomains();
    }

    public record Claim(BlockPos componentPos, ComponentClaimPolicy policy) {
        public Claim { componentPos = componentPos.immutable(); }
    }
    public record Conflict(BlockPos componentPos, BlockPos ownerPos) { }
    public record ClaimResult(boolean accepted, @Nullable Conflict conflict) {
        public static ClaimResult success() { return new ClaimResult(true, null); }
        public static ClaimResult conflict(BlockPos componentPos, BlockPos ownerPos) {
            return new ClaimResult(false, new Conflict(componentPos, ownerPos));
        }
    }
    public record ResourceDomain(long id, long generation, Set<BlockPos> controllers) { }
}
```

Implement `rebuildDomains()` as a breadth-first walk over controllers connected by a component whose claim policy is `SHARED_SERIALIZED`. Create a fresh monotonically increasing generation for every rebuilt connected component; never reuse a domain ID or generation. Return immutable copies from `ownersOf`, `domainFor`, and `ResourceDomain.controllers`.

- [ ] **Step 4: Run registry tests to verify they pass**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.multiblock.StructureClaimRegistryTest --no-daemon`

Expected: PASS.

- [ ] **Step 5: Commit the atomic registry**

```bash
git add src/main/java/cn/howxu/mmcr/internal/multiblock/ComponentClaimPolicy.java src/main/java/cn/howxu/mmcr/internal/multiblock/StructureClaimRegistry.java src/test/java/cn/howxu/mmcr/internal/multiblock/StructureClaimRegistryTest.java
```

### Task 2: Make Shared IO Port Ownership And Appearance Multi-Controller Safe

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/MachineComponentTile.java`
- Modify: `src/main/java/cn/howxu/mmcr/internal/tile/IOPortBlockEntity.java:29-187`
- Modify: `src/test/java/cn/howxu/mmcr/internal/tile/MachinePortAppearanceTest.java`

**Interfaces:**
- Consumes: `ComponentClaimPolicy` from Task 1.
- Produces: `MachineComponentTile.claimPolicy()`, `IOPortBlockEntity.linkControllerAppearance`, `unlinkControllerAppearance`, `linkedControllerPositions`, and deterministic `linkedControllerPos` compatibility accessor.

- [ ] **Step 1: Write failing appearance tests for owner-specific unlinking**

```java
@Test
void removingOneSharedOwnerKeepsTheOtherOwnersAppearance() {
    ItemInputBusBlockEntity port = itemInputBus(new BlockPos(1, 64, 1));
    BlockPos first = new BlockPos(0, 64, 0);
    BlockPos second = new BlockPos(4, 64, 0);
    Identifier firstTexture = MMCR.id("block/first");
    Identifier secondTexture = MMCR.id("block/second");

    port.linkControllerAppearance(first, firstTexture);
    port.linkControllerAppearance(second, secondTexture);
    port.unlinkControllerAppearance(first);

    assertThat(port.linkedControllerPositions()).containsExactly(second);
    assertThat(port.linkedControllerPos()).isEqualTo(second);
    assertThat(port.appearanceBaseTexture()).isEqualTo(secondTexture);
}

@Test
void ioPortsAreSharedSerializedButSchedulersAreExclusiveByDefault() {
    assertThat(itemInputBus(new BlockPos(1, 64, 1)).claimPolicy()).isEqualTo(ComponentClaimPolicy.SHARED_SERIALIZED);
    assertThat(new MachineComponentTile() { }.claimPolicy()).isEqualTo(ComponentClaimPolicy.EXCLUSIVE);
}
```

- [ ] **Step 2: Run the appearance test to verify it fails**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.tile.MachinePortAppearanceTest --no-daemon`

Expected: compilation failure because the multi-owner API and policy method do not exist.

- [ ] **Step 3: Add policy defaults and multi-owner persistence**

```java
public interface MachineComponentTile {
    MachineComponent provideComponent();

    default ComponentClaimPolicy claimPolicy() {
        return ComponentClaimPolicy.EXCLUSIVE;
    }
}
```

Replace `IOPortBlockEntity`'s singular `linkedControllerPos` field with a `TreeMap<BlockPos, Identifier>` ordered by `BlockPos::compareTo`. Implement the public surface as follows:

```java
@Override
public ComponentClaimPolicy claimPolicy() {
    return ComponentClaimPolicy.SHARED_SERIALIZED;
}

public void linkControllerAppearance(BlockPos controllerPos, Identifier texture) {
    linkedControllers.put(controllerPos.immutable(), texture == null ? DEFAULT_APPEARANCE_BASE_TEXTURE : texture);
    refreshLinkedAppearance();
}

public void unlinkControllerAppearance(BlockPos controllerPos) {
    if (controllerPos == null || linkedControllers.remove(controllerPos) == null) return;
    refreshLinkedAppearance();
}

public Set<BlockPos> linkedControllerPositions() {
    return Set.copyOf(linkedControllers.keySet());
}

public @Nullable BlockPos linkedControllerPos() {
    return linkedControllers.isEmpty() ? null : linkedControllers.firstKey();
}

private void refreshLinkedAppearance() {
    setAppearanceBaseTexture(linkedControllers.isEmpty()
            ? DEFAULT_APPEARANCE_BASE_TEXTURE
            : linkedControllers.get(linkedControllers.firstKey()));
    setChanged();
}
```

Persist every owner position and texture in a list child named `LinkedControllers`. During `maintainControllerLink`, remove only entries whose controller no longer exists, is unformed, or no longer reports this port in `hasLinkedPort`; call `refreshLinkedAppearance()` only if that removal changed the map. Retain `bindControllerAppearance` as a delegating deprecated compatibility method only until every internal caller moves to `linkControllerAppearance`; remove it in Task 3.

- [ ] **Step 4: Run the appearance tests to verify they pass**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.tile.MachinePortAppearanceTest --no-daemon`

Expected: PASS.

- [ ] **Step 5: Commit multi-owner appearance support**

```bash
git add src/main/java/cn/howxu/mmcr/api/recipe/MachineComponentTile.java src/main/java/cn/howxu/mmcr/internal/tile/IOPortBlockEntity.java src/test/java/cn/howxu/mmcr/internal/tile/MachinePortAppearanceTest.java
```

### Task 3: Atomically Claim Components During Formation And Release Them During Reset

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java:406-461, 493-546, 619-731, 846-892`
- Modify: `src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntityTest.java`

**Interfaces:**
- Consumes: `StructureClaimRegistry.claim(BlockPos, List<Claim>)`, `release(BlockPos)`, and `MachineComponentTile.claimPolicy()`.
- Produces: `MachineControllerBlockEntity.resourceDomain()`, `releaseStructureClaims()`, a component-conflict formation failure, and owner-specific port linking/unlinking.

- [ ] **Step 1: Write failing formation and reset tests**

```java
@Test
void exclusiveComponentClaimPreventsSecondControllerFromForming() {
    MachineControllerBlockEntity first = formedControllerAt(new BlockPos(0, 64, 0), exclusiveSchedulerAt(new BlockPos(1, 64, 0)));
    MachineControllerBlockEntity second = controllerAt(new BlockPos(4, 64, 0), samePatternUsing(new BlockPos(1, 64, 0)));

    second.serverTick();

    assertThat(first.isFormed()).isTrue();
    assertThat(second.isFormed()).isFalse();
    assertThat(second.getLastFormationFailure()).contains("component_claim_conflict");
}

@Test
void sharedPortRemainsLinkedWhenOneOfItsControllersResets() {
    ItemInputBusBlockEntity shared = itemInputBus(new BlockPos(1, 64, 0));
    MachineControllerBlockEntity first = formedControllerUsing(shared, new BlockPos(0, 64, 0));
    MachineControllerBlockEntity second = formedControllerUsing(shared, new BlockPos(4, 64, 0));

    first.resetForTesting();

    assertThat(shared.linkedControllerPositions()).containsExactly(second.getBlockPos());
    assertThat(second.hasLinkedPort(shared.getBlockPos())).isTrue();
}
```

- [ ] **Step 2: Run the controller test to verify it fails**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.tile.MachineControllerBlockEntityTest --no-daemon`

Expected: FAIL because formation does not claim components and reset clears a shared port indiscriminately.

- [ ] **Step 3: Integrate two-phase claims into the controller lifecycle**

Add a `StructureClaimRegistry` lookup scoped to the server level, with this minimum public API:

```java
public static StructureClaimRegistry get(ServerLevel level);
public @Nullable ResourceDomain resourceDomain(BlockPos controllerPos);
```

In `tryFormMachine(candidate, facing, rotatedPattern)`, preserve all current pattern and port validation. Immediately after `validatePortTiers(...)` succeeds, collect only stateful participants:

```java
private List<StructureClaimRegistry.Claim> componentClaims(BlockArray pattern, CompiledMachinePattern compiled, Direction facing) {
    List<StructureClaimRegistry.Claim> claims = new ArrayList<>();
    for (BlockPos relativePos : componentPositions(pattern, compiled, facing)) {
        BlockEntity entity = level.getBlockEntity(getBlockPos().offset(relativePos));
        if (entity instanceof MachineComponentTile tile) {
            claims.add(new StructureClaimRegistry.Claim(entity.getBlockPos(), tile.claimPolicy()));
        } else if (entity instanceof ParallelControllerBlockEntity || entity instanceof FactorySchedulerBlockEntity) {
            claims.add(new StructureClaimRegistry.Claim(entity.getBlockPos(), ComponentClaimPolicy.EXCLUSIVE));
        }
    }
    return claims;
}
```

Call `registry.claim(getBlockPos(), claims)` before `onStructureFormed`. On failure, store a specific failure message containing `component_claim_conflict`, the component coordinate and owner coordinate, then return `false` without mutating `foundMachine`, port rendering, or `FORMED_CONTROLLERS`.

Inside `onStructureFormed`, cache the resolved `ResourceDomain` after the claim succeeds. Replace `bindControllerAppearance` with `linkControllerAppearance`. Replace both broad reset loops with `unlinkControllerAppearance(getBlockPos())`, and call `registry.release(getBlockPos())` before clearing formation fields in `resetMachine`. Override `setRemoved()` to call `resetMachine()` server-side before `super.setRemoved()`.

Do not call `registry.release` for a chunk-unloaded pause path; its existing behavior only pauses active work.

- [ ] **Step 4: Run controller and port tests to verify they pass**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.tile.MachineControllerBlockEntityTest --tests cn.howxu.mmcr.internal.tile.MachinePortAppearanceTest --no-daemon`

Expected: PASS.

- [ ] **Step 5: Commit formation claim integration**

```bash
git add src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntityTest.java
```

### Task 4: Add Fair Resource-Domain Request Resolution

**Files:**
- Create: `src/main/java/cn/howxu/mmcr/internal/multiblock/SharedIoCoordinator.java`
- Create: `src/main/java/cn/howxu/mmcr/internal/event/SharedIoEvents.java`
- Modify: `src/main/java/cn/howxu/mmcr/MMCR.java`
- Create: `src/test/java/cn/howxu/mmcr/internal/multiblock/SharedIoCoordinatorTest.java`

**Interfaces:**
- Consumes: `StructureClaimRegistry.ResourceDomain` and `MachineControllerBlockEntity` version getters.
- Produces: `SharedIoCoordinator.enqueue(StartRequest|TickRequest|FinishRequest)`, `resolve(Level)`, and a stable `LaneKey(BlockPos controllerPos, String laneId)`.

- [ ] **Step 1: Write failing pure coordinator tests for partial grants, round-robin, and stale generations**

```java
@Test
void startRequestsUseRotatingOrderAndMayReceivePartialParallelism() {
    SharedIoCoordinator coordinator = new SharedIoCoordinator();
    ResourceDomain domain = new ResourceDomain(4L, 9L, Set.of(A, B));
    List<String> committed = new ArrayList<>();

    coordinator.enqueue(new StartRequest(domain, new LaneKey(A, "base"), 17L, 8,
            maximum -> Math.min(maximum, 8), committed::add));
    coordinator.enqueue(new StartRequest(domain, new LaneKey(B, "base"), 21L, 8,
            maximum -> Math.min(maximum, 2), committed::add));

    coordinator.resolve(domain);

    assertThat(committed).containsExactly("A:8", "B:2");
    assertThat(coordinator.nextStartLane(domain.id())).isEqualTo(new LaneKey(B, "base"));
}

@Test
void staleGenerationNeverCallsTheCommitter() {
    SharedIoCoordinator coordinator = new SharedIoCoordinator();
    AtomicBoolean committed = new AtomicBoolean();
    coordinator.enqueue(new StartRequest(new ResourceDomain(2L, 3L, Set.of(A)), new LaneKey(A, "base"), 7L, 4,
            ignored -> 4, ignored -> committed.set(true)));

    coordinator.resolve(new ResourceDomain(2L, 4L, Set.of(A)));

    assertThat(committed).isFalse();
}
```

- [ ] **Step 2: Run the coordinator test to verify it fails**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.multiblock.SharedIoCoordinatorTest --no-daemon`

Expected: compilation failure because the coordinator and request records do not exist.

- [ ] **Step 3: Implement an end-of-level coordinator with independent start and running cursors**

Implement `SharedIoCoordinator` as a per-`ServerLevel` singleton. Requests must include domain ID/generation, controller structure version, immutable `LaneKey`, a validator, and a server-thread transaction callback. The core resolver must:

```java
private void resolveDomain(ResourceDomain domain, List<Request> requests) {
    List<Request> current = requests.stream()
            .filter(request -> request.domainId() == domain.id())
            .filter(request -> request.domainGeneration() == domain.generation())
            .filter(Request::isStillValid)
            .sorted(Comparator.comparing(Request::laneKey))
            .toList();
    resolveRoundRobin(current, startCursors, domain.id());
    resolveRoundRobin(current, tickCursors, domain.id());
    resolveRoundRobin(current, finishCursors, domain.id());
}
```

`resolveRoundRobin` must begin after the stored lane cursor, attempt each request once, advance the cursor after every successful grant, and leave an insufficient-resource request pending only for the next level tick. Start callbacks receive the requested maximum parallelism and return the actual committed parallelism, where `0` means no start. Tick callbacks return whether the full per-tick request was supplied. Finish callbacks return whether output was committed.

Register `SharedIoCoordinator.get(level).resolve(level)` on `LevelTickEvent.Post` for server levels. Register a level-unload callback that discards the level's coordinator and claim registry. Do not execute request callbacks from an async worker or from a block-entity tick.

- [ ] **Step 4: Run coordinator tests to verify they pass**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.multiblock.SharedIoCoordinatorTest --no-daemon`

Expected: PASS.

- [ ] **Step 5: Commit fair domain scheduling**

```bash
git add src/main/java/cn/howxu/mmcr/internal/multiblock/SharedIoCoordinator.java src/main/java/cn/howxu/mmcr/internal/event/SharedIoEvents.java src/main/java/cn/howxu/mmcr/MMCR.java src/test/java/cn/howxu/mmcr/internal/multiblock/SharedIoCoordinatorTest.java
```

### Task 5: Route Recipe Start Through Transactional Shared-IO Requests

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/internal/recipe/RecipeThread.java:39-100`
- Modify: `src/main/java/cn/howxu/mmcr/internal/recipe/FactoryRecipeThread.java:22-118`
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContext.java:165-235, 490-920`
- Modify: `src/main/java/cn/howxu/mmcr/internal/recipe/FactoryRecipeScheduler.java:211-238`
- Modify: `src/test/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContextTest.java`
- Modify: `src/test/java/cn/howxu/mmcr/internal/recipe/FactoryRecipeSchedulerTest.java`

**Interfaces:**
- Consumes: Task 4 `StartRequest` and controller `resourceDomain()`.
- Produces: `RecipeThread.requestStart`, `RecipeCraftingContext.commitStart(MachineRecipe, int)`, and a stable `FactoryRecipeThread.laneId()`.

- [ ] **Step 1: Write failing tests that two starts sharing one inventory cannot over-commit**

```java
@Test
void sharedInputStartsOnlyTheParallelismThatCanBeCommitted() {
    ItemInputBusBlockEntity input = itemInputBus(new BlockPos(1, 64, 0));
    input.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 10));
    MachineControllerBlockEntity first = controllerWithComponents(input);
    MachineControllerBlockEntity second = controllerWithComponents(input);
    MachineRecipe recipe = explicitItemRecipe("shared_start", List.of(itemInput(Items.IRON_INGOT, 1)));

    int firstParallel = new RecipeCraftingContext(first).commitStart(recipe, 8);
    int secondParallel = new RecipeCraftingContext(second).commitStart(recipe, 8);

    assertThat(firstParallel + secondParallel).isEqualTo(10);
    assertThat(input.getItemStackHandler(null).getStackInSlot(0)).isEmpty();
}
```

Add a scheduler test that gives two factory threads a shared request budget of ten with a theoretical maximum of eight each and asserts the resolved thread parallelisms total ten.

- [ ] **Step 2: Run the start tests to verify they fail**

Run: `./gradlew test --tests cn.howxu.mmcr.api.recipe.RecipeCraftingContextTest --tests cn.howxu.mmcr.internal.recipe.FactoryRecipeSchedulerTest --no-daemon`

Expected: compilation failure because `commitStart` and queued start behavior do not exist.

- [ ] **Step 3: Make start simulation and mutation one coordinator transaction**

In `RecipeCraftingContext`, add one server-thread method that redoes all checks against live handlers and commits only the granted amount:

```java
public int commitStart(MachineRecipe recipe, int requestedParallelism) {
    int parallelism = Math.max(0, Math.min(requestedParallelism, maxInputParallelism(recipe, requestedParallelism)));
    while (parallelism > 0) {
        if (simulateInputs(recipe, parallelism)
                && simulateOutputs(recipe, parallelism)
                && canStartCrafting(new ActiveMachineRecipe(recipe, parallelism), parallelism)
                && startCrafting(recipe, parallelism)) {
            return parallelism;
        }
        parallelism--;
    }
    return 0;
}
```

Refactor existing `startCrafting` internals so it does not re-simulate against a different route. It must commit output feasibility before extracting item/fluid inputs, retain the current no-swallow guarantee, and use the actual granted parallelism when applying energy/start requirements.

In `RecipeThread.searchAndStartRecipe`, keep `RecipeSearchTask.compute()` only as a candidate builder. Instead of calling `next.start(nextContext)` immediately, enqueue a `StartRequest` carrying controller position, `structureVersion`, current domain generation, lane ID, candidate active recipe, and context. Its coordinator callback must call `commitStart`; on a positive grant, set `next.setParallelism(grant)`, refresh duration, then atomically install `activeRecipe`, `context`, `WORKING`, and `onStarted`. On zero grant, return the borrowed context and leave the thread idle rather than failed.

Give the base lane ID `base`, generated factory lanes monotonically assigned IDs such as `factory-0`, and core lanes `core-<threadName>`. `FactoryRecipeScheduler` must treat a thread with a pending start as unavailable for another start attempt in the same tick.

- [ ] **Step 4: Run start and scheduler tests to verify they pass**

Run: `./gradlew test --tests cn.howxu.mmcr.api.recipe.RecipeCraftingContextTest --tests cn.howxu.mmcr.internal.recipe.FactoryRecipeSchedulerTest --no-daemon`

Expected: PASS.

- [ ] **Step 5: Commit transactional starts**

```bash
git add src/main/java/cn/howxu/mmcr/internal/recipe/RecipeThread.java src/main/java/cn/howxu/mmcr/internal/recipe/FactoryRecipeThread.java src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContext.java src/main/java/cn/howxu/mmcr/internal/recipe/FactoryRecipeScheduler.java src/test/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContextTest.java src/test/java/cn/howxu/mmcr/internal/recipe/FactoryRecipeSchedulerTest.java
```

### Task 6: Route Continuous Consumption And Outputs Through The Same Domain

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/ActiveMachineRecipe.java:228-280`
- Modify: `src/main/java/cn/howxu/mmcr/internal/recipe/RecipeThread.java:79-94`
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContext.java:153-163, 490-920`
- Modify: `src/test/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContextTest.java`
- Modify: `src/test/java/cn/howxu/mmcr/internal/multiblock/SharedIoCoordinatorTest.java`

**Interfaces:**
- Consumes: Task 4 `TickRequest` and `FinishRequest`; Task 5 installed active recipes and stable lane IDs.
- Produces: `RecipeThread.requestTick`, `RecipeThread.requestFinish`, `RecipeCraftingContext.commitIoTick`, and `RecipeCraftingContext.commitOutputs` used only from coordinator callbacks.

- [ ] **Step 1: Write failing tests for fair energy ticks and blocked shared output**

```java
@Test
void finiteSharedEnergyAdvancesOnlyTheLaneGrantedFullEnergyAndRotatesNextTick() {
    TestEnergyHatch energy = new TestEnergyHatch(20);
    List<String> advanced = new ArrayList<>();
    SharedIoCoordinator coordinator = coordinatorFor(energy);

    coordinator.enqueue(tickRequest(A, "base", 15, () -> energy.extractEnergy(15, false) == 15, () -> advanced.add("A")));
    coordinator.enqueue(tickRequest(B, "base", 15, () -> energy.extractEnergy(15, false) == 15, () -> advanced.add("B")));
    coordinator.resolve(currentDomain());
    energy.receiveEnergy(15, false);
    coordinator.enqueue(tickRequest(A, "base", 15, () -> energy.extractEnergy(15, false) == 15, () -> advanced.add("A")));
    coordinator.enqueue(tickRequest(B, "base", 15, () -> energy.extractEnergy(15, false) == 15, () -> advanced.add("B")));
    coordinator.resolve(currentDomain());

    assertThat(advanced).containsExactly("A", "B");
}

@Test
void blockedOutputKeepsCompletedRecipeAndDoesNotDuplicateItsProduct() {
    ActiveMachineRecipe active = activeRecipeAtFinalTickWithFullSharedOutput();

    assertThat(active.tick(context)).isEqualTo(ActiveMachineRecipe.TickStatus.WAITING);
    assertThat(active.getTick()).isEqualTo(active.getTotalTick() - 1);
    assertThat(outputAmount()).isZero();
}
```

- [ ] **Step 2: Run the tick and output tests to verify they fail**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.multiblock.SharedIoCoordinatorTest --tests cn.howxu.mmcr.api.recipe.RecipeCraftingContextTest --no-daemon`

Expected: FAIL because active recipes directly call `ioTick` and `finishCrafting` from controller tick order.

- [ ] **Step 3: Split active recipe progression from resource commits**

Change `ActiveMachineRecipe` to expose planning state but not mutate live handlers itself:

```java
public boolean needsFinishCommit() {
    return tick + 1 >= totalTick;
}

public TickStatus applyTickGrant(boolean resourcesGranted, boolean outputsCommitted, int gameTime) {
    if (!resourcesGranted) {
        doFailureAction(RecipeFailureActions.STILL);
        return TickStatus.WAITING;
    }
    int nextTick = Math.min(tick + 1, totalTick);
    if (nextTick < totalTick) {
        tick = nextTick;
        return TickStatus.CONTINUE;
    }
    if (!outputsCommitted) {
        tick = Math.max(0, totalTick - 1);
        markFinishBlocked(gameTime);
        return TickStatus.WAITING;
    }
    tick = nextTick;
    return TickStatus.FINISHED;
}
```

`RecipeThread.tick()` must enqueue a `TickRequest` rather than call `activeRecipe.tick(context)` directly. Its callback calls `context.commitIoTick(recipe, parallelism)`, which must validate all per-tick requirements before applying any destructive operation. If the recipe would finish, enqueue/resolve its `FinishRequest` in the same domain pass after a successful tick grant; that callback calls `context.commitOutputs(recipe, parallelism)`. Apply the `TickStatus` result only from the coordinator callback.

Use the running cursor, not the start cursor, for tick requests. A lane that cannot receive its full request must not advance, decrement progress, or consume a partial tick. A completed lane with blocked output retains its context and retries only after `shouldRetryFinish(gameTime)` permits it.

- [ ] **Step 4: Run tick/output tests to verify they pass**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.multiblock.SharedIoCoordinatorTest --tests cn.howxu.mmcr.api.recipe.RecipeCraftingContextTest --no-daemon`

Expected: PASS.

- [ ] **Step 5: Commit fair shared runtime IO**

```bash
git add src/main/java/cn/howxu/mmcr/api/recipe/ActiveMachineRecipe.java src/main/java/cn/howxu/mmcr/internal/recipe/RecipeThread.java src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContext.java src/test/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContextTest.java src/test/java/cn/howxu/mmcr/internal/multiblock/SharedIoCoordinatorTest.java
```

### Task 7: Add In-World Regression Coverage And Run The Full Verification Set

**Files:**
- Create: `src/gametest/java/cn/howxu/mmcr/SharedMultiblockIoGameTest.java`
- Modify: `src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntityTest.java`

**Interfaces:**
- Consumes: completed claim, controller, port, and coordinator APIs from Tasks 1-6.
- Produces: executable regressions proving the end-to-end behavior in a server world.

- [ ] **Step 1: Write GameTests for shared formation, partial parallel start, fairness, and teardown**

```java
@GameTest(template = "empty")
public void sharedEnergyPortFormsBothControllersAndSurvivesOneTeardown(GameTestHelper helper) {
    BlockPos sharedEnergy = new BlockPos(2, 2, 2);
    MachineControllerBlockEntity first = placeBlastFurnace(helper, new BlockPos(0, 2, 0), sharedEnergy);
    MachineControllerBlockEntity second = placeBlastFurnace(helper, new BlockPos(4, 2, 0), sharedEnergy);

    helper.runAtTickTime(4, () -> {
        helper.assertTrue(first.isFormed(), "first controller should form");
        helper.assertTrue(second.isFormed(), "second controller should form through shared energy port");
        helper.destroyBlock(first.getBlockPos());
    });
    helper.runAtTickTime(8, () -> {
        IOPortBlockEntity port = helper.getBlockEntity(sharedEnergy);
        helper.assertTrue(second.isFormed(), "second controller remains formed after first teardown");
        helper.assertTrue(port.linkedControllerPositions().contains(second.getBlockPos()), "shared port retains second owner");
        helper.succeed();
    });
}
```

Add separate GameTests which seed one shared item input with ten items and two controllers requesting parallel eight, then assert total active parallelism is ten; and which supply one per-tick energy grant at a time for two lanes, asserting both lane tick counters increase over successive ticks.

- [ ] **Step 2: Run the GameTest compilation and targeted unit tests**

Run: `./gradlew compileTestJava compileGameTestJava test --tests cn.howxu.mmcr.internal.multiblock.StructureClaimRegistryTest --tests cn.howxu.mmcr.internal.multiblock.SharedIoCoordinatorTest --tests cn.howxu.mmcr.internal.tile.MachineControllerBlockEntityTest --tests cn.howxu.mmcr.internal.tile.MachinePortAppearanceTest --tests cn.howxu.mmcr.api.recipe.RecipeCraftingContextTest --no-daemon`

Expected: PASS.

- [ ] **Step 3: Run the complete project verification**

Run: `./gradlew build --no-daemon`

Expected: PASS. Do not run `runClient`.

- [ ] **Step 4: Inspect final changes before committing**

Run: `git status --short && git diff --check && git diff -- src/main/java/cn/howxu/mmcr/internal/multiblock src/main/java/cn/howxu/mmcr/internal/tile src/main/java/cn/howxu/mmcr/internal/recipe src/main/java/cn/howxu/mmcr/api/recipe src/test/java src/gametest/java`

Expected: no whitespace errors; only shared multiblock IO implementation and its tests are staged for the final commit.

- [ ] **Step 5: Commit end-to-end regression coverage**

```bash
git add src/gametest/java/cn/howxu/mmcr/SharedMultiblockIoGameTest.java src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntityTest.java
```

## Self-Review

- Spec coverage: Tasks 1-3 implement stateful component policy, atomic claims, owner-specific release, domain connectivity, and generation invalidation. Tasks 4-6 implement server-thread-only batching, partial starts, separate fair cursors, version validation, per-tick full-resource grants, and output blocking. Task 7 covers the agreed in-world acceptance cases and non-regression verification.
- Placeholder scan: every code-changing task identifies file paths, named methods/types, tests, commands, and expected outcomes; no steps are deferred or unnamed.
- Type consistency: `ComponentClaimPolicy`, `StructureClaimRegistry.Claim`, `ResourceDomain`, `LaneKey`, `StartRequest`, `TickRequest`, and `FinishRequest` are introduced before their consuming tasks. `commitStart`, `commitIoTick`, and `commitOutputs` are the transaction surface used by recipe scheduling.
