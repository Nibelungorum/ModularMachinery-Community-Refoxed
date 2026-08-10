package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.RecipeFailureActions;
import cn.howxu.mmcr.api.recipe.helper.EnergyRecipeIo;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.internal.tile.EnergyHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;

public final class RecipeCraftingContext {

    public static final String FAILURE_MISSING_INPUT = "gui.mmcr.controller.failure.missing_input";
    public static final String FAILURE_MISSING_OUTPUT = "gui.mmcr.controller.failure.missing_output";
    public static final String FAILURE_MISSING_ENERGY = "gui.mmcr.controller.failure.missing_energy";
    public static final String FAILURE_SEARCH_EXCEPTION = "gui.mmcr.controller.failure.recipe_search_exception";

    private MachineControllerBlockEntity controller;
    private long structureVersion;
    private long modifierSnapshotVersion;

    private List<ItemInputRoute> itemInputRoutes = List.of();
    private List<ItemOutputRoute> itemOutputRoutes = List.of();
    private List<FluidInputRoute> fluidInputRoutes = List.of();
    private List<FluidOutputRoute> fluidOutputRoutes = List.of();
    private @Nullable List<ItemOutputState> simulatedItemOutputStates;
    private @Nullable List<FluidOutputState> simulatedFluidOutputStates;
    private @Nullable String lastFailureUnloc;
    private @Nullable RequirementFailure lastRequirementFailure;
    private @Nullable Identifier poolRecipeId;
    private List<RecipeModifier> structureModifiers = List.of();

    public RecipeCraftingContext(MachineControllerBlockEntity controller) {
        this.controller = controller;
        this.structureVersion = controller.getStructureVersion();
        this.modifierSnapshotVersion = controller.getModifierSnapshotVersion();
    }

    public void serialize(ValueOutput output) {
        output.putInt("context_version", 1);
        if (lastFailureUnloc != null) output.putString("last_failure_unloc", lastFailureUnloc);
        ValueOutput.TypedOutputList<RecipeModifier> modifiers = output.list("structure_modifiers", RecipeModifier.CODEC);
        for (RecipeModifier modifier : structureModifiers) modifiers.add(modifier);
        serializeItemInputRoutes(output);
        serializeItemOutputRoutes(output);
        serializeFluidInputRoutes(output);
        serializeFluidOutputRoutes(output);
    }

    public static RecipeCraftingContext from(MachineControllerBlockEntity controller, ValueInput input) {
        RecipeCraftingContext context = new RecipeCraftingContext(controller);
        String failure = input.getStringOr("last_failure_unloc", "");
        context.lastFailureUnloc = failure.isEmpty() ? null : failure;
        List<RecipeModifier> modifiers = new ArrayList<>();
        input.listOrEmpty("structure_modifiers", RecipeModifier.CODEC).forEach(modifiers::add);
        context.setStructureModifiers(modifiers);
        context.itemInputRoutes = context.readItemInputRoutes(input);
        context.itemOutputRoutes = context.readItemOutputRoutes(input);
        context.fluidInputRoutes = context.readFluidInputRoutes(input);
        context.fluidOutputRoutes = context.readFluidOutputRoutes(input);
        return context;
    }

    private void serializeItemInputRoutes(ValueOutput output) {
        output.putInt("item_input_route_count", itemInputRoutes.size());
        for (int i = 0; i < itemInputRoutes.size(); i++) {
            ItemInputRoute route = itemInputRoutes.get(i);
            if (route == null) continue;
            ValueOutput routeOutput = output.child("item_input_route_" + i);
            routeOutput.putBoolean("present", true);
            routeOutput.putInt("transfer_count", route.transfers().size());
            for (int j = 0; j < route.transfers().size(); j++) {
                ItemInputTransfer transfer = route.transfers().get(j);
                ValueOutput transferOutput = routeOutput.child("transfer_" + j);
                if (transfer.pos() == null) continue;
                transferOutput.putLong("pos", transfer.pos().asLong());
                transferOutput.putInt("slot", transfer.slot());
                transferOutput.putInt("amount", transfer.amount());
            }
        }
    }

    private void serializeItemOutputRoutes(ValueOutput output) {
        output.putInt("item_output_route_count", itemOutputRoutes.size());
        for (int i = 0; i < itemOutputRoutes.size(); i++) {
            ItemOutputRoute route = itemOutputRoutes.get(i);
            if (route == null) continue;
            ValueOutput routeOutput = output.child("item_output_route_" + i);
            routeOutput.putBoolean("present", true);
            routeOutput.putInt("transfer_count", route.transfers().size());
            for (int j = 0; j < route.transfers().size(); j++) {
                ItemOutputTransfer transfer = route.transfers().get(j);
                ValueOutput transferOutput = routeOutput.child("transfer_" + j);
                if (transfer.pos() == null) continue;
                transferOutput.putLong("pos", transfer.pos().asLong());
                transferOutput.putInt("slot", transfer.slot());
                transferOutput.store("stack", ItemStack.CODEC, transfer.stack());
            }
        }
    }

    private void serializeFluidInputRoutes(ValueOutput output) {
        output.putInt("fluid_input_route_count", fluidInputRoutes.size());
        for (int i = 0; i < fluidInputRoutes.size(); i++) {
            FluidInputRoute route = fluidInputRoutes.get(i);
            if (route == null) continue;
            ValueOutput routeOutput = output.child("fluid_input_route_" + i);
            routeOutput.putBoolean("present", true);
            routeOutput.putInt("transfer_count", route.transfers().size());
            for (int j = 0; j < route.transfers().size(); j++) {
                FluidInputTransfer transfer = route.transfers().get(j);
                ValueOutput transferOutput = routeOutput.child("transfer_" + j);
                if (transfer.pos() == null) continue;
                transferOutput.putLong("pos", transfer.pos().asLong());
                transferOutput.putInt("tank", transfer.tank());
                transferOutput.store("stack", FluidStack.CODEC, transfer.stack());
            }
        }
    }

    private void serializeFluidOutputRoutes(ValueOutput output) {
        output.putInt("fluid_output_route_count", fluidOutputRoutes.size());
        for (int i = 0; i < fluidOutputRoutes.size(); i++) {
            FluidOutputRoute route = fluidOutputRoutes.get(i);
            if (route == null) continue;
            ValueOutput routeOutput = output.child("fluid_output_route_" + i);
            routeOutput.putBoolean("present", true);
            routeOutput.putInt("transfer_count", route.transfers().size());
            for (int j = 0; j < route.transfers().size(); j++) {
                FluidOutputTransfer transfer = route.transfers().get(j);
                ValueOutput transferOutput = routeOutput.child("transfer_" + j);
                if (transfer.pos() == null) continue;
                transferOutput.putLong("pos", transfer.pos().asLong());
                transferOutput.putInt("tank", transfer.tank());
                transferOutput.store("stack", FluidStack.CODEC, transfer.stack());
            }
        }
    }

    private List<ItemInputRoute> readItemInputRoutes(ValueInput input) {
        List<ItemInputRoute> routes = emptyItemInputRoutes(Math.max(0, input.getIntOr("item_input_route_count", 0)));
        for (int i = 0; i < routes.size(); i++) {
            ValueInput routeInput = input.childOrEmpty("item_input_route_" + i);
            if (!routeInput.getBooleanOr("present", false)) continue;
            List<ItemInputTransfer> transfers = new ArrayList<>();
            boolean valid = true;
            for (int j = 0; j < Math.max(0, routeInput.getIntOr("transfer_count", 0)); j++) {
                ValueInput transferInput = routeInput.childOrEmpty("transfer_" + j);
                ItemBusBlockEntity bus = itemBusAt(net.minecraft.core.BlockPos.of(transferInput.getLongOr("pos", Long.MIN_VALUE)), IOType.INPUT);
                int slot = transferInput.getIntOr("slot", -1);
                if (bus == null || slot < 0 || slot >= bus.getItemHandler(null).getSlots()) {
                    valid = false;
                    break;
                }
                transfers.add(new ItemInputTransfer(bus.getItemHandler(null), bus.getBlockPos(), slot,
                        null,
                        transferInput.getIntOr("amount", 0)));
            }
            if (valid) routes.set(i, new ItemInputRoute(List.copyOf(transfers)));
        }
        return routes;
    }

