# Controller Crafting Tick Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move `MachineControllerBlockEntity` recipe execution details behind `ActiveMachineRecipe` and a new `RecipeCraftingContext`, and add NBT persistence for active recipe state.

**Architecture:** A new `RecipeCraftingContext` owns the `IItemHandler` / `IFluidHandler` / energy storage access that used to live as `findAndCheck*` helpers and `consumeAndProduce` inside `MachineControllerBlockEntity`. The controller keeps lifecycle responsibilities, holds an `ActiveMachineRecipe` field instead of a raw `MachineRecipe`, and delegates tick work to `active.tick(context)`. The existing `ActiveMachineRecipe` class gains a `TickStatus` enum and a `tick(...)` method that drives the staged simulate/commit protocol. The controller gains `loadAdditional` / `saveAdditional` so active progress survives chunk reload.

**Tech Stack:** Java 21, Minecraft 26.1.2 NeoForge, existing `MachineRecipe` / `MachineIngredient` / `ActiveMachineRecipe` / `RecipeRegistry` API. No new dependencies.

## Global Constraints

- Spec refactor scope only; no new gameplay features beyond what `2026-08-04-controller-crafting-tick-design.md` describes.
- Default `maxParallelism` / `parallelism` to `1`; controller never constructs an `ActiveMachineRecipe` with anything larger in this step.
- `ActiveMachineRecipe` fields (`maxParallelism`, `parallelism`, `data`) are kept for forward compatibility but unused this step.
- Commit order is fixed: outputs first, then inputs.
- Energy remains a total completion cost (`fePerTick * recipe.tickTime()`); no per-tick drain this step.
- Fluid output is out of scope; explicitly deferred.
- Do not change the public API surface that the GUI relies on (`getActiveRecipe()` returning `MachineRecipe`, `getTickCounter()` returning `int`).
- Do not change existing `ActiveMachineRecipe` public method signatures except as needed to add the new tick / status support.
- Keep the existing `E2ERecipeRunGameTest.ironCompressorRuns` passing as-is.
- Project conventions: no comments unless behaviour is non-obvious, no unrelated refactors, AGENTS.md "do not introduce unregistered tiers / new blocks / MMCE complexity too early".

---

## File Structure

- Create `src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContext.java` — staged simulate/commit for a formed controller.
- Modify `src/main/java/cn/howxu/mmcr/api/recipe/ActiveMachineRecipe.java` — add `TickStatus` enum and `tick(RecipeCraftingContext)` method.
- Modify `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java` — replace `MachineRecipe activeRecipe` with `ActiveMachineRecipe active` + `RecipeCraftingContext context`; delegate tick; add `loadAdditional` / `saveAdditional`.

No other files are touched by this refactor. The GUI (`MachineMenuScreen`), the network payload (`PktMachineStatePayload`), and the registry (`ModBlockEntities`) keep working through the existing getter signatures.

---

## Task 1: Add `RecipeCraftingContext`

**Files:**
- Create: `src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContext.java`

**Interfaces:**
- Consumes: a `Level` and the controller's `BlockPos` at construction.
- Produces (consumed by later tasks):
  - `public RecipeCraftingContext(Level level, BlockPos controllerPos)`
  - `public boolean simulateInputs(MachineRecipe recipe)`
  - `public boolean simulateOutputs(MachineRecipe recipe)`
  - `public boolean commitOutputs(MachineRecipe recipe)`
  - `public boolean commitInputs(MachineRecipe recipe)`
- No mutable per-tick state beyond resolved component locations.
- Hides `IItemHandler`, `IFluidHandler`, and energy storage from callers.

- [ ] **Step 1: Create the new class file**

Create `src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContext.java` with this exact content:

