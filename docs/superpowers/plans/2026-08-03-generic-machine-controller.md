# Generic Machine Controller Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make multiblock machine creation machine-first, with each `Machine` automatically owning a generated controller block/item/block-entity registration and generated assets.

**Architecture:** Add controller metadata to the machine API, then make registry/datagen/build paths consume that metadata instead of hardcoding `mmcr:controller`. Because blocks cannot be registered at runtime, this plan supports built-in/startup machine definitions and keeps KubeJS machine controller generation constrained to registry-time definitions.

**Tech Stack:** Java 25, Minecraft/NeoForge 1.21-era APIs, DeferredRegister, KubeJS compile-only integration, JUnit 5, AssertJ, NeoForge datagen.

## Global Constraints

- Every machine controller block/item/id is per-machine, e.g. `mmcr:blast_furnace` owns `mmcr:blast_furnace_controller`.
- Creating a machine must not require manually creating a concrete controller block.
- IO ports remain generic item/fluid/energy ports; do not add new tiers or third-party port types in this plan.
- Controller textures live on `Machine` metadata and must support one-call five-side assignment plus per-face setters.
- Blockstate, block model, and item model generation must not special-case a global `controller` id.
- Do not add dependencies or change `build.gradle`.
- Do not auto-migrate old `mmcr:controller` saved blocks.
- Do not commit unless the user explicitly asks.

---

## File Structure

- Create: `src/main/java/cn/howxu/mmcr/api/machine/MachineControllerSpec.java` — immutable controller id and texture metadata.
- Modify: `src/main/java/cn/howxu/mmcr/api/machine/Machine.java` — expose `MachineControllerSpec controller()`.
- Modify: `src/main/java/cn/howxu/mmcr/api/machine/DynamicMachine.java` — store controller spec with compatibility constructor.
- Modify: `src/main/java/cn/howxu/mmcr/compat/kubejs/MachineBuilderJS.java` — add controller texture APIs and build machines with controller spec.
- Modify: `src/main/java/cn/howxu/mmcr/internal/block/MachineControllerBlock.java` — store machine id and create controller BE through per-machine type lookup.
- Modify: `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java` — bind machine from owning controller block instead of hardcoded blast furnace.
- Modify: `src/main/java/cn/howxu/mmcr/registry/ModBlocks.java` — register per-machine controller blocks and expose lookup helpers.
- Modify: `src/main/java/cn/howxu/mmcr/registry/ModItems.java` — auto-register item for per-machine controller blocks.
- Modify: `src/main/java/cn/howxu/mmcr/registry/ModBlockEntities.java` — register per-machine controller block entity types.
- Modify: `src/main/java/org/nibelungorum/DefaultMachines.java` — remove explicit controller argument and use machine-owned controller.
- Modify: `src/main/java/cn/howxu/mmcr/internal/command/BuildCommand.java` — place `ModBlocks.controllerFor(machine)`.
- Modify: `src/main/java/cn/howxu/mmcr/datagen/ModelGen.java` — generate controller assets from `MachineControllerSpec`.
- Modify: `src/main/java/cn/howxu/mmcr/datagen/Translations.java` — add machine controller lang entries.
- Modify: `src/test/java/cn/howxu/mmcr/test/TestBootstrap.java` — bind the generated blast furnace controller holder.
- Modify: `src/test/java/cn/howxu/mmcr/internal/machine/DefaultMachinesTest.java` — assert controller spec and pattern controller block.
- Modify: `src/test/java/cn/howxu/mmcr/api/machine/BuildPlacementConsistencyTest.java` — assert placement uses generated controller.
- Modify or add focused tests under `src/test/java/cn/howxu/mmcr/api/machine/` and `src/test/java/cn/howxu/mmcr/compat/kubejs/` for controller spec defaults and builder texture APIs.

---

### Task 1: Machine Controller Metadata API

**Files:**
- Create: `src/main/java/cn/howxu/mmcr/api/machine/MachineControllerSpec.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/machine/Machine.java`
- Modify: `src/main/java/cn/howxu/mmcr/api/machine/DynamicMachine.java`
- Modify: `src/main/java/cn/howxu/mmcr/compat/kubejs/MachineBuilderJS.java`
- Test: `src/test/java/cn/howxu/mmcr/api/machine/MachineControllerSpecTest.java`
- Test: `src/test/java/cn/howxu/mmcr/compat/kubejs/MachineBuilderJSTest.java`