    private List<ItemOutputRoute> readItemOutputRoutes(ValueInput input) {
        List<ItemOutputRoute> routes = emptyItemOutputRoutes(Math.max(0, input.getIntOr("item_output_route_count", 0)));
        for (int i = 0; i < routes.size(); i++) {
            ValueInput routeInput = input.childOrEmpty("item_output_route_" + i);
            if (!routeInput.getBooleanOr("present", false)) continue;
            List<ItemOutputTransfer> transfers = new ArrayList<>();
            boolean valid = true;
            for (int j = 0; j < Math.max(0, routeInput.getIntOr("transfer_count", 0)); j++) {
                ValueInput transferInput = routeInput.childOrEmpty("transfer_" + j);
                ItemBusBlockEntity bus = itemBusAt(net.minecraft.core.BlockPos.of(transferInput.getLongOr("pos", Long.MIN_VALUE)), IOType.OUTPUT);
                ItemStack stack = transferInput.read("stack", ItemStack.CODEC).orElse(ItemStack.EMPTY);
                int slot = transferInput.getIntOr("slot", -1);
                if (bus == null || stack.isEmpty() || slot < 0 || slot >= bus.getItemHandler(null).getSlots()) {
                    valid = false;
                    break;
                }
                transfers.add(new ItemOutputTransfer(bus.getItemHandler(null), bus.getBlockPos(), slot, stack));
            }
            if (valid) routes.set(i, new ItemOutputRoute(List.copyOf(transfers)));
        }
        return routes;
    }

    private List<FluidInputRoute> readFluidInputRoutes(ValueInput input) {
        List<FluidInputRoute> routes = emptyFluidInputRoutes(Math.max(0, input.getIntOr("fluid_input_route_count", 0)));
        for (int i = 0; i < routes.size(); i++) {
            ValueInput routeInput = input.childOrEmpty("fluid_input_route_" + i);
            if (!routeInput.getBooleanOr("present", false)) continue;
            List<FluidInputTransfer> transfers = new ArrayList<>();
            boolean valid = true;
            for (int j = 0; j < Math.max(0, routeInput.getIntOr("transfer_count", 0)); j++) {
                ValueInput transferInput = routeInput.childOrEmpty("transfer_" + j);
                FluidHatchBlockEntity hatch = fluidHatchAt(net.minecraft.core.BlockPos.of(transferInput.getLongOr("pos", Long.MIN_VALUE)), IOType.INPUT);
                FluidStack stack = transferInput.read("stack", FluidStack.CODEC).orElse(FluidStack.EMPTY);
                int tank = transferInput.getIntOr("tank", -1);
                if (hatch == null || stack.isEmpty() || tank < 0 || tank >= hatch.getFluidHandler(null).getTanks()) {
                    valid = false;
                    break;
                }
                transfers.add(new FluidInputTransfer(hatch.getFluidHandler(null), hatch.getBlockPos(), tank, stack));
            }
            if (valid) routes.set(i, new FluidInputRoute(List.copyOf(transfers)));
        }
        return routes;
    }

    private List<FluidOutputRoute> readFluidOutputRoutes(ValueInput input) {
        List<FluidOutputRoute> routes = emptyFluidOutputRoutes(Math.max(0, input.getIntOr("fluid_output_route_count", 0)));
        for (int i = 0; i < routes.size(); i++) {
            ValueInput routeInput = input.childOrEmpty("fluid_output_route_" + i);
            if (!routeInput.getBooleanOr("present", false)) continue;
            List<FluidOutputTransfer> transfers = new ArrayList<>();
            boolean valid = true;
            for (int j = 0; j < Math.max(0, routeInput.getIntOr("transfer_count", 0)); j++) {
                ValueInput transferInput = routeInput.childOrEmpty("transfer_" + j);
                FluidHatchBlockEntity hatch = fluidHatchAt(net.minecraft.core.BlockPos.of(transferInput.getLongOr("pos", Long.MIN_VALUE)), IOType.OUTPUT);
                FluidStack stack = transferInput.read("stack", FluidStack.CODEC).orElse(FluidStack.EMPTY);
                int tank = transferInput.getIntOr("tank", -1);
                if (hatch == null || stack.isEmpty() || tank < 0 || tank >= hatch.getFluidHandler(null).getTanks()) {
                    valid = false;
                    break;
                }
                transfers.add(new FluidOutputTransfer(hatch.getFluidHandler(null), hatch.getBlockPos(), tank, stack));
            }
            if (valid) routes.set(i, new FluidOutputRoute(List.copyOf(transfers)));
        }
        return routes;
    }

    public boolean isStructureVersionCurrent() {
        return structureVersion == controller.getStructureVersion()
                && modifierSnapshotVersion == controller.getModifierSnapshotVersion();
    }

    public boolean isStructureVersionOnlyCurrent() {
        return structureVersion == controller.getStructureVersion();
    }

    public void refreshModifierSnapshot(List<RecipeModifier> modifiers) {
        this.modifierSnapshotVersion = controller.getModifierSnapshotVersion();
        setStructureModifiers(modifiers);
    }

    void resetFor(MachineControllerBlockEntity controller) {
        this.controller = controller;
        this.structureVersion = controller.getStructureVersion();
        this.modifierSnapshotVersion = controller.getModifierSnapshotVersion();
        resetTransientState();
    }

    void resetTransientState() {
        itemInputRoutes = List.of();
        itemOutputRoutes = List.of();
        fluidInputRoutes = List.of();
        fluidOutputRoutes = List.of();
        lastFailureUnloc = null;
        lastRequirementFailure = null;
        structureModifiers = List.of();
    }

    public void setStructureModifiers(List<RecipeModifier> modifiers) {
        structureModifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
    }

    public List<RecipeModifier> structureModifiers() {
        return structureModifiers;
    }

    public List<RecipeModifier> effectiveModifiers(MachineRecipe recipe) {
        ArrayList<RecipeModifier> result = new ArrayList<>(recipe.modifiers().size() + structureModifiers.size());
        result.addAll(recipe.modifiers());
        result.addAll(structureModifiers);
        return List.copyOf(result);
    }

    void setPoolRecipeId(Identifier recipeId) {
        this.poolRecipeId = recipeId;
    }

    @Nullable Identifier poolRecipeId() {
        return poolRecipeId;
    }

    public RecipeFailureActions failureAction() {
        Machine m = controller.getMachine();
        return m == null ? RecipeFailureActions.getDefaultAction() : m.failureAction();
    }

    public @Nullable String getLastFailureUnloc() {
        return lastFailureUnloc;
    }

    public @Nullable RequirementFailure getLastRequirementFailure() {
        return lastRequirementFailure;
    }

    private void setFailure(String key) {
        setFailure(key, null);
    }

    private void setFailure(String key, @Nullable RequirementFailure failure) {
        this.lastFailureUnloc = key;
        this.lastRequirementFailure = failure;
    }

    public void setRequirementFailure(String key, @Nullable RequirementFailure failure) {
        setFailure(key, failure);
    }

    private static String componentTrace(BlockEntity entity) {
        return entity.getClass().getSimpleName() + "@" + entity.getBlockPos().toShortString();
    }

    private static List<String> componentTraces(List<? extends BlockEntity> entities) {
        List<String> traces = new ArrayList<>(entities.size());
        for (BlockEntity entity : entities) traces.add(componentTrace(entity));
        return traces;
    }

    public boolean ioTick(MachineRecipe recipe) {
        return ioTick(recipe, 1);
    }

    public boolean ioTick(MachineRecipe recipe, int parallelism) {
        return ioTick(scaledRequirements(recipe, parallelism));
    }

    private boolean ioTick(List<MachineRequirement> requirements) {
        lastFailureUnloc = null;
        lastRequirementFailure = null;
        for (int requirementIndex = 0; requirementIndex < requirements.size(); requirementIndex++) {
            if (!requirements.get(requirementIndex).ioTick(this, requirementIndex)) return false;
        }
        return true;
    }