```java
package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.internal.tile.EnergyInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

public final class RecipeCraftingContext {

    private final Level level;
    private final BlockPos controllerPos;

    public RecipeCraftingContext(Level level, BlockPos controllerPos) {
        this.level = level;
        this.controllerPos = controllerPos;
    }

    public boolean simulateInputs(MachineRecipe recipe) {
        for (MachineIngredient ingredient : recipe.inputs()) {
            if (ingredient instanceof MachineIngredient.ItemIngredient item && findAndCheckItemBus(item) == null) return false;
            if (ingredient instanceof MachineIngredient.FluidIngredient fluid && findAndCheckFluidHatch(fluid) == null) return false;
            if (ingredient instanceof MachineIngredient.EnergyIngredient energy && findAndCheckEnergyHatch(energy, recipe) == null) return false;
        }
        return true;
    }

    public boolean simulateOutputs(MachineRecipe recipe) {
        List<OutputSlotState> slots = outputSlotStates();
        for (ItemStack output : recipe.outputs()) {
            ItemStack remaining = output.copy();
            for (OutputSlotState slot : slots) {
                if (remaining.isEmpty()) break;
                remaining = slot.insert(remaining);
            }
            if (!remaining.isEmpty()) return false;
        }
        return true;
    }

    public boolean commitOutputs(MachineRecipe recipe) {
        for (ItemStack output : recipe.outputs()) {
            ItemStack remaining = output.copy();
            for (OutputSlot slot : outputSlots()) {
                if (remaining.isEmpty()) break;
                remaining = slot.handler().insertItem(slot.slot(), remaining, false);
            }
            if (!remaining.isEmpty()) return false;
        }
        return true;
    }

    public boolean commitInputs(MachineRecipe recipe) {
        for (MachineIngredient ingredient : recipe.inputs()) {
            if (ingredient instanceof MachineIngredient.ItemIngredient item) {
                ItemInputBusBlockEntity bus = findAndCheckItemBus(item);
                if (bus == null) continue;
                IItemHandler handler = bus.getItemHandler(null);
                int left = item.count();
                for (int slot = 0; slot < handler.getSlots() && left > 0; slot++) {
                    ItemStack stack = handler.getStackInSlot(slot);
                    if (item.item().test(stack)) {
                        int taken = Math.min(left, stack.getCount());
                        handler.extractItem(slot, taken, false);
                        left -= taken;
                    }
                }
            } else if (ingredient instanceof MachineIngredient.FluidIngredient fluid) {
                FluidInputHatchBlockEntity hatch = findAndCheckFluidHatch(fluid);
                if (hatch != null) hatch.getFluidHandler(null).drain(fluid.amount(), IFluidHandler.FluidAction.EXECUTE);
            } else if (ingredient instanceof MachineIngredient.EnergyIngredient energy) {
                EnergyInputHatchBlockEntity hatch = findAndCheckEnergyHatch(energy, recipe);
                if (hatch != null) hatch.getEnergyStorage(null).extractEnergy(energy.fePerTick() * recipe.tickTime(), false);
            }
        }
        return true;
    }

    private ItemInputBusBlockEntity findAndCheckItemBus(MachineIngredient.ItemIngredient ingredient) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (level.getBlockEntity(controllerPos.offset(dx, 1, dz)) instanceof ItemInputBusBlockEntity bus) {
                IItemHandler handler = bus.getItemHandler(null);
                int count = 0;
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    ItemStack stack = handler.getStackInSlot(slot);
                    if (ingredient.item().test(stack)) count += stack.getCount();
                }
                if (count >= ingredient.count()) return bus;
            }
        }
        return null;
    }

    private FluidInputHatchBlockEntity findAndCheckFluidHatch(MachineIngredient.FluidIngredient ingredient) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (level.getBlockEntity(controllerPos.offset(dx, 1, dz)) instanceof FluidInputHatchBlockEntity hatch
                    && ingredient.fluid().test(hatch.getFluidHandler(null).getFluidInTank(0))
                    && hatch.getFluidHandler(null).getFluidInTank(0).getAmount() >= ingredient.amount()) return hatch;
        }
        return null;
    }

    private EnergyInputHatchBlockEntity findAndCheckEnergyHatch(MachineIngredient.EnergyIngredient ingredient, MachineRecipe recipe) {
        int required = ingredient.fePerTick() * recipe.tickTime();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (level.getBlockEntity(controllerPos.offset(dx, 1, dz)) instanceof EnergyInputHatchBlockEntity hatch
                    && hatch.getEnergyStorage(null).getEnergyStored() >= required) return hatch;
        }
        return null;
    }

    private List<OutputSlot> outputSlots() {
        List<OutputSlot> slots = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (level.getBlockEntity(controllerPos.offset(dx, 1, dz)) instanceof ItemOutputBusBlockEntity bus) {
                IItemHandler handler = bus.getItemHandler(null);
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    slots.add(new OutputSlot(handler, slot));
                }
            }
        }
        return slots;
    }

    private List<OutputSlotState> outputSlotStates() {
        return outputSlots().stream().map(OutputSlotState::new).toList();
    }

    private record OutputSlot(IItemHandler handler, int slot) {}

    private static final class OutputSlotState {
        private final OutputSlot slot;
        private ItemStack stack;

        private OutputSlotState(OutputSlot slot) {
            this.slot = slot;
            this.stack = slot.handler().getStackInSlot(slot.slot()).copy();
        }

        private ItemStack insert(ItemStack input) {
            ItemStack accepted = input.copy();
            ItemStack simulatedRemainder = slot.handler().insertItem(slot.slot(), accepted, true);
            accepted.shrink(simulatedRemainder.getCount());
            if (accepted.isEmpty()) return input;
            if (!stack.isEmpty() && !ItemStack.isSameItemSameComponents(stack, accepted)) return input;

            int limit = Math.min(slot.handler().getSlotLimit(slot.slot()), accepted.getMaxStackSize());
            int space = stack.isEmpty() ? limit : limit - stack.getCount();
            int inserted = Math.min(space, accepted.getCount());
            if (inserted <= 0) return input;

            if (stack.isEmpty()) {
                stack = accepted.copyWithCount(inserted);
            } else {
                stack.grow(inserted);
            }
            ItemStack remaining = input.copy();
            remaining.shrink(inserted);
            return remaining;
        }
    }
}
```