**Interfaces:**
- Consumes: existing `BlockArray`, `DynamicMachine(Identifier, String, BlockArray)`, and `MachineBuilderJS.createObject()`.
- Produces: `MachineControllerSpec`, `Machine.controller()`, `DynamicMachine(Identifier, String, BlockArray, MachineControllerSpec)`, and KubeJS texture builder methods.

- [ ] **Step 1: Write failing tests for default controller spec**

Create `src/test/java/cn/howxu/mmcr/api/machine/MachineControllerSpecTest.java`:

```java
package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.MMCR;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MachineControllerSpecTest {

    @Test
    void defaults_derive_controller_id_and_safe_textures_from_machine_id() {
        MachineControllerSpec spec = MachineControllerSpec.defaultsFor(MMCR.id("blast_furnace"));

        assertThat(spec.id()).isEqualTo(MMCR.id("blast_furnace_controller"));
        assertThat(spec.frontTexture()).isEqualTo(MMCR.id("block/blast_furnace_controller"));
        assertThat(spec.sideTexture()).isEqualTo(MMCR.id("block/casing"));
        assertThat(spec.topTexture()).isEqualTo(MMCR.id("block/casing"));
        assertThat(spec.bottomTexture()).isEqualTo(MMCR.id("block/casing"));
    }

    @Test
    void dynamic_machine_compat_constructor_uses_default_controller_spec() {
        DynamicMachine machine = new DynamicMachine(
                MMCR.id("blast_furnace"), "高炉", new BlockArray(Map.of()));

        assertThat(machine.controller()).isEqualTo(MachineControllerSpec.defaultsFor(MMCR.id("blast_furnace")));
    }

    @Test
    void spec_rejects_null_values() {
        Identifier id = MMCR.id("blast_furnace_controller");
        Identifier texture = MMCR.id("block/casing");

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new MachineControllerSpec(null, texture, texture, texture, texture));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new MachineControllerSpec(id, null, texture, texture, texture));
    }
}
```

- [ ] **Step 2: Write failing tests for builder texture APIs**

Create `src/test/java/cn/howxu/mmcr/compat/kubejs/MachineBuilderJSTest.java`:

```java
package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MachineBuilderJSTest {

    @Test
    void controller_textures_sets_front_and_all_other_faces() {
        var machine = new MachineBuilderJS(MMCR.id("arc_furnace"))
                .controllerTextures(MMCR.id("block/arc_front"), MMCR.id("block/arc_side"))
                .createObject();

        assertThat(machine.controller()).isEqualTo(new MachineControllerSpec(
                MMCR.id("arc_furnace_controller"),
                MMCR.id("block/arc_front"),
                MMCR.id("block/arc_side"),
                MMCR.id("block/arc_side"),
                MMCR.id("block/arc_side")));
    }

    @Test
    void individual_texture_setters_override_only_that_face() {
        var machine = new MachineBuilderJS(MMCR.id("arc_furnace"))
                .controllerTextures("mmcr:block/arc_front", "mmcr:block/arc_side")
                .controllerTopTexture(MMCR.id("block/arc_top"))
                .controllerBottomTexture(MMCR.id("block/arc_bottom"))
                .createObject();

        assertThat(machine.controller().frontTexture()).isEqualTo(MMCR.id("block/arc_front"));
        assertThat(machine.controller().sideTexture()).isEqualTo(MMCR.id("block/arc_side"));
        assertThat(machine.controller().topTexture()).isEqualTo(MMCR.id("block/arc_top"));
        assertThat(machine.controller().bottomTexture()).isEqualTo(MMCR.id("block/arc_bottom"));
    }
}
```

- [ ] **Step 3: Run tests and verify they fail for missing APIs**

Run: `./gradlew test --tests cn.howxu.mmcr.api.machine.MachineControllerSpecTest --tests cn.howxu.mmcr.compat.kubejs.MachineBuilderJSTest`

Expected: compile failure for missing `MachineControllerSpec`, `Machine.controller()`, and builder texture methods.

- [ ] **Step 4: Add `MachineControllerSpec`**

Create `src/main/java/cn/howxu/mmcr/api/machine/MachineControllerSpec.java`:

```java
package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.MMCR;
import net.minecraft.resources.Identifier;

public record MachineControllerSpec(
        Identifier id,
        Identifier frontTexture,
        Identifier sideTexture,
        Identifier topTexture,
        Identifier bottomTexture) {

    public MachineControllerSpec {
        if (id == null) throw new IllegalArgumentException("id null");
        if (frontTexture == null) throw new IllegalArgumentException("frontTexture null");
        if (sideTexture == null) throw new IllegalArgumentException("sideTexture null");
        if (topTexture == null) throw new IllegalArgumentException("topTexture null");
        if (bottomTexture == null) throw new IllegalArgumentException("bottomTexture null");
    }

    public static MachineControllerSpec defaultsFor(Identifier machineId) {
        if (machineId == null) throw new IllegalArgumentException("machineId null");
        String controllerPath = machineId.getPath() + "_controller";
        Identifier controllerId = Identifier.fromNamespaceAndPath(machineId.getNamespace(), controllerPath);
        Identifier casing = MMCR.id("block/casing");
        return new MachineControllerSpec(
                controllerId,
                Identifier.fromNamespaceAndPath(machineId.getNamespace(), "block/" + controllerPath),
                casing,
                casing,
                casing);
    }
}
```

- [ ] **Step 5: Extend `Machine` and `DynamicMachine`**

Update `src/main/java/cn/howxu/mmcr/api/machine/Machine.java` to include:

```java
MachineControllerSpec controller();
```

Update `src/main/java/cn/howxu/mmcr/api/machine/DynamicMachine.java` to this shape:

```java
public record DynamicMachine(
        Identifier registryName,
        String localizedName,
        BlockArray pattern,
        MachineControllerSpec controller
) implements Machine {
    public DynamicMachine(Identifier registryName, String localizedName, BlockArray pattern) {
        this(registryName, localizedName, pattern, MachineControllerSpec.defaultsFor(registryName));
    }

    public DynamicMachine {
        if (registryName == null) throw new IllegalArgumentException("registryName null");
        if (localizedName == null) throw new IllegalArgumentException("localizedName null");
        if (pattern == null) throw new IllegalArgumentException("pattern null");
        if (controller == null) throw new IllegalArgumentException("controller null");
    }
}
```

- [ ] **Step 6: Add builder texture methods**

Modify `src/main/java/cn/howxu/mmcr/compat/kubejs/MachineBuilderJS.java`:

```java
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
```

Add fields:

```java
public transient Identifier controllerFrontTexture;
public transient Identifier controllerSideTexture;
public transient Identifier controllerTopTexture;
public transient Identifier controllerBottomTexture;
```

Add methods:

```java
public MachineBuilderJS controllerTextures(String front, String otherFive) {
    return controllerTextures(Identifier.parse(front), Identifier.parse(otherFive));
}

public MachineBuilderJS controllerTextures(Identifier front, Identifier otherFive) {
    this.controllerFrontTexture = front;
    this.controllerSideTexture = otherFive;
    this.controllerTopTexture = otherFive;
    this.controllerBottomTexture = otherFive;
    return this;
}

public MachineBuilderJS controllerTextures(Identifier front, Identifier side, Identifier top, Identifier bottom) {
    this.controllerFrontTexture = front;
    this.controllerSideTexture = side;
    this.controllerTopTexture = top;
    this.controllerBottomTexture = bottom;
    return this;
}

public MachineBuilderJS controllerFrontTexture(String texture) {
    return controllerFrontTexture(Identifier.parse(texture));
}

public MachineBuilderJS controllerFrontTexture(Identifier texture) {
    this.controllerFrontTexture = texture;
    return this;
}

public MachineBuilderJS controllerSideTexture(String texture) {
    return controllerSideTexture(Identifier.parse(texture));
}

public MachineBuilderJS controllerSideTexture(Identifier texture) {
    this.controllerSideTexture = texture;
    return this;
}

public MachineBuilderJS controllerTopTexture(String texture) {
    return controllerTopTexture(Identifier.parse(texture));
}

public MachineBuilderJS controllerTopTexture(Identifier texture) {
    this.controllerTopTexture = texture;
    return this;
}

public MachineBuilderJS controllerBottomTexture(String texture) {
    return controllerBottomTexture(Identifier.parse(texture));
}

public MachineBuilderJS controllerBottomTexture(Identifier texture) {
    this.controllerBottomTexture = texture;
    return this;
}

private MachineControllerSpec controllerSpec() {
    MachineControllerSpec defaults = MachineControllerSpec.defaultsFor(id);
    return new MachineControllerSpec(
            defaults.id(),
            controllerFrontTexture != null ? controllerFrontTexture : defaults.frontTexture(),
            controllerSideTexture != null ? controllerSideTexture : defaults.sideTexture(),
            controllerTopTexture != null ? controllerTopTexture : defaults.topTexture(),
            controllerBottomTexture != null ? controllerBottomTexture : defaults.bottomTexture());
}
```