    public boolean simulateInputs(MachineRecipe recipe) {
        return simulateInputs(recipe, 1);
    }

    public boolean simulateInputs(MachineRecipe recipe, int parallelism) {
        return simulateInputs(scaledRequirements(recipe, parallelism));
    }

    private boolean simulateInputs(List<MachineRequirement> requirements) {
        lastFailureUnloc = null;
        lastRequirementFailure = null;
        itemInputRoutes = emptyItemInputRoutes(requirements.size());
        fluidInputRoutes = emptyFluidInputRoutes(requirements.size());

        for (int requirementIndex = 0; requirementIndex < requirements.size(); requirementIndex++) {
            MachineRequirement requirement = requirements.get(requirementIndex);
            if (requirement.io() == RecipeModifier.IOType.INPUT && !requirement.simulate(this, requirementIndex)) return false;
        }
        return true;
    }

    public boolean simulateOutputs(MachineRecipe recipe) {
        return simulateOutputs(recipe, 1);
    }

    public boolean simulateOutputs(MachineRecipe recipe, int parallelism) {
        return simulateOutputs(scaledRequirements(recipe, parallelism));
    }

    public int maxInputParallelism(MachineRecipe recipe, int limit) {
        int max = Math.max(1, limit);
        int best = max;
        for (MachineRequirement requirement : recipe.runtimeRequirements(structureModifiers)) {
            if (requirement.io() != RecipeModifier.IOType.INPUT) continue;
            int requirementMax = requirement.maxInputParallelism(this, max);
            if (requirementMax < 0) return -1;
            best = Math.min(best, requirementMax);
        }
        return best;
    }

    public int countMatchingItemInputs(net.minecraft.world.item.crafting.Ingredient ingredient, List<String> requiredTags) {
        if (ingredient == null) return 0;
        int count = 0;
        for (ItemInputState state : itemInputStates(requiredTags)) {
            if (ingredient.test(state.stack())) count += state.stack().getCount();
        }
        return count;
    }

    private boolean simulateOutputs(List<MachineRequirement> requirements) {
        lastFailureUnloc = null;
        lastRequirementFailure = null;
        itemOutputRoutes = emptyItemOutputRoutes(requirements.size());
        fluidOutputRoutes = emptyFluidOutputRoutes(requirements.size());
        simulatedItemOutputStates = allItemOutputStates();
        simulatedFluidOutputStates = allFluidOutputStates();

        try {
            for (int requirementIndex = 0; requirementIndex < requirements.size(); requirementIndex++) {
                MachineRequirement requirement = requirements.get(requirementIndex);
                if (requirement instanceof ItemRequirement item && item.io() == RecipeModifier.IOType.OUTPUT && item.chance() <= 0F) continue;
                if (requirement instanceof FluidRequirement fluid && fluid.io() == RecipeModifier.IOType.OUTPUT && fluid.chance() <= 0F) continue;
                if (requirement.io() == RecipeModifier.IOType.OUTPUT && !requirement.simulate(this, requirementIndex)) return false;
            }
            return true;
        } finally {
            simulatedItemOutputStates = null;
            simulatedFluidOutputStates = null;
        }
    }

    private List<MachineRequirement> scaledRequirements(MachineRecipe recipe, int parallelism) {
        List<MachineRequirement> requirements = recipe.runtimeRequirements(structureModifiers);
        if (parallelism <= 1) return requirements;
        try {
            List<MachineRequirement> scaled = new ArrayList<>(requirements.size());
            for (MachineRequirement requirement : requirements) {
                if (requirement instanceof ItemRequirement item) {
                    if (item.io() == RecipeModifier.IOType.INPUT) {
                        scaled.add(new ItemRequirement(item.io(), item.item(), Math.multiplyExact(item.count(), parallelism), item.stack(), item.chance(), item.tags()));
                    } else {
                        ItemStack stack = item.stack().copy();
                        stack.setCount(Math.multiplyExact(stack.getCount(), parallelism));
                        scaled.add(new ItemRequirement(item.io(), item.item(), item.count(), stack, item.chance(), item.tags()));
                    }
                } else if (requirement instanceof FluidRequirement fluid) {
                    if (fluid.io() == RecipeModifier.IOType.INPUT) {
                        scaled.add(new FluidRequirement(fluid.io(), fluid.fluid(), Math.multiplyExact(fluid.amount(), parallelism), fluid.stack(), fluid.chance(), fluid.tags()));
                    } else {
                        FluidStack stack = fluid.stack().copy();
                        stack.setAmount(Math.multiplyExact(stack.getAmount(), parallelism));
                        scaled.add(new FluidRequirement(fluid.io(), fluid.fluid(), fluid.amount(), stack, fluid.chance(), fluid.tags()));
                    }
                } else if (requirement instanceof EnergyRequirement energy) {
                    scaled.add(new EnergyRequirement(energy.io(), Math.multiplyExact(energy.fePerTick(), parallelism), energy.tags()));
                } else {
                    scaled.add(requirement);
                }
            }
            return List.copyOf(scaled);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Parallel requirement overflow: " + recipe.id(), e);
        }
    }

    public boolean simulateItemInput(int requirementIndex, ItemRequirement item) {
        List<ItemInputState> itemStates = itemInputStates(item.tags());
        List<ItemBusBlockEntity> taggedOut = excludedLiveComponents(ItemBusBlockEntity.class, IOType.INPUT, item.tags());
        List<ItemBusBlockEntity> itemHatches = liveComponents(ItemBusBlockEntity.class, IOType.INPUT, item.tags());
        List<ItemInputTransfer> transfers = new ArrayList<>();
        MachineIngredient.ItemIngredient ingredient = new MachineIngredient.ItemIngredient(item.item(), item.count());
        int remaining = item.count();
        List<ItemBusBlockEntity> matched = new ArrayList<>();
        for (ItemInputState state : itemStates) {
            int before = transfers.size();
            remaining = state.extract(ingredient, remaining, transfers);
            if (transfers.size() > before && state.bus() != null) matched.add(state.bus());
            if (remaining <= 0) break;
        }
        if (remaining > 0) {
            RequirementFailure failure = buildMissingInputFailure(requirementIndex, item.count(), remaining, itemHatches, matched, taggedOut);
            setFailure(FAILURE_MISSING_INPUT, failure);
            return false;
        }
        itemInputRoutes.set(requirementIndex, new ItemInputRoute(transfers));
        return true;
    }

    public boolean simulateFluidInput(int requirementIndex, FluidRequirement fluid) {
        List<FluidInputState> fluidStates = fluidInputStates(fluid.tags());
        List<FluidHatchBlockEntity> taggedOut = excludedLiveComponents(FluidHatchBlockEntity.class, IOType.INPUT, fluid.tags());
        List<FluidHatchBlockEntity> fluidHatches = liveComponents(FluidHatchBlockEntity.class, IOType.INPUT, fluid.tags());
        List<FluidInputTransfer> transfers = new ArrayList<>();
        MachineIngredient.FluidIngredient ingredient = new MachineIngredient.FluidIngredient(fluid.fluid(), fluid.amount());
        int remaining = fluid.amount();
        List<FluidHatchBlockEntity> matched = new ArrayList<>();
        for (FluidInputState state : fluidStates) {
            int before = transfers.size();
            remaining = state.drain(ingredient, remaining, transfers);
            if (transfers.size() > before && state.hatch() != null) matched.add(state.hatch());
            if (remaining <= 0) break;
        }
        if (remaining > 0) {
            RequirementFailure failure = buildMissingInputFailure(requirementIndex, fluid.amount(), remaining, fluidHatches, matched, taggedOut);
            setFailure(FAILURE_MISSING_INPUT, failure);
            return false;
        }
        fluidInputRoutes.set(requirementIndex, new FluidInputRoute(transfers));
        return true;
    }