- [ ] **Step 2: Compile to confirm the new class builds in isolation**

Run: `./gradlew compileJava --no-daemon`
Expected: `BUILD SUCCESSFUL`. The class is currently unused so this only confirms the file compiles.

- [ ] **Step 3: Commit the new context class**

```bash
rtk git add src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContext.java
rtk git commit -m "feat(recipe): add RecipeCraftingContext for staged simulate/commit"
```

---

## Task 2: Add `TickStatus` and `tick(...)` to `ActiveMachineRecipe`

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/api/recipe/ActiveMachineRecipe.java`

**Interfaces:**
- Adds (consumed by Task 3):
  - `public enum TickStatus { CONTINUE, WAITING, FINISHED }`
  - `public TickStatus tick(RecipeCraftingContext context)` — increments `tick` while preserving the existing pause/wait semantics; on completion runs re-simulate + commit and returns `FINISHED`; on failure to commit returns `WAITING` and pins `tick = totalTick - 1`.
- No existing public method signature is removed.
- `tick` is intentionally pinned at `totalTick - 1` on failure so the recipe does not progress past completion when inputs/outputs are not available.

- [ ] **Step 1: Add the `TickStatus` enum and the `tick` method**

Open `src/main/java/cn/howxu/mmcr/api/recipe/ActiveMachineRecipe.java` and apply these edits.

Add immediately above the closing `}` of the class (just after the `serialize()` method):

```java
    public enum TickStatus {
        CONTINUE,
        WAITING,
        FINISHED
    }

    public TickStatus tick(RecipeCraftingContext context) {
        if (recipe == null) {
            return TickStatus.WAITING;
        }
        int total = getTotalTick();
        if (total <= 0) {
            return TickStatus.WAITING;
        }
        int nextTick = Math.min(getTick() + 1, total);
        setTick(nextTick);

        if (!isCompleted()) {
            return TickStatus.CONTINUE;
        }

        if (!context.simulateOutputs(recipe) || !context.simulateInputs(recipe)) {
            setTick(Math.max(0, total - 1));
            return TickStatus.WAITING;
        }

        boolean outputsOk = context.commitOutputs(recipe);
        boolean inputsOk = context.commitInputs(recipe);
        if (!outputsOk || !inputsOk) {
            setTick(Math.max(0, total - 1));
            return TickStatus.WAITING;
        }

        return TickStatus.FINISHED;
    }