Change `createObject()`:

```java
return new DynamicMachine(id, localizedName, pattern, controllerSpec());
```

- [ ] **Step 7: Run focused tests**

Run: `./gradlew test --tests cn.howxu.mmcr.api.machine.MachineControllerSpecTest --tests cn.howxu.mmcr.compat.kubejs.MachineBuilderJSTest`

Expected: PASS.

---

### Task 2: Per-Machine Controller Registration

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/internal/block/MachineControllerBlock.java`
- Modify: `src/main/java/cn/howxu/mmcr/registry/ModBlocks.java`
- Modify: `src/main/java/cn/howxu/mmcr/registry/ModItems.java`
- Modify: `src/main/java/cn/howxu/mmcr/registry/ModBlockEntities.java`
- Modify: `src/test/java/cn/howxu/mmcr/test/TestBootstrap.java`
- Test: `src/test/java/cn/howxu/mmcr/registry/MachineControllerRegistrationTest.java`

**Interfaces:**
- Consumes: `MachineControllerSpec.defaultsFor(Identifier)` and `Machine.controller()` from Task 1.
- Produces: `ModBlocks.controllerFor(Machine)`, `ModBlocks.controllerFor(Identifier)`, `ModBlocks.machineIdForController(Block)`, `ModBlockEntities.controllerFor(Identifier)`, and `MachineControllerBlock.machineId()`.

- [ ] **Step 1: Write failing registration tests**

Create `src/test/java/cn/howxu/mmcr/registry/MachineControllerRegistrationTest.java`:

```java
package cn.howxu.mmcr.registry;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MachineControllerRegistrationTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void default_blast_furnace_controller_is_registered_as_machine_specific_block_item_and_be() {
        Identifier machineId = MMCR.id("blast_furnace");
        String controllerName = MachineControllerSpec.defaultsFor(machineId).id().getPath();

        assertThat(ModBlocks.BLOCKS).containsKey(controllerName);
        assertThat(ModItems.ITEMS).containsKey(controllerName);
        assertThat(ModBlockEntities.BES).containsKey(controllerName);
        assertThat(ModBlocks.controllerFor(machineId)).isSameAs(ModBlocks.BLOCKS.get(controllerName));
        assertThat(ModBlockEntities.controllerFor(machineId)).isSameAs(ModBlockEntities.BES.get(controllerName));
    }

    @Test
    void controller_block_knows_owning_machine_id() {
        var block = ModBlocks.controllerFor(MMCR.id("blast_furnace")).get();

        assertThat(block).isInstanceOf(MachineControllerBlock.class);
        assertThat(((MachineControllerBlock) block).machineId()).isEqualTo(MMCR.id("blast_furnace"));
    }
}
```

- [ ] **Step 2: Run test and verify it fails**

Run: `./gradlew test --tests cn.howxu.mmcr.registry.MachineControllerRegistrationTest`

Expected: compile failure for missing controller lookup methods and `MachineControllerBlock.machineId()`.

- [ ] **Step 3: Add machine id to controller block**

Modify `src/main/java/cn/howxu/mmcr/internal/block/MachineControllerBlock.java`:

```java
import cn.howxu.mmcr.registry.ModBlockEntities;
import net.minecraft.resources.Identifier;
```

Add field and constructors:

```java
private final Identifier machineId;

public MachineControllerBlock(Properties props) {
    this(cn.howxu.mmcr.MMCR.id("unknown"), props);
}

public MachineControllerBlock(Identifier machineId, Properties props) {
    super(props.sound(SoundType.METAL));
    if (machineId == null) throw new IllegalArgumentException("machineId null");
    this.machineId = machineId;
    registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(FORMED, false)
            .setValue(ACTIVE, false));
}