    public boolean simulateItemOutput(int requirementIndex, ItemRequirement item) {
        List<ItemOutputState> itemStates = itemOutputStates(item.tags());
        List<ItemBusBlockEntity> taggedOut = excludedLiveComponents(ItemBusBlockEntity.class, IOType.OUTPUT, item.tags());
        List<ItemBusBlockEntity> itemHatches = liveComponents(ItemBusBlockEntity.class, IOType.OUTPUT, item.tags());
        List<ItemOutputTransfer> transfers = new ArrayList<>();
        ItemStack output = item.stack();
        ItemStack remaining = normalizeRecipeOutput(output);
        List<ItemBusBlockEntity> matched = new ArrayList<>();
        for (ItemOutputState state : itemStates) {
            int before = transfers.size();
            remaining = state.insertIntoMatchingStack(remaining, transfers);
            if (transfers.size() > before && state.bus() != null) matched.add(state.bus());
            if (remaining.isEmpty()) break;
        }
        for (ItemOutputState state : itemStates) {
            int before = transfers.size();
            remaining = state.insert(remaining, transfers);
            if (transfers.size() > before && state.bus() != null) matched.add(state.bus());
            if (remaining.isEmpty()) break;
        }
        if (!remaining.isEmpty()) {
            RequirementFailure failure = buildMissingOutputFailure(requirementIndex, output.getCount(), remaining.getCount(), itemHatches, matched, taggedOut);
            setFailure(FAILURE_MISSING_OUTPUT, failure);
            return false;
        }
        itemOutputRoutes.set(requirementIndex, new ItemOutputRoute(transfers));
        return true;
    }

    public boolean simulateFluidOutput(int requirementIndex, FluidRequirement fluid) {
        List<FluidOutputState> fluidStates = fluidOutputStates(fluid.tags());
        List<FluidHatchBlockEntity> taggedOut = excludedLiveComponents(FluidHatchBlockEntity.class, IOType.OUTPUT, fluid.tags());
        List<FluidHatchBlockEntity> fluidHatches = liveComponents(FluidHatchBlockEntity.class, IOType.OUTPUT, fluid.tags());
        List<FluidOutputTransfer> transfers = new ArrayList<>();
        FluidStack output = fluid.stack();
        int remaining = output.getAmount();
        List<FluidHatchBlockEntity> matched = new ArrayList<>();
        for (FluidOutputState state : fluidStates) {
            int before = transfers.size();
            remaining = state.fill(output, remaining, transfers);
            if (transfers.size() > before && state.hatch() != null) matched.add(state.hatch());
            if (remaining <= 0) break;
        }
        if (remaining > 0) {
            RequirementFailure failure = buildMissingOutputFailure(requirementIndex, output.getAmount(), remaining, fluidHatches, matched, taggedOut);
            setFailure(FAILURE_MISSING_OUTPUT, failure);
            return false;
        }
        fluidOutputRoutes.set(requirementIndex, new FluidOutputRoute(transfers));
        return true;
    }

    private RequirementFailure buildMissingInputFailure(int requirementIndex, int required, int remaining, List<? extends BlockEntity> searched, List<? extends BlockEntity> matched) {
        return buildMissingInputFailure(requirementIndex, required, remaining, searched, matched, List.of());
    }

    private RequirementFailure buildMissingInputFailure(int requirementIndex, int required, int remaining, List<? extends BlockEntity> searched, List<? extends BlockEntity> matched, List<? extends BlockEntity> tagExcluded) {
        RequirementFailure.Kind kind = searched.isEmpty() && !tagExcluded.isEmpty()
                ? RequirementFailure.Kind.TAG_MISMATCH
                : RequirementFailure.Kind.MISSING_INPUT;
        List<BlockEntity> traces = new ArrayList<>(searched);
        if (searched.isEmpty()) traces.addAll(tagExcluded);
        return new RequirementFailure(
                requirementIndex,
                kind,
                required,
                required - remaining,
                remaining,
                componentTraces(traces),
                componentTraces(new ArrayList<>(matched))
        );
    }

    private RequirementFailure buildMissingOutputFailure(int requirementIndex, int required, int remaining, List<? extends BlockEntity> searched, List<? extends BlockEntity> matched) {
        return buildMissingOutputFailure(requirementIndex, required, remaining, searched, matched, List.of());
    }

    private RequirementFailure buildMissingOutputFailure(int requirementIndex, int required, int remaining, List<? extends BlockEntity> searched, List<? extends BlockEntity> matched, List<? extends BlockEntity> tagExcluded) {
        RequirementFailure.Kind kind = searched.isEmpty() && !tagExcluded.isEmpty()
                ? RequirementFailure.Kind.TAG_MISMATCH
                : RequirementFailure.Kind.MISSING_OUTPUT;
        List<BlockEntity> traces = new ArrayList<>(searched);
        if (searched.isEmpty()) traces.addAll(tagExcluded);
        return new RequirementFailure(
                requirementIndex,
                kind,
                required,
                required - remaining,
                remaining,
                componentTraces(traces),
                componentTraces(new ArrayList<>(matched))
        );
    }

    public boolean startCrafting(MachineRecipe recipe) {
        return startCrafting(recipe, 1);
    }

    public boolean startCrafting(MachineRecipe recipe, int parallelism) {
        List<MachineRequirement> requirements = scaledRequirements(recipe, parallelism);
        if (!simulateInputs(requirements)) return false;
        if (!simulateOutputs(requirements)) return false;
        return commitInputs(requirements);
    }

    public boolean canStartCrafting(ActiveMachineRecipe activeRecipe) {
        if (activeRecipe == null || activeRecipe.getRecipe() == null) return false;
        int parallelism = ParallelRecipeCalculator.maxStartableParallelism(
                this, activeRecipe.getRecipe(), activeRecipe.getMaxParallelism());
        if (parallelism <= 0) return false;
        activeRecipe.setParallelism(parallelism);
        return true;
    }

    public boolean canRestartCrafting(ActiveMachineRecipe activeRecipe) {
        if (activeRecipe == null || activeRecipe.getRecipe() == null) return false;
        int current = activeRecipe.getParallelism();
        int max = activeRecipe.getMaxParallelism();
        if (current > max) {
            activeRecipe.setParallelism(max);
            if (simulateInputs(activeRecipe.getRecipe(), max)
                    && simulateOutputs(activeRecipe.getRecipe(), max)) {
                return true;
            }
            activeRecipe.setParallelism(1);
        }
        return canStartCrafting(activeRecipe);
    }

    public boolean finishCrafting(MachineRecipe recipe) {
        return finishCrafting(recipe, 1);
    }

    public boolean finishCrafting(MachineRecipe recipe, int parallelism) {
        return commitOutputs(scaledRequirements(recipe, parallelism));
    }

    public boolean commitInputs(MachineRecipe recipe) {
        return commitInputs(recipe.runtimeRequirements(structureModifiers));
    }

    private boolean commitInputs(List<MachineRequirement> requirements) {
        RequirementFailure itemFailure = firstItemInputFailure(requirements);
        if (itemFailure != null) {
            setFailure(FAILURE_MISSING_INPUT, itemFailure);
            return false;
        }
        RequirementFailure fluidFailure = firstFluidInputFailure(requirements);
        if (fluidFailure != null) {
            setFailure(FAILURE_MISSING_INPUT, fluidFailure);
            return false;
        }
        List<ItemInputTransfer> itemTransfers = new ArrayList<>();
        List<FluidInputTransfer> fluidTransfers = new ArrayList<>();
        for (int requirementIndex = 0; requirementIndex < requirements.size(); requirementIndex++) {
            MachineRequirement requirement = requirements.get(requirementIndex);
            if (requirement.io() == RecipeModifier.IOType.INPUT && !requirement.commit(this, requirementIndex)) return false;
        }
        for (int requirementIndex = 0; requirementIndex < requirements.size(); requirementIndex++) {
            MachineRequirement requirement = requirements.get(requirementIndex);
            if (requirement instanceof ItemRequirement item && item.io() == RecipeModifier.IOType.INPUT) {
                ItemInputRoute route = itemInputRoutes.get(requirementIndex);
                itemTransfers.addAll(route.transfers());
            } else if (requirement instanceof FluidRequirement fluid && fluid.io() == RecipeModifier.IOType.INPUT) {
                FluidInputRoute route = fluidInputRoutes.get(requirementIndex);
                fluidTransfers.addAll(route.transfers());
            }
        }
        extract(itemTransfers);
        drain(fluidTransfers);
        return true;
    }