```

- [ ] **Step 2: Compile to confirm the additions build**

Run: `./gradlew compileJava --no-daemon`
Expected: `BUILD SUCCESSFUL`. The new method is referenced from the same package, so any typo in the body will surface here.

- [ ] **Step 3: Run the existing recipe API smoke tests to confirm nothing regressed**

Run: `./gradlew test --no-daemon --tests cn.howxu.mmcr.api.recipe.RecipeApiSmokeTest`
Expected: `BUILD SUCCESSFUL` and the existing `active_recipe_*` tests pass. The new `tick` method is unused by these tests so this only confirms the file's existing behaviour is intact.

- [ ] **Step 4: Commit the additions**

```bash
rtk git add src/main/java/cn/howxu/mmcr/api/recipe/ActiveMachineRecipe.java
rtk git commit -m "feat(recipe): add ActiveMachineRecipe.tick with TickStatus"
```

---

## Task 3: Refactor `MachineControllerBlockEntity` to use the new objects

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java`

**Interfaces:**
- Field swap:
  - Remove `private MachineRecipe activeRecipe;` and `private int tickCounter = 0;`.
  - Add `private ActiveMachineRecipe active;` and `private RecipeCraftingContext context;`.
- Getter compatibility (must remain unchanged so `MachineMenuScreen` keeps working):
  - `public MachineRecipe getActiveRecipe()` returns `active == null ? null : active.getRecipe()`.
  - `public int getTickCounter()` returns `active == null ? 0 : active.getTick()`.
- `setActiveRecipe(MachineRecipe)` is removed; the controller no longer exposes a setter for raw recipes.
- `serverTick()` orchestrates: bind → checkStructure → if formed and `active == null` call `tryStartNewRecipe()`; if `active != null` call `active.tick(context)` and clear `active` + `context` on `FINISHED`.
- `tryStartNewRecipe()` builds both `ActiveMachineRecipe` (always with `maxParallelism = 1`) and `RecipeCraftingContext`, gated by `context.simulateInputs(recipe) && context.simulateOutputs(recipe)`.
- `resetMachine()` and structure-break paths clear `active` and `context` together.
- The previous `findAndCheck*` helpers, `OutputSlot` / `OutputSlotState` records, `consumeAndProduce`, and `canAcceptOutputs` are removed from the controller.

- [ ] **Step 1: Rewrite the controller body**

Replace the entire contents of `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java` with this exact file:

```java
package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockArrayCache;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.StructureMatcher;
import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContext;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.network.PktMachineStatePayload;
import cn.howxu.mmcr.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.nibelungorum.DefaultMachines;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MachineControllerBlockEntity extends BlockEntity {

    private Machine machine;
    private Machine foundMachine;
    private BlockArray foundPattern;
    private Direction controllerFacing;
    private ActiveMachineRecipe active;
    private RecipeCraftingContext context;

    public MachineControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.controllerFor(machineIdFromState(state)).get(), pos, state);
    }

    private static Identifier machineIdFromState(BlockState state) {
        if (state.getBlock() instanceof MachineControllerBlock controller) {
            return controller.machineId();
        }
        throw new IllegalArgumentException("MachineControllerBlockEntity requires a MachineControllerBlock state");
    }

    public Machine getMachine() { return machine; }
    public void setMachine(Machine m) { this.machine = m; setChanged(); }

    public Machine getFoundMachine() { return foundMachine; }
    public BlockArray getFoundPattern() { return foundPattern; }

    public boolean isFormed() { return getBlockState().getValue(MachineControllerBlock.FORMED); }
    public void setFormed(boolean f) {
        level.setBlock(getBlockPos(), getBlockState().setValue(MachineControllerBlock.FORMED, f), 3);
    }

    public MachineRecipe getActiveRecipe() { return active == null ? null : active.getRecipe(); }

    public int getTickCounter() { return active == null ? 0 : active.getTick(); }

    public ActiveMachineRecipe getActive() { return active; }

    public void serverTick() {
        if (level == null || level.isClientSide()) return;
        if (machine == null) bindDefaultMachine();

        checkStructure();
        if (isFormed()) {
            if (active == null) tryStartNewRecipe();
            if (active != null) tickActiveRecipe();
        }
        broadcastState();
    }

    private void checkStructure() {
        Direction facing = getBlockState().getValue(MachineControllerBlock.FACING);
        if (foundMachine != null && foundPattern != null && controllerFacing == facing) {
            if (StructureMatcher.matchesRotated(foundPattern, level, getBlockPos())) {
                if (!isFormed()) setFormed(true);
                return;
            }
            resetMachine();
        }

        if (machine != null && tryFormMachine(machine, facing)) return;
        checkAllPatterns(facing);
        if (!isFormed()) resetMachine();
    }

    private void checkAllPatterns(Direction facing) {
        for (Machine candidate : MachineRegistry.getAll().values()) {
            if (candidate == machine) continue;
            if (tryFormMachine(candidate, facing)) return;
        }
    }

    private boolean tryFormMachine(Machine candidate, Direction facing) {
        BlockArray rotatedPattern = BlockArrayCache.get(candidate.pattern(), facing);
        if (!StructureMatcher.matchesRotated(rotatedPattern, level, getBlockPos())) return false;

        onStructureFormed(candidate, rotatedPattern, facing);
        return true;
    }

    private void onStructureFormed(Machine matchedMachine, BlockArray rotatedPattern, Direction facing) {
        foundMachine = matchedMachine;
        foundPattern = rotatedPattern;
        controllerFacing = facing;
        machine = matchedMachine;
        if (!isFormed()) setFormed(true);
        setChanged();
    }

    private void resetMachine() {
        foundMachine = null;
        foundPattern = null;
        controllerFacing = null;
        if (active != null) {
            active = null;
            context = null;
        }
        if (isFormed()) setFormed(false);
        setChanged();
    }

    private void broadcastState() {
        if (!(level instanceof ServerLevel sl)) return;
        String name = active == null ? "" : active.getRecipe().id().toString();
        var pkt = new PktMachineStatePayload(getBlockPos(), name, isFormed());
        for (var player : sl.getPlayers(p -> p.distanceToSqr(getBlockPos().getCenter()) < 64 * 64)) {
            ((ServerPlayer) player).connection.send(new ClientboundCustomPayloadPacket(pkt));
        }
    }

    private void tryStartNewRecipe() {
        for (MachineRecipe recipe : recipesForMachine()) {
            RecipeCraftingContext candidate = new RecipeCraftingContext(level, getBlockPos());
            if (!candidate.simulateInputs(recipe) || !candidate.simulateOutputs(recipe)) continue;
            ActiveMachineRecipe next = new ActiveMachineRecipe(recipe, 1);
            active = next;
            context = candidate;
            setChanged();
            return;
        }
    }

    private void tickActiveRecipe() {
        if (active == null || context == null) return;
        ActiveMachineRecipe.TickStatus status = active.tick(context);
        if (status == ActiveMachineRecipe.TickStatus.FINISHED) {
            active = null;
            context = null;
        }
        setChanged();
    }

    void bindDefaultMachine() {
        bindDefaultMachine(machineIdFromState(getBlockState()));
    }

    void bindDefaultMachine(Identifier machineId) {
        DefaultMachines.ensureRegistered();
        setMachine(cn.howxu.mmcr.api.machine.MachineRegistry.getMachine(machineId));
    }

    private List<MachineRecipe> recipesForMachine() {
        Map<Identifier, MachineRecipe> recipes = new LinkedHashMap<>();
        for (MachineRecipe recipe : RecipeRegistry.byMachine(machine)) {
            recipes.put(recipe.id(), recipe);
        }
        if (level instanceof ServerLevel sl) {
            for (RecipeHolder<?> holder : sl.recipeAccess().getRecipes()) {
                if (holder.value() instanceof MachineRecipe recipe
                        && recipe.machineId().equals(machine.registryName())) {
                    recipes.putIfAbsent(recipe.id(), recipe);
                }
            }
        }
        return new ArrayList<>(recipes.values());
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava --no-daemon`
Expected: `BUILD SUCCESSFUL`. Any missed import or field access surfaces here.