public Identifier machineId() {
    return machineId;
}
```

Change `newBlockEntity`:

```java
return ModBlockEntities.controllerFor(machineId).get().create(pos, state);
```

Keep the legacy one-argument constructor only so existing tests or holders compile; new business code must use the `Identifier` constructor.

- [ ] **Step 4: Register default machine controller in blocks**

Modify `src/main/java/cn/howxu/mmcr/registry/ModBlocks.java`:

```java
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import net.minecraft.resources.Identifier;
```

Add fields:

```java
private static final Identifier BLAST_FURNACE_ID = MMCR.id("blast_furnace");
```

In static block, replace global controller registration:

```java
registerMachineController(BLAST_FURNACE_ID);
BLOCKS.put("casing", REGISTER.registerBlock("casing", MachineCasingBlock::new));
PortKinds.all().forEach(ModBlocks::registerIoPort);
```

Keep constants:

```java
public static final DeferredHolder<Block, Block> BLAST_FURNACE_CONTROLLER = controllerFor(BLAST_FURNACE_ID);
public static final DeferredHolder<Block, Block> CONTROLLER = BLAST_FURNACE_CONTROLLER;
public static final DeferredHolder<Block, Block> CASING = BLOCKS.get("casing");
```

Add helpers:

```java
private static void registerMachineController(Identifier machineId) {
    String name = MachineControllerSpec.defaultsFor(machineId).id().getPath();
    BLOCKS.put(name, REGISTER.registerBlock(name,
            properties -> new MachineControllerBlock(machineId, properties)));
}

public static DeferredHolder<Block, Block> controllerFor(cn.howxu.mmcr.api.machine.Machine machine) {
    return controllerFor(machine.registryName());
}

public static DeferredHolder<Block, Block> controllerFor(Identifier machineId) {
    String name = MachineControllerSpec.defaultsFor(machineId).id().getPath();
    DeferredHolder<Block, Block> holder = BLOCKS.get(name);
    if (holder == null) throw new IllegalArgumentException("No controller registered for machine: " + machineId);
    return holder;
}

public static Identifier machineIdForController(Block block) {
    if (block instanceof MachineControllerBlock controller) {
        return controller.machineId();
    }
    return null;
}
```

- [ ] **Step 5: Ensure items include generated controller**

No structural change should be necessary because `ModItems` already iterates `ModBlocks.BLOCKS`. Check that the generated key `blast_furnace_controller` appears in `ModItems.ITEMS` after Task 2 Step 4.

- [ ] **Step 6: Register per-machine controller block entity type**

Modify `src/main/java/cn/howxu/mmcr/registry/ModBlockEntities.java`:

```java
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import net.minecraft.resources.Identifier;
```

Add field:

```java
private static final Identifier BLAST_FURNACE_ID = MMCR.id("blast_furnace");
```

In static block, replace controller BE registration:

```java
registerMachineController(BLAST_FURNACE_ID);
PortKinds.all().forEach(kind -> { ... });
```

Add helpers:

```java
private static void registerMachineController(Identifier machineId) {
    String name = MachineControllerSpec.defaultsFor(machineId).id().getPath();
    BES.put(name, register(name, () -> new BlockEntityType<>(
            MachineControllerBlockEntity::new, ModBlocks.controllerFor(machineId).get())));
}