    public boolean commitOutputs(MachineRecipe recipe) {
        return commitOutputs(recipe.runtimeRequirements(structureModifiers));
    }

    private boolean commitOutputs(List<MachineRequirement> requirements) {
        List<ItemOutputTransfer> itemTransfers = new ArrayList<>();
        List<FluidOutputTransfer> fluidTransfers = new ArrayList<>();
        RequirementFailure itemFailure = firstItemOutputFailure(requirements);
        if (itemFailure != null) {
            setFailure(FAILURE_MISSING_OUTPUT, itemFailure);
            return false;
        }
        RequirementFailure fluidFailure = firstFluidOutputFailure(requirements);
        if (fluidFailure != null) {
            setFailure(FAILURE_MISSING_OUTPUT, fluidFailure);
            return false;
        }
        for (int requirementIndex = 0; requirementIndex < requirements.size(); requirementIndex++) {
            MachineRequirement requirement = requirements.get(requirementIndex);
            if (requirement instanceof ItemRequirement item && item.io() == RecipeModifier.IOType.OUTPUT && item.chance() <= 0F) continue;
            if (requirement instanceof FluidRequirement fluid && fluid.io() == RecipeModifier.IOType.OUTPUT && fluid.chance() <= 0F) continue;
            if (requirement.io() == RecipeModifier.IOType.OUTPUT && !requirement.commit(this, requirementIndex)) return false;
        }
        for (int requirementIndex = 0; requirementIndex < requirements.size(); requirementIndex++) {
            MachineRequirement requirement = requirements.get(requirementIndex);
            if (requirement instanceof ItemRequirement item && item.io() == RecipeModifier.IOType.OUTPUT) {
                if (!shouldProduce(item.chance())) continue;
                ItemOutputRoute route = itemOutputRoutes.get(requirementIndex);
                itemTransfers.addAll(route.transfers());
            } else if (requirement instanceof FluidRequirement fluid && fluid.io() == RecipeModifier.IOType.OUTPUT) {
                if (!shouldProduce(fluid.chance())) continue;
                FluidOutputRoute route = fluidOutputRoutes.get(requirementIndex);
                fluidTransfers.addAll(route.transfers());
            }
        }
        insert(itemTransfers);
        fill(fluidTransfers);
        return true;
    }

    private boolean shouldProduce(float chance) {
        if (chance >= 1F) return true;
        if (chance <= 0F) return false;
        var level = controller.getLevel();
        return (level == null ? Math.random() : level.getRandom().nextFloat()) < chance;
    }

    public List<FluidHatchBlockEntity> fluidOutputs() {
        return liveComponents(FluidHatchBlockEntity.class, IOType.OUTPUT, List.of());
    }

    private List<EnergyHatchBlockEntity> liveEnergyInputs() {
        return liveComponents(EnergyHatchBlockEntity.class, IOType.INPUT, List.of());
    }

    private List<EnergyHatchBlockEntity> liveEnergyOutputs() {
        return liveComponents(EnergyHatchBlockEntity.class, IOType.OUTPUT, List.of());
    }

    private static List<IEnergyStorage> energyStorages(List<EnergyHatchBlockEntity> hatches) {
        return hatches.stream()
                .map(hatch -> hatch.getEnergyStorage(null))
                .toList();
    }