- [ ] **Step 3: Run the existing GameTest to confirm runtime behaviour is preserved**

Run: `./gradlew gametest --no-daemon --tests cn.howxu.mmcr.E2ERecipeRunGameTest`
Expected: the `ironCompressorRuns` test passes (40 ticks, input consumed, output inserted, energy reduced by `80 * 40 = 3200 FE`, leaving `6800 FE`). If the runner is not configured for this project, fall back to `./gradlew build --no-daemon` and confirm `BUILD SUCCESSFUL`. The user can also run the GameTest inside a dev client; the plan treats the GameTest as the runtime gate.

- [ ] **Step 4: Commit the controller refactor**

```bash
rtk git add src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java
rtk git commit -m "refactor(controller): delegate recipe execution to ActiveMachineRecipe and RecipeCraftingContext"
```

---

## Task 4: Add NBT persistence for the active recipe

**Files:**
- Modify: `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java`

**Interfaces:**
- Adds (consumed by Task 5 only as verification):
  - `protected void saveAdditional(CompoundTag tag)` — writes the active recipe using `active.serialize()` under key `"active_recipe"`, plus a `"has_active"` boolean. If `active == null` the boolean is `false` and no payload is written.
  - `public void loadAdditional(CompoundTag tag)` — reads the boolean and reconstructs the `ActiveMachineRecipe` via `new ActiveMachineRecipe(CompoundTag)`. If the saved recipe id no longer resolves, `active` and `context` stay `null` and the load is treated as "no active recipe".
- No existing public method signature changes; `BlockEntity.saveAdditional` / `loadAdditional` overrides are simply added.

- [ ] **Step 1: Add the persistence overrides to the controller**

Open `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java` and apply these two edits.

Add the import for `CompoundTag`:

```java
import net.minecraft.nbt.CompoundTag;
```

Add the two methods immediately after `public Machine getMachine()` and `setMachine`, or — to keep the diff minimal — right before the closing `}` of the class:

```java
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (active != null) {
            tag.putBoolean("has_active", true);
            tag.put("active_recipe", active.serialize());
        } else {
            tag.putBoolean("has_active", false);
        }
    }

    @Override
    public void loadAdditional(CompoundTag tag) {
        super.loadAdditional(tag);
        if (!tag.getBooleanOr("has_active", false)) {
            active = null;
            context = null;
            return;
        }
        CompoundTag recipeTag = tag.getCompoundOrEmpty("active_recipe");
        ActiveMachineRecipe restored = new ActiveMachineRecipe(recipeTag);
        if (restored.getRecipe() == null) {
            active = null;
            context = null;
            return;
        }
        active = restored;
        context = new RecipeCraftingContext(level, getBlockPos());
        setChanged();
    }
```