public static DeferredHolder<BlockEntityType<?>, BlockEntityType<?>> controllerFor(Identifier machineId) {
    String name = MachineControllerSpec.defaultsFor(machineId).id().getPath();
    DeferredHolder<BlockEntityType<?>, BlockEntityType<?>> holder = BES.get(name);
    if (holder == null) throw new IllegalArgumentException("No controller block entity registered for machine: " + machineId);
    return holder;
}
```

- [ ] **Step 7: Update test bootstrap binding**

Modify `src/test/java/cn/howxu/mmcr/test/TestBootstrap.java`:

```java
bind(ModBlocks.controllerFor(cn.howxu.mmcr.MMCR.id("blast_furnace")), Blocks.IRON_BLOCK);
```

Remove the direct `bind(ModBlocks.CONTROLLER, Blocks.IRON_BLOCK);` line if it becomes duplicate. Keep IO port bindings unchanged.

- [ ] **Step 8: Run focused registration tests**

Run: `./gradlew test --tests cn.howxu.mmcr.registry.MachineControllerRegistrationTest`

Expected: PASS.

---

### Task 3: Default Machine Pattern Uses Its Generated Controller

**Files:**
- Modify: `src/main/java/org/nibelungorum/DefaultMachines.java`
- Modify: `src/test/java/cn/howxu/mmcr/internal/machine/DefaultMachinesTest.java`
- Modify: `src/test/java/cn/howxu/mmcr/api/machine/BuildPlacementConsistencyTest.java`

**Interfaces:**
- Consumes: `ModBlocks.controllerFor(Identifier)` from Task 2.
- Produces: `DefaultMachines.blastFurnace(Block casing, Block itemPort, Block fluidPort)` and machine patterns whose `C` predicate points to `blast_furnace_controller`.

- [ ] **Step 1: Update failing tests for new default machine signature**

Modify `DefaultMachinesTest` assertions:

```java
assertThat(machine.controller().id()).isEqualTo(MMCR.id("blast_furnace_controller"));
assertThat(machine.pattern().get(BlockPos.ZERO))
        .isEqualTo(new BlockPredicate.OfBlock(ModBlocks.controllerFor(machine).get()));
```

Modify `default_blast_furnace_raw_pattern_faces_south` machine creation:

```java
Machine machine = DefaultMachines.blastFurnace(
        ModBlocks.CASING.get(),
        ModBlocks.BLOCKS.get("io_port_item_basic").get(),
        ModBlocks.BLOCKS.get("io_port_fluid_basic").get());
```

Modify `BuildPlacementConsistencyTest`:

```java
assertThat(world.get(controller).getBlock()).isEqualTo(ModBlocks.controllerFor(machine).get());
```

And fixture:

```java
return DefaultMachines.blastFurnace(Blocks.STONE, Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS);
```

- [ ] **Step 2: Run tests and verify they fail before implementation**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.machine.DefaultMachinesTest --tests cn.howxu.mmcr.api.machine.BuildPlacementConsistencyTest`

Expected: compile failure because `blastFurnace(Block, Block, Block)` does not exist and placement helper still uses old controller.

- [ ] **Step 3: Update `DefaultMachines`**

Modify `src/main/java/org/nibelungorum/DefaultMachines.java`:

```java
public static void ensureRegistered() {
    if (MachineRegistry.getMachine(BLAST_FURNACE_ID) == null) {
        Block casing = ModBlocks.CASING.get();
        Block itemPort = ModBlocks.BLOCKS.get("io_port_item_basic").get();
        Block fluidPort = ModBlocks.BLOCKS.get("io_port_fluid_basic").get();
        MachineRegistry.register(blastFurnace(casing, itemPort, fluidPort));
    }
}

public static Machine blastFurnace(Block casing, Block itemPort, Block fluidPort) {
    Block controller = ModBlocks.controllerFor(BLAST_FURNACE_ID).get();
    BlockPredicate ioPort = new BlockPredicate.AnyOf(List.of(
            new BlockPredicate.OfBlock(itemPort),
            new BlockPredicate.OfBlock(fluidPort)));

    BlockArray pattern = BlockArray.builder()
            .pattern(
                    "XXX", "XIX", "XXX",
                    "XXX", "I I", "XXX",
                    "XXX", "XCX", "XXX")
            .set('X', new BlockPredicate.OfBlock(casing))
            .set('C', new BlockPredicate.OfBlock(controller))
            .set('I', ioPort)
            .build();

    return new DynamicMachine(BLAST_FURNACE_ID, "高炉", pattern);
}
```

Remove the obsolete four-argument `blastFurnace` overload unless a compile error proves a non-test caller still requires it. Do not keep backward compatibility without a concrete need.

- [ ] **Step 4: Update `BuildPlacementConsistencyTest` helper**

Change controller placement in `buildPlacement`:

```java
BlockState ctrlBase = ModBlocks.controllerFor(machine).get().defaultBlockState();
```

- [ ] **Step 5: Run focused tests**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.machine.DefaultMachinesTest --tests cn.howxu.mmcr.api.machine.BuildPlacementConsistencyTest`

Expected: PASS.

---

### Task 4: Controller Block Entity Binds Machine From Controller Block

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java`
- Modify: `src/main/java/cn/howxu/mmcr/internal/command/BuildCommand.java`
- Test: `src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntityTest.java`
- Test: `src/test/java/cn/howxu/mmcr/internal/command/BuildCommandPlacementTest.java` if direct command testing is practical; otherwise extend `BuildPlacementConsistencyTest`.

**Interfaces:**
- Consumes: `MachineControllerBlock.machineId()`, `ModBlockEntities.controllerFor(Identifier)`, `ModBlocks.controllerFor(Machine)`.
- Produces: `MachineControllerBlockEntity` that no longer hardcodes `blast_furnace`, and `BuildCommand` that places machine-specific controller blocks.

- [ ] **Step 1: Write failing BE binding test**

Create `src/test/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntityTest.java`:

```java
package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.nibelungorum.DefaultMachines;

import static org.assertj.core.api.Assertions.assertThat;

class MachineControllerBlockEntityTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @AfterEach
    void cleanup() {
        MachineRegistry.clearForTesting();
    }

    @Test
    void bind_default_machine_uses_owning_controller_block_machine_id() {
        DefaultMachines.ensureRegistered();
        var state = ModBlocks.controllerFor(MMCR.id("blast_furnace")).get().defaultBlockState();
        var be = new MachineControllerBlockEntity(BlockPos.ZERO, state);

        be.bindDefaultMachine();

        assertThat(be.getMachine()).isSameAs(MachineRegistry.getMachine(MMCR.id("blast_furnace")));
    }
}
```

This test requires `bindDefaultMachine()` to become package-private or public for direct verification. Prefer package-private if the test is placed in `cn.howxu.mmcr.internal.tile`.

- [ ] **Step 2: Run test and verify it fails**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.tile.MachineControllerBlockEntityTest`

Expected: compile failure because `bindDefaultMachine()` is private, or assertion failure because it hardcodes old behavior.

- [ ] **Step 3: Update BE constructor and binding**

Modify `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java` imports:

```java
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
```

Change constructor:

```java
public MachineControllerBlockEntity(BlockPos pos, BlockState state) {
    super(ModBlockEntities.controllerFor(machineIdFromState(state)).get(), pos, state);
}
```

Add helper:

```java
private static Identifier machineIdFromState(BlockState state) {
    if (state.getBlock() instanceof MachineControllerBlock controller) {
        return controller.machineId();
    }
    throw new IllegalArgumentException("MachineControllerBlockEntity requires a MachineControllerBlock state");
}
```

Change binding method from private to package-private:

```java
void bindDefaultMachine() {
    DefaultMachines.ensureRegistered();
    setMachine(cn.howxu.mmcr.api.machine.MachineRegistry.getMachine(machineIdFromState(getBlockState())));
}
```

Keep `serverTick()` calling `bindDefaultMachine()` when `machine == null`.

- [ ] **Step 4: Update BuildCommand placement**

Modify `src/main/java/cn/howxu/mmcr/internal/command/BuildCommand.java`:

```java
BlockState controllerState = ModBlocks.controllerFor(machine).get().defaultBlockState()
        .setValue(BlockStateProperties.HORIZONTAL_FACING, ctrlFacing);
```

Remove the direct `ModBlocks.CONTROLLER` usage from `placeMachine`.

- [ ] **Step 5: Run focused BE and placement tests**

Run: `./gradlew test --tests cn.howxu.mmcr.internal.tile.MachineControllerBlockEntityTest --tests cn.howxu.mmcr.api.machine.BuildPlacementConsistencyTest`

Expected: PASS.

---

### Task 5: Dynamic Datagen and Final Verification

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/datagen/ModelGen.java`
- Modify: `src/main/java/cn/howxu/mmcr/datagen/Translations.java`
- Modify: generated resources under `src/generated/resources/assets/mmcr/` after running datagen.

**Interfaces:**
- Consumes: `MachineControllerSpec.defaultsFor(MMCR.id("blast_furnace"))`, `ModBlocks.controllerFor(Identifier)`, `ModItems.ITEMS`.
- Produces: generated blockstate/model/item model for `blast_furnace_controller`, and lang entries for block/item names.

- [ ] **Step 1: Update datagen model code**

Modify `src/main/java/cn/howxu/mmcr/datagen/ModelGen.java` imports:

```java
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import net.minecraft.resources.Identifier;
```

Add helper:

```java
private static final Identifier BLAST_FURNACE_ID = MMCR.id("blast_furnace");
```

Update controller branch in `registerModels`:

```java
MachineControllerSpec controller = MachineControllerSpec.defaultsFor(BLAST_FURNACE_ID);
String controllerName = controller.id().getPath();

ModBlocks.BLOCKS.forEach((name, blockHolder) -> {
    Block block = blockHolder.get();

    if (controllerName.equals(name)) {
        blockModels.createHorizontallyRotatedBlock(block, TexturedModel.ORIENTABLE.updateTexture(m -> m
                .put(TextureSlot.FRONT, new Material(controller.frontTexture()))
                .put(TextureSlot.SIDE, new Material(controller.sideTexture()))
                .put(TextureSlot.TOP, new Material(controller.topTexture()))
                .put(TextureSlot.BOTTOM, new Material(controller.bottomTexture()))));
        blockModels.registerSimpleItemModel(block.asItem(), ModelLocationUtils.getModelLocation(block));
    } else {
        blockModels.createTrivialBlock(block, TexturedModel.CUBE.updateTexture(
                m -> m.put(TextureSlot.ALL, textureFor(name))));
    }
});
```

If the `Material` constructor requires a texture atlas argument in this mappings version, use the existing `textureFor(String)` helper style by adding an overload:

```java
private static Material textureFor(Identifier texture) {
    return new Material(texture);
}
```

Then use `textureFor(controller.frontTexture())` etc. Compile will confirm the exact constructor shape.

- [ ] **Step 2: Update translations**

Modify `src/main/java/cn/howxu/mmcr/datagen/Translations.java` by replacing old controller keys with:

```java
Map.entry("block.mmcr.blast_furnace_controller", "Blast Furnace Controller"),
Map.entry("item.mmcr.blast_furnace_controller", "Blast Furnace Controller"),
```

For `zh_cn` add:

```java
Map.entry("block.mmcr.blast_furnace_controller", "高炉控制器"),
Map.entry("item.mmcr.blast_furnace_controller", "高炉控制器"),
```

Keep casing and IO port translations unchanged.

- [ ] **Step 3: Run unit tests**

Run: `./gradlew test`

Expected: PASS.

- [ ] **Step 4: Run datagen**

Run: `./gradlew runClientData`

Expected: task succeeds and generated resources include:

- `src/generated/resources/assets/mmcr/blockstates/blast_furnace_controller.json`
- `src/generated/resources/assets/mmcr/models/block/blast_furnace_controller.json`
- `src/generated/resources/assets/mmcr/items/blast_furnace_controller.json` or the item definition path used by this mappings version
- `src/generated/resources/assets/mmcr/lang/en_us.json`
- `src/generated/resources/assets/mmcr/lang/zh_cn.json`

- [ ] **Step 5: Verify no business path uses global controller**

Run: `rg "ModBlocks\.CONTROLLER|get\(\"controller\"\)|\"controller\"\.equals" src/main/java src/test/java`

Expected: no matches in business paths. Matches are acceptable only if they are comments explaining legacy behavior or compatibility constants; otherwise update them to `controllerFor(...)` or `blast_furnace_controller`.

- [ ] **Step 6: Run full build**

Run: `./gradlew build`

Expected: PASS.

- [ ] **Step 7: Inspect git diff**

Run: `git diff -- src/main/java src/test/java src/generated/resources docs/superpowers/plans/2026-08-03-generic-machine-controller.md docs/superpowers/specs/2026-08-03-generic-machine-controller-design.md`

Expected: diff only contains controller generalization, tests, datagen outputs, and the approved docs.

---

## Self-Review

- Spec coverage: Tasks 1-2 cover D1/D2/D4 and the controller spec/API; Task 3 covers generated controller in default machine patterns; Task 4 covers BE binding and build placement; Task 5 covers D5 datagen and final no-global-controller verification; D3 is preserved by not changing IO port behavior.
- Placeholder scan: no `TBD`, no open-ended “handle edge cases”, and no “similar to” implementation steps.
- Type consistency: all later tasks consume `MachineControllerSpec`, `Machine.controller()`, `ModBlocks.controllerFor(...)`, and `ModBlockEntities.controllerFor(...)` as defined earlier.