    private static int availableEnergy(List<EnergyHatchBlockEntity> hatches) {
        long available = 0;
        for (EnergyHatchBlockEntity hatch : hatches) {
            available += hatch.getEnergyStorage(null).getEnergyStored();
            if (available >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        }
        return (int) available;
    }

    public List<IEnergyStorage> energyStorages() {
        return energyStorages(liveEnergyInputs());
    }

    public List<IEnergyStorage> taggedEnergyStorages(List<String> requiredTags) {
        return energyStorages(liveComponents(EnergyHatchBlockEntity.class, IOType.INPUT, requiredTags));
    }

    public List<IEnergyStorage> taggedEnergyOutputs(List<String> requiredTags) {
        return energyStorages(liveComponents(EnergyHatchBlockEntity.class, IOType.OUTPUT, requiredTags));
    }

    public int taggedAvailableEnergy(List<String> requiredTags) {
        return availableEnergy(liveComponents(EnergyHatchBlockEntity.class, IOType.INPUT, requiredTags));
    }

    public int taggedAvailableOutputEnergy(List<String> requiredTags) {
        long available = 0;
        for (EnergyHatchBlockEntity hatch : liveComponents(EnergyHatchBlockEntity.class, IOType.OUTPUT, requiredTags)) {
            IEnergyStorage storage = hatch.getEnergyStorage(null);
            available += (long) storage.getMaxEnergyStored() - storage.getEnergyStored();
            if (available >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        }
        return (int) available;
    }

    public List<String> energyComponentTraces(List<String> requiredTags) {
        List<EnergyHatchBlockEntity> matches = liveComponents(EnergyHatchBlockEntity.class, IOType.INPUT, requiredTags);
        List<EnergyHatchBlockEntity> tagExcluded = excludedLiveComponents(EnergyHatchBlockEntity.class, IOType.INPUT, requiredTags);
        List<BlockEntity> traces = new ArrayList<>(matches);
        traces.addAll(tagExcluded);
        return componentTraces(traces);
    }

    public List<String> energyOutputComponentTraces(List<String> requiredTags) {
        List<EnergyHatchBlockEntity> matches = liveComponents(EnergyHatchBlockEntity.class, IOType.OUTPUT, requiredTags);
        List<EnergyHatchBlockEntity> tagExcluded = excludedLiveComponents(EnergyHatchBlockEntity.class, IOType.OUTPUT, requiredTags);
        List<BlockEntity> traces = new ArrayList<>(matches);
        traces.addAll(tagExcluded);
        return componentTraces(traces);
    }

    public String energyComponentSummary() {
        return liveEnergyInputs().stream()
                .map(hatch -> hatch.getBlockPos() + ":energy_input_hatch=" + hatch.getEnergyStorage(null).getEnergyStored())
                .toList()
                .toString();
    }

    public String energyOutputComponentSummary() {
        return liveEnergyOutputs().stream()
                .map(hatch -> hatch.getBlockPos()
                        + ":energy_output_hatch=stored/max="
                        + hatch.getEnergyStorage(null).getEnergyStored()
                        + "/"
                        + hatch.getEnergyStorage(null).getMaxEnergyStored())
                .toList()
                .toString();
    }

    public boolean simulateEnergyInput(int requirementIndex, EnergyRequirement energy) {
        if (EnergyRecipeIo.canConsumeInputs(taggedEnergyStorages(energy.tags()), energy.fePerTick(), 1)) return true;
        long available = taggedAvailableEnergy(energy.tags());
        setRequirementFailure(FAILURE_MISSING_ENERGY, new RequirementFailure(
                requirementIndex,
                RequirementFailure.Kind.MISSING_ENERGY,
                energy.fePerTick(),
                available,
                Math.max(0, (long) energy.fePerTick() - available),
                energyComponentTraces(energy.tags()),
                List.of()
        ));
        return false;
    }

    public boolean simulateEnergyOutput(int requirementIndex, EnergyRequirement energy) {
        if (EnergyRecipeIo.canProduceOutputs(taggedEnergyOutputs(energy.tags()), energy.fePerTick(), 1)) return true;
        long available = taggedAvailableOutputEnergy(energy.tags());
        setRequirementFailure(FAILURE_MISSING_OUTPUT, new RequirementFailure(
                requirementIndex,
                RequirementFailure.Kind.MISSING_OUTPUT,
                energy.fePerTick(),
                available,
                Math.max(0, (long) energy.fePerTick() - available),
                energyOutputComponentTraces(energy.tags()),
                List.of()
        ));
        return false;
    }

    public boolean collectEnergyInputRoute(int requirementIndex) {
        return true;
    }

    public boolean collectEnergyOutputRoute(int requirementIndex) {
        return true;
    }

    private static List<ItemInputRoute> emptyItemInputRoutes(int size) {
        List<ItemInputRoute> routes = new ArrayList<>(size);
        for (int i = 0; i < size; i++) routes.add(null);
        return routes;
    }

    private static List<ItemOutputRoute> emptyItemOutputRoutes(int size) {
        List<ItemOutputRoute> routes = new ArrayList<>(size);
        for (int i = 0; i < size; i++) routes.add(null);
        return routes;
    }

    private static List<FluidInputRoute> emptyFluidInputRoutes(int size) {
        List<FluidInputRoute> routes = new ArrayList<>(size);
        for (int i = 0; i < size; i++) routes.add(null);
        return routes;
    }

    private static List<FluidOutputRoute> emptyFluidOutputRoutes(int size) {
        List<FluidOutputRoute> routes = new ArrayList<>(size);
        for (int i = 0; i < size; i++) routes.add(null);
        return routes;
    }

    private @Nullable RequirementFailure firstItemInputFailure(List<MachineRequirement> requirements) {
        List<ItemInputState> states = new ArrayList<>();
        for (int requirementIndex = 0; requirementIndex < requirements.size(); requirementIndex++) {
            MachineRequirement requirement = requirements.get(requirementIndex);
            if (!(requirement instanceof ItemRequirement item) || item.io() != RecipeModifier.IOType.INPUT) continue;
            ItemInputRoute route = requirementIndex < itemInputRoutes.size() ? itemInputRoutes.get(requirementIndex) : null;
            if (route == null) return new RequirementFailure(requirementIndex, RequirementFailure.Kind.MISSING_INPUT, item.count(), 0);
            int available = 0;
            for (ItemInputTransfer transfer : route.transfers()) {
                ItemInputState state = itemInputState(states, transfer);
                int remaining = state.extract(transfer.ingredient(), transfer.amount(), new ArrayList<>());
                available += transfer.amount() - remaining;
                if (remaining > 0) {
                    return new RequirementFailure(requirementIndex, RequirementFailure.Kind.MISSING_INPUT, item.count(), available);
                }
            }
        }
        return null;
    }

    private @Nullable RequirementFailure firstItemOutputFailure(List<MachineRequirement> requirements) {
        List<ItemOutputState> states = new ArrayList<>();
        for (int requirementIndex = 0; requirementIndex < requirements.size(); requirementIndex++) {
            MachineRequirement requirement = requirements.get(requirementIndex);
            if (!(requirement instanceof ItemRequirement item) || item.io() != RecipeModifier.IOType.OUTPUT) continue;
            if (item.chance() <= 0F) continue;
            ItemOutputRoute route = requirementIndex < itemOutputRoutes.size() ? itemOutputRoutes.get(requirementIndex) : null;
            if (route == null) return new RequirementFailure(requirementIndex, RequirementFailure.Kind.MISSING_OUTPUT, item.stack().getCount(), 0);
            int available = 0;
            for (ItemOutputTransfer transfer : route.transfers()) {
                ItemOutputState state = itemOutputState(states, transfer);
                ItemStack remaining = state.insert(transfer.stack(), new ArrayList<>());
                available += transfer.stack().getCount() - remaining.getCount();
                if (!remaining.isEmpty()) {
                    return new RequirementFailure(requirementIndex, RequirementFailure.Kind.MISSING_OUTPUT, item.stack().getCount(), available);
                }
            }
        }
        return null;
    }

    private @Nullable RequirementFailure firstFluidInputFailure(List<MachineRequirement> requirements) {
        List<FluidInputState> states = new ArrayList<>();
        for (int requirementIndex = 0; requirementIndex < requirements.size(); requirementIndex++) {
            MachineRequirement requirement = requirements.get(requirementIndex);
            if (!(requirement instanceof FluidRequirement fluid) || fluid.io() != RecipeModifier.IOType.INPUT) continue;
            FluidInputRoute route = requirementIndex < fluidInputRoutes.size() ? fluidInputRoutes.get(requirementIndex) : null;
            if (route == null) return new RequirementFailure(requirementIndex, RequirementFailure.Kind.MISSING_INPUT, fluid.amount(), 0);
            int available = 0;
            for (FluidInputTransfer transfer : route.transfers()) {
                FluidInputState state = fluidInputState(states, transfer);
                int remaining = state.drain(transfer.stack(), transfer.stack().getAmount(), new ArrayList<>());
                available += transfer.stack().getAmount() - remaining;
                if (remaining > 0) {
                    return new RequirementFailure(requirementIndex, RequirementFailure.Kind.MISSING_INPUT, fluid.amount(), available);
                }
            }
        }
        return null;
    }

    private @Nullable RequirementFailure firstFluidOutputFailure(List<MachineRequirement> requirements) {
        List<FluidOutputState> states = new ArrayList<>();
        for (int requirementIndex = 0; requirementIndex < requirements.size(); requirementIndex++) {
            MachineRequirement requirement = requirements.get(requirementIndex);
            if (!(requirement instanceof FluidRequirement fluid) || fluid.io() != RecipeModifier.IOType.OUTPUT) continue;
            if (fluid.chance() <= 0F) continue;
            FluidOutputRoute route = requirementIndex < fluidOutputRoutes.size() ? fluidOutputRoutes.get(requirementIndex) : null;
            if (route == null) return new RequirementFailure(requirementIndex, RequirementFailure.Kind.MISSING_OUTPUT, fluid.stack().getAmount(), 0);
            int available = 0;
            for (FluidOutputTransfer transfer : route.transfers()) {
                FluidOutputState state = fluidOutputState(states, transfer);
                int remaining = state.fill(transfer.stack(), transfer.stack().getAmount(), new ArrayList<>());
                available += transfer.stack().getAmount() - remaining;
                if (remaining > 0) {
                    return new RequirementFailure(requirementIndex, RequirementFailure.Kind.MISSING_OUTPUT, fluid.stack().getAmount(), available);
                }
            }
        }
        return null;
    }

    public boolean collectItemInputRoute(int requirementIndex) {
        return requirementIndex < itemInputRoutes.size() && itemInputRoutes.get(requirementIndex) != null;
    }

    public boolean collectItemOutputRoute(int requirementIndex) {
        return requirementIndex < itemOutputRoutes.size() && itemOutputRoutes.get(requirementIndex) != null;
    }

    public boolean collectFluidInputRoute(int requirementIndex) {
        return requirementIndex < fluidInputRoutes.size() && fluidInputRoutes.get(requirementIndex) != null;
    }

    public boolean collectFluidOutputRoute(int requirementIndex) {
        return requirementIndex < fluidOutputRoutes.size() && fluidOutputRoutes.get(requirementIndex) != null;
    }

    private <T extends BlockEntity> List<T> liveComponents(Class<T> type, IOType ioType, List<String> requiredTags) {
        var level = controller.getLevel();
        if (level == null) return List.of();

        List<T> matches = new ArrayList<>();
        for (ProcessingComponent component : controller.getComponents()) {
            if (!type.isInstance(component.getContainer())) continue;
            if (!matchesIo(component.getContainer(), ioType)) continue;
            if (!tagsMatch(requiredTags, component.tags())) continue;
            BlockEntity live = level.getBlockEntity(component.getPos());
            if (live == component.getContainer()) {
                matches.add(type.cast(live));
            }
        }
        return matches;
    }

    private <T extends BlockEntity> List<T> excludedLiveComponents(Class<T> type, IOType ioType, List<String> requiredTags) {
        if (requiredTags.isEmpty()) return List.of();
        var level = controller.getLevel();
        if (level == null) return List.of();

        List<T> matches = new ArrayList<>();
        for (ProcessingComponent component : controller.getComponents()) {
            if (!type.isInstance(component.getContainer())) continue;
            if (!matchesIo(component.getContainer(), ioType)) continue;
            if (tagsMatch(requiredTags, component.tags())) continue;
            BlockEntity live = level.getBlockEntity(component.getPos());
            if (live == component.getContainer()) {
                matches.add(type.cast(live));
            }
        }
        return matches;
    }

    private static boolean matchesIo(BlockEntity entity, IOType ioType) {
        if (entity instanceof ItemBusBlockEntity bus) return bus.ioType() == ioType;
        if (entity instanceof FluidHatchBlockEntity hatch) return hatch.ioType() == ioType;
        if (entity instanceof EnergyHatchBlockEntity hatch) return hatch.ioType() == ioType;
        return false;
    }

    private @Nullable ItemBusBlockEntity itemBusAt(net.minecraft.core.BlockPos pos, IOType ioType) {
        for (ProcessingComponent component : controller.getComponents()) {
            if (!(component.getContainer() instanceof ItemBusBlockEntity bus)) continue;
            if (bus.ioType() == ioType && component.getPos().equals(pos)) return bus;
        }
        return null;
    }

    private @Nullable FluidHatchBlockEntity fluidHatchAt(net.minecraft.core.BlockPos pos, IOType ioType) {
        for (ProcessingComponent component : controller.getComponents()) {
            if (!(component.getContainer() instanceof FluidHatchBlockEntity hatch)) continue;
            if (hatch.ioType() == ioType && component.getPos().equals(pos)) return hatch;
        }
        return null;
    }

    private static boolean tagsMatch(List<String> required, List<String> componentTags) {
        if (required.isEmpty()) return true;
        if (componentTags.isEmpty()) return false;
        for (String tag : required) {
            if (componentTags.contains(tag)) return true;
        }
        return false;
    }

    private List<ItemInputState> itemInputStates(List<String> requiredTags) {
        List<ItemInputState> states = new ArrayList<>();
        for (ItemBusBlockEntity bus : liveComponents(ItemBusBlockEntity.class, IOType.INPUT, requiredTags)) {
            IItemHandler handler = bus.getItemHandler(null);
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                states.add(new ItemInputState(bus, handler, slot, handler.getStackInSlot(slot).copy()));
            }
        }
        return states;
    }

    private List<ItemOutputState> itemOutputStates(List<String> requiredTags) {
        List<ItemOutputState> source = simulatedItemOutputStates == null ? allItemOutputStates() : simulatedItemOutputStates;
        List<ItemOutputState> states = new ArrayList<>();
        for (ItemOutputState state : source) {
            if (tagsMatch(requiredTags, state.tags())) states.add(state);
        }
        return states;
    }

    private List<ItemOutputState> allItemOutputStates() {
        var level = controller.getLevel();
        if (level == null) return List.of();
        List<ItemOutputState> states = new ArrayList<>();
        for (ProcessingComponent component : controller.getComponents()) {
            if (!(component.getContainer() instanceof ItemBusBlockEntity bus)) continue;
            if (!matchesIo(bus, IOType.OUTPUT)) continue;
            BlockEntity live = level.getBlockEntity(component.getPos());
            if (live != bus) continue;
            IItemHandler handler = bus.getItemHandler(null);
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                states.add(new ItemOutputState(bus, handler, slot, handler.getStackInSlot(slot).copy(), handler.getSlotLimit(slot), component.tags()));
            }
        }
        return states;
    }

    private List<FluidInputState> fluidInputStates(List<String> requiredTags) {
        List<FluidInputState> states = new ArrayList<>();
        for (FluidHatchBlockEntity hatch : liveComponents(FluidHatchBlockEntity.class, IOType.INPUT, requiredTags)) {
            IFluidHandler handler = hatch.getFluidHandler(null);
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                states.add(new FluidInputState(hatch, handler, tank, handler.getFluidInTank(tank).copy()));
            }
        }
        return states;
    }

    private List<FluidOutputState> fluidOutputStates(List<String> requiredTags) {
        List<FluidOutputState> source = simulatedFluidOutputStates == null ? allFluidOutputStates() : simulatedFluidOutputStates;
        List<FluidOutputState> states = new ArrayList<>();
        for (FluidOutputState state : source) {
            if (tagsMatch(requiredTags, state.tags())) states.add(state);
        }
        return states;
    }

    private List<FluidOutputState> allFluidOutputStates() {
        var level = controller.getLevel();
        if (level == null) return List.of();
        List<FluidOutputState> states = new ArrayList<>();
        for (ProcessingComponent component : controller.getComponents()) {
            if (!(component.getContainer() instanceof FluidHatchBlockEntity hatch)) continue;
            if (!matchesIo(hatch, IOType.OUTPUT)) continue;
            BlockEntity live = level.getBlockEntity(component.getPos());
            if (live != hatch) continue;
            IFluidHandler handler = hatch.getFluidHandler(null);
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                states.add(new FluidOutputState(hatch, handler, tank, handler.getFluidInTank(tank).copy(), handler.getTankCapacity(tank), component.tags()));
            }
        }
        return states;
    }

    private static void extract(List<ItemInputTransfer> transfers) {
        for (ItemInputTransfer transfer : transfers) {
            transfer.handler().extractItem(transfer.slot(), transfer.amount(), false);
        }
    }

    private static void insert(List<ItemOutputTransfer> transfers) {
        for (ItemOutputTransfer transfer : transfers) {
            ItemStack stack = normalizeRecipeOutput(transfer.stack());
            normalizeSlotBeforeInsert(transfer.handler(), transfer.slot(), stack);
            transfer.handler().insertItem(transfer.slot(), stack, false);
        }
    }

    private static boolean canDrain(List<FluidInputTransfer> transfers) {
        List<FluidInputState> states = new ArrayList<>();
        for (FluidInputTransfer transfer : transfers) {
            FluidInputState state = fluidInputState(states, transfer);
            if (state.drain(transfer.stack(), transfer.stack().getAmount(), new ArrayList<>()) > 0) return false;
        }
        return true;
    }

    private static void drain(List<FluidInputTransfer> transfers) {
        for (FluidInputTransfer transfer : transfers) {
            transfer.handler().drain(transfer.stack(), IFluidHandler.FluidAction.EXECUTE);
        }
    }

    private static boolean canFill(List<FluidOutputTransfer> transfers) {
        List<FluidOutputState> states = new ArrayList<>();
        for (FluidOutputTransfer transfer : transfers) {
            FluidOutputState state = fluidOutputState(states, transfer);
            if (state.fill(transfer.stack(), transfer.stack().getAmount(), new ArrayList<>()) > 0) return false;
        }
        return true;
    }

    private static void fill(List<FluidOutputTransfer> transfers) {
        for (FluidOutputTransfer transfer : transfers) {
            transfer.handler().fill(transfer.stack(), IFluidHandler.FluidAction.EXECUTE);
        }
    }

    private static ItemInputState itemInputState(List<ItemInputState> states, ItemInputTransfer transfer) {
        for (ItemInputState state : states) {
            if (state.handler == transfer.handler() && state.slot == transfer.slot()) return state;
        }
        ItemInputState state = new ItemInputState(null, transfer.handler(), transfer.slot(), transfer.handler().getStackInSlot(transfer.slot()).copy());
        states.add(state);
        return state;
    }

    private static ItemOutputState itemOutputState(List<ItemOutputState> states, ItemOutputTransfer transfer) {
        for (ItemOutputState state : states) {
            if (state.handler == transfer.handler() && state.slot == transfer.slot()) return state;
        }
        ItemOutputState state = new ItemOutputState(null, transfer.handler(), transfer.slot(),
                transfer.handler().getStackInSlot(transfer.slot()).copy(), transfer.handler().getSlotLimit(transfer.slot()));
        states.add(state);
        return state;
    }

    private static ItemStack normalizeRecipeOutput(ItemStack stack) {
        if (stack.isEmpty()) return stack;
        if (!stack.getComponents().isEmpty()) return stack.copy();
        ItemStack normalized = stack.getItem().getDefaultInstance();
        if (normalized.isEmpty() || stack.getMaxStackSize() >= normalized.getMaxStackSize()) return stack.copy();
        normalized.setCount(stack.getCount());
        return normalized;
    }

    private static void normalizeSlotBeforeInsert(IItemHandler handler, int slot, ItemStack input) {
        if (!(handler instanceof IItemHandlerModifiable modifiable) || input.isEmpty()) return;
        ItemStack current = handler.getStackInSlot(slot);
        if (current.isEmpty()) return;
        ItemStack normalized = normalizeRecipeOutput(current);
        if (normalized == current || normalized.getMaxStackSize() <= current.getMaxStackSize()) return;
        if (!ItemStack.isSameItemSameComponents(normalized, input)) return;
        modifiable.setStackInSlot(slot, normalized.copyWithCount(current.getCount()));
    }

    private static FluidInputState fluidInputState(List<FluidInputState> states, FluidInputTransfer transfer) {
        for (FluidInputState state : states) {
            if (state.handler == transfer.handler() && state.tank == transfer.tank()) return state;
        }
        FluidInputState state = new FluidInputState(null, transfer.handler(), transfer.tank(), transfer.handler().getFluidInTank(transfer.tank()).copy());
        states.add(state);
        return state;
    }

    private static FluidOutputState fluidOutputState(List<FluidOutputState> states, FluidOutputTransfer transfer) {
        for (FluidOutputState state : states) {
            if (state.handler == transfer.handler() && state.tank == transfer.tank()) return state;
        }
        FluidOutputState state = new FluidOutputState(null, transfer.handler(), transfer.tank(),
                transfer.handler().getFluidInTank(transfer.tank()).copy(), transfer.handler().getTankCapacity(transfer.tank()));
        states.add(state);
        return state;
    }

    private record ItemInputRoute(List<ItemInputTransfer> transfers) {}

    private record ItemOutputRoute(List<ItemOutputTransfer> transfers) {}

    private record FluidInputRoute(List<FluidInputTransfer> transfers) {}

    private record FluidOutputRoute(List<FluidOutputTransfer> transfers) {}

    private record ItemInputTransfer(IItemHandler handler, @Nullable net.minecraft.core.BlockPos pos, int slot,
                                     MachineIngredient.ItemIngredient ingredient, int amount) {}

    private record ItemOutputTransfer(IItemHandler handler, @Nullable net.minecraft.core.BlockPos pos, int slot, ItemStack stack) {}

    private record FluidInputTransfer(IFluidHandler handler, @Nullable net.minecraft.core.BlockPos pos, int tank, FluidStack stack) {}

    private record FluidOutputTransfer(IFluidHandler handler, @Nullable net.minecraft.core.BlockPos pos, int tank, FluidStack stack) {}

    private static final class ItemInputState {
        private final ItemBusBlockEntity bus;
        private final IItemHandler handler;
        private final int slot;
        private final ItemStack stack;

        private ItemInputState(ItemBusBlockEntity bus, IItemHandler handler, int slot, ItemStack stack) {
            this.bus = bus;
            this.handler = handler;
            this.slot = slot;
            this.stack = stack;
        }

        private int extract(MachineIngredient.ItemIngredient ingredient, int remaining, List<ItemInputTransfer> transfers) {
            if (remaining <= 0 || !ingredient.item().test(stack)) return remaining;
            int taken = Math.min(remaining, stack.getCount());
            if (taken <= 0) return remaining;
            transfers.add(new ItemInputTransfer(handler, bus == null ? null : bus.getBlockPos(), slot, ingredient, taken));
            stack.shrink(taken);
            return remaining - taken;
        }

        private ItemBusBlockEntity bus() {
            return bus;
        }

        private ItemStack stack() {
            return stack;
        }
    }

    private static final class ItemOutputState {
        private final ItemBusBlockEntity bus;
        private final IItemHandler handler;
        private final int slot;
        private ItemStack stack;
        private final int limit;
        private final List<String> tags;

        private ItemOutputState(ItemBusBlockEntity bus, IItemHandler handler, int slot, ItemStack stack, int limit) {
            this(bus, handler, slot, stack, limit, List.of());
        }

        private ItemOutputState(ItemBusBlockEntity bus, IItemHandler handler, int slot, ItemStack stack, int limit, List<String> tags) {
            this.bus = bus;
            this.handler = handler;
            this.slot = slot;
            this.stack = stack;
            this.limit = limit;
            this.tags = tags == null ? List.of() : List.copyOf(tags);
        }

        private ItemStack insert(ItemStack input, List<ItemOutputTransfer> transfers) {
            input = normalizeRecipeOutput(input);
            if (input.isEmpty()) return input;
            if (!stack.isEmpty()) {
                stack = normalizeRecipeOutput(stack);
                if (!ItemStack.isSameItemSameComponents(stack, input)) return input;
            }
            int slotLimit = Math.min(limit, input.getMaxStackSize());
            int room = stack.isEmpty() ? slotLimit : slotLimit - stack.getCount();
            int inserted = Math.min(room, input.getCount());
            if (inserted <= 0) return input;
            ItemStack insertedStack = input.copyWithCount(inserted);
            transfers.add(new ItemOutputTransfer(handler, bus == null ? null : bus.getBlockPos(), slot, insertedStack));
            if (stack.isEmpty()) {
                stack = insertedStack.copy();
            } else {
                stack.grow(inserted);
            }
            ItemStack remaining = input.copy();
            remaining.shrink(inserted);
            return remaining;
        }

        private ItemStack insertIntoMatchingStack(ItemStack input, List<ItemOutputTransfer> transfers) {
            return stack.isEmpty() ? input : insert(input, transfers);
        }

        private ItemBusBlockEntity bus() {
            return bus;
        }

        private List<String> tags() {
            return tags;
        }
    }

    private static final class FluidInputState {
        private final FluidHatchBlockEntity hatch;
        private final IFluidHandler handler;
        private final int tank;
        private final FluidStack stack;

        private FluidInputState(FluidHatchBlockEntity hatch, IFluidHandler handler, int tank, FluidStack stack) {
            this.hatch = hatch;
            this.handler = handler;
            this.tank = tank;
            this.stack = stack;
        }

        private int drain(MachineIngredient.FluidIngredient ingredient, int remaining, List<FluidInputTransfer> transfers) {
            return ingredient.fluid().test(stack) ? drain(stack, remaining, transfers) : remaining;
        }

        private int drain(FluidStack match, int remaining, List<FluidInputTransfer> transfers) {
            if (remaining <= 0 || !FluidStack.isSameFluidSameComponents(stack, match)) return remaining;
            int drained = Math.min(remaining, stack.getAmount());
            if (drained <= 0) return remaining;
            FluidStack transfer = stack.copy();
            transfer.setAmount(drained);
            transfers.add(new FluidInputTransfer(handler, hatch == null ? null : hatch.getBlockPos(), tank, transfer));
            stack.shrink(drained);
            return remaining - drained;
        }

        private FluidHatchBlockEntity hatch() {
            return hatch;
        }
    }

    private static final class FluidOutputState {
        private final FluidHatchBlockEntity hatch;
        private final IFluidHandler handler;
        private final int tank;
        private FluidStack stack;
        private final int capacity;
        private final List<String> tags;

        private FluidOutputState(FluidHatchBlockEntity hatch, IFluidHandler handler, int tank, FluidStack stack, int capacity) {
            this(hatch, handler, tank, stack, capacity, List.of());
        }

        private FluidOutputState(FluidHatchBlockEntity hatch, IFluidHandler handler, int tank, FluidStack stack, int capacity, List<String> tags) {
            this.hatch = hatch;
            this.handler = handler;
            this.tank = tank;
            this.stack = stack;
            this.capacity = capacity;
            this.tags = tags == null ? List.of() : List.copyOf(tags);
        }

        private int fill(FluidStack input, int remaining, List<FluidOutputTransfer> transfers) {
            if (remaining <= 0) return remaining;
            if (!stack.isEmpty() && !FluidStack.isSameFluidSameComponents(stack, input)) return remaining;
            int room = stack.isEmpty() ? capacity : capacity - stack.getAmount();
            int filled = Math.min(room, remaining);
            if (filled <= 0) return remaining;
            FluidStack transfer = input.copy();
            transfer.setAmount(filled);
            transfers.add(new FluidOutputTransfer(handler, hatch == null ? null : hatch.getBlockPos(), tank, transfer));
            if (stack.isEmpty()) {
                stack = transfer.copy();
            } else {
                stack.grow(filled);
            }
            return remaining - filled;
        }

        private FluidHatchBlockEntity hatch() {
            return hatch;
        }

        private List<String> tags() {
            return tags;
        }
    }
}