The exact insertion point does not matter; place them at the bottom of the class.

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava --no-daemon`
Expected: `BUILD SUCCESSFUL`. `getBooleanOr` and `getCompoundOrEmpty` are the `CompoundTag` accessor names that already appear in `ActiveMachineRecipe` (see `ActiveMachineRecipe.java:31,35`) and that the existing `RecipeApiSmokeTest` exercises through NBT roundtrip, so this should compile without surprise.

- [ ] **Step 3: Run the GameTest once more to confirm persistence changes did not regress runtime**

Run: `./gradlew gametest --no-daemon --tests cn.howxu.mmcr.E2ERecipeRunGameTest`
Expected: still passing. Persistence is only exercised on chunk unload / reload; in the GameTest the controller is not unloaded mid-test, so this run guards against a compile-time or startup regression introduced by the new overrides.

- [ ] **Step 4: Commit persistence**

```bash
rtk git add src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java
rtk git commit -m "feat(controller): persist ActiveMachineRecipe across chunk reload"
```

---

## Task 5: Final verification pass

**Files:**
- No new code changes expected.
- Optional inspect-only: `src/main/java/cn/howxu/mmcr/client/gui/MachineMenuScreen.java` (to confirm `getActiveRecipe()` + `getTickCounter()` still produce the same values) and `src/main/java/cn/howxu/mmcr/internal/network/PktMachineStatePayload.java` (to confirm the broadcast payload contract is unchanged).

**Interfaces:**
- No API changes. This task is a final compile + GameTest + spot-check pass.

- [ ] **Step 1: Full project compile + build**

Run: `./gradlew build --no-daemon`
Expected: `BUILD SUCCESSFUL`. Any pre-existing unrelated build blocker that appears here is recorded as a known issue (per spec acceptance criterion 5) and is not fixed by this refactor.

- [ ] **Step 2: Re-run the E2E GameTest from a clean state**

Run: `./gradlew clean gametest --no-daemon --tests cn.howxu.mmcr.E2ERecipeRunGameTest`
Expected: `BUILD SUCCESSFUL` and the `ironCompressorRuns` test passes against a clean build. This is the final runtime gate.

- [ ] **Step 3: Spot-check the GUI compatibility assumptions**

Open `src/main/java/cn/howxu/mmcr/client/gui/MachineMenuScreen.java` and confirm:

- `MachineMenuScreen` still calls `owner.getActiveRecipe()` and `owner.getTickCounter()` only (no new dependency on `getActive()` or on `RecipeCraftingContext`).
- The translated status string keys (`gui.mmcr.controller.*`) and progress text remain unchanged.

Open `src/main/java/cn/howxu/mmcr/internal/network/PktMachineStatePayload.java` and confirm:

- The payload still takes the active recipe id as a `String` and the formed state as a `boolean`, matching the values produced by `broadcastState()` in the refactored controller.

If the spot check finds a divergence, fix the divergence in the smallest possible way (controller-side getter adjustment, not GUI changes) and rerun `./gradlew compileJava --no-daemon`. Otherwise no code change is needed.

- [ ] **Step 4: Final commit (only if step 3 needed a tweak)**

If step 3 surfaced and required a fix:

```bash
rtk git add src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java
rtk git commit -m "fix(controller): align getters with GUI and network payload contract"
```

If no code change was required, skip this commit and end the plan here.

---

## Self-Review Notes

- **Spec coverage:**
  - Goals (formed controller runs a recipe using item/fluid/energy + item output) — Tasks 1, 2, 3.
  - Fluid output deferral — Goal section plus Deferred Work in the spec; Task 1's `commitOutputs` is item-only.
  - `ActiveMachineRecipe` adopted as controller field — Task 3 field swap.
  - NBT persistence — Task 4.
  - `E2ERecipeRunGameTest.ironCompressorRuns` kept passing — Tasks 3 and 5 verification gates.
  - Deferred Work document — out of scope for this plan; spec calls for it as a follow-up doc, not a code change.
- **Placeholder scan:** no `TBD` / `TODO` / "implement later" / "similar to Task N" placeholders. Each step shows exact file paths and code.
- **Type consistency:** `ActiveMachineRecipe.TickStatus` enum, `RecipeCraftingContext` method names, and `controller.getActive()` / `getActiveRecipe()` / `getTickCounter()` are referenced consistently across Tasks 1–5. The `context` field on the controller is set at startup, reused during `tick`, and cleared on `FINISHED` / structure break, matching the spec's lifecycle.