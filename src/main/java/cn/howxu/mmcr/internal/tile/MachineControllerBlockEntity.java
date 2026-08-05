package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockArrayCache;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.StructureMatcher;
import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineComponentTile;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContext;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.nibelungorum.DefaultMachines;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class MachineControllerBlockEntity extends BlockEntity {

    private static final Logger LOG = LoggerFactory.getLogger(MachineControllerBlockEntity.class);
    private static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger();

    private final int instanceId = INSTANCE_COUNTER.incrementAndGet();

    private Machine machine;
    private Machine foundMachine;
    private BlockArray foundPattern;
    private Direction controllerFacing;
    private ActiveMachineRecipe active;
    private RecipeCraftingContext context;
    private final List<ProcessingComponent> components = new ArrayList<>();
    private boolean clientActive;
    private Boolean lastBroadcastFormed;
    private boolean lastBroadcastActive;

    public MachineControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.controllerFor(machineIdFromState(state)).get(), pos, state);
        LOG.info("Controller BE spawned: instance=#{} pos={} blockStateMachineId={}", instanceId, pos, machineIdFromState(state));
    }

    private static Identifier machineIdFromState(BlockState state) {
        if (state.getBlock() instanceof MachineControllerBlock controller) {
            return controller.machineId();
        }
        throw new IllegalArgumentException("MachineControllerBlockEntity requires a MachineControllerBlock state");
    }

    public Machine getMachine() { return machine; }
    public void setMachine(Machine m) {
        Identifier before = this.machine == null ? null : this.machine.registryName();
        this.machine = m;
        LOG.info("[Ctrl#{}] setMachine: {} → {} at pos={}", instanceId, before, m == null ? null : m.registryName(), getBlockPos());
        setChanged();
    }

    public Machine getFoundMachine() { return foundMachine; }
    public BlockArray getFoundPattern() { return foundPattern; }

    public boolean isFormed() { return getBlockState().getValue(MachineControllerBlock.FORMED); }
    public void setFormed(boolean f) {
        boolean before = isFormed();
        if (before == f) return;
        level.setBlock(getBlockPos(), getBlockState().setValue(MachineControllerBlock.FORMED, f), 3);
        LOG.info("[Ctrl#{}] setFormed: {} → {} at pos={}", instanceId, before, f, getBlockPos());
    }

    public MachineRecipe getActiveRecipe() { return active == null ? null : active.getRecipe(); }

    public int getTickCounter() { return active == null ? 0 : active.getTick(); }

    public ActiveMachineRecipe getActive() { return active; }

    public void applyClientState(String recipeName, boolean formed) {
        if (level == null || !level.isClientSide()) return;
        boolean active = recipeName != null && !recipeName.isEmpty();
        if (isFormed() != formed) {
            level.setBlock(getBlockPos(), getBlockState().setValue(MachineControllerBlock.FORMED, formed), 3);
        }
        if (getBlockState().getValue(MachineControllerBlock.ACTIVE) != active) {
            level.setBlock(getBlockPos(), getBlockState().setValue(MachineControllerBlock.ACTIVE, active), 3);
        }
        this.clientActive = active;
    }

    public boolean hasClientActiveRecipe() { return clientActive; }

    public List<ProcessingComponent> getComponents() { return List.copyOf(components); }

    public void serverTick() {
        if (level == null || level.isClientSide()) return;
        Identifier boundMachine = machine == null ? null : machine.registryName();
        Identifier activeRecipe = active == null ? null : active.getRecipe().id();
        boolean activeBefore = active != null;
        LOG.debug("[Ctrl#{}] serverTick pos={} formed={} boundMachine={} activeRecipe={} tick={}/{}",
                instanceId, getBlockPos(), isFormed(), boundMachine, activeRecipe,
                active == null ? -1 : active.getTick(), active == null ? -1 : active.getTotalTick());
        if (machine == null) bindDefaultMachine();

        checkStructure();
        if (isFormed()) {
            if (active == null) tryStartNewRecipe();
            if (active != null) tickActiveRecipe();
        }
        broadcastStateIfChanged(activeBefore);
    }

    private void checkStructure() {
        Direction facing = getBlockState().getValue(MachineControllerBlock.FACING);
        if (foundMachine != null && foundPattern != null && controllerFacing == facing) {
            if (StructureMatcher.matchesRotated(foundPattern, level, getBlockPos())) {
                if (!isFormed()) setFormed(true);
                updateComponents();
                return;
            }
            LOG.info("[Ctrl#{}] checkStructure: cached pattern for {} no longer matches → reset", instanceId, foundMachine.registryName());
            resetMachine();
            return;
        }

        if (machine != null) {
            if (tryFormMachine(machine, facing)) {
                LOG.debug("[Ctrl#{}] checkStructure: pre-bound machine {} matched facing {}", instanceId, machine.registryName(), facing);
                return;
            }
            LOG.debug("[Ctrl#{}] checkStructure: pre-bound machine {} did not match, scanning registry", instanceId, machine.registryName());
        }
        checkAllPatterns(facing);
        if (!isFormed()) resetMachine();
    }

    private void checkAllPatterns(Direction facing) {
        int totalCandidates = 0;
        int rejected = 0;
        int accepted = -1;
        for (Machine candidate : MachineRegistry.getAll().values()) {
            if (candidate == machine) continue;
            totalCandidates++;
            if (tryFormMachine(candidate, facing)) {
                accepted = totalCandidates;
                LOG.info("[Ctrl#{}] checkAllPatterns: auto-matched {} from registry (scanned {} of {} candidates, rejected {} before)",
                        instanceId, candidate.registryName(), totalCandidates, MachineRegistry.getAll().size(), rejected);
                return;
            }
            rejected++;
        }
        if (totalCandidates > 0 && accepted < 0) {
            LOG.debug("[Ctrl#{}] checkAllPatterns: no match among {} candidates at pos={} facing {}",
                    instanceId, totalCandidates, getBlockPos(), facing);
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
        updateComponents();
        LOG.info("[Ctrl#{}] onStructureFormed: pos={} machine={} facing={}", instanceId, getBlockPos(), matchedMachine.registryName(), facing);
        setChanged();
    }

    private void updateComponents() {
        components.clear();
        if (level == null || foundMachine == null || foundPattern == null) return;

        int itemInputs = 0;
        int itemOutputs = 0;
        int fluidInputs = 0;
        int fluidOutputs = 0;
        int energyInputs = 0;
        int energyOutputs = 0;
        for (BlockPos relativePos : foundPattern.pattern().keySet()) {
            BlockPos worldPos = getBlockPos().offset(relativePos);
            if (!(level.getBlockEntity(worldPos) instanceof MachineComponentTile tile)) continue;

            var component = tile.provideComponent();
            if (!(tile instanceof BlockEntity container)) continue;
            components.add(new ProcessingComponent(component, container, worldPos, relativePos, null));
            switch (component.kind().id()) {
                case "item_input_bus" -> itemInputs++;
                case "item_output_bus" -> itemOutputs++;
                case "fluid_input_hatch" -> fluidInputs++;
                case "fluid_output_hatch" -> fluidOutputs++;
                case "energy_input_hatch" -> energyInputs++;
                case "energy_output_hatch" -> energyOutputs++;
                default -> { }
            }
        }
        LOG.info("[Ctrl#{}] updateComponents: machine={} components={} itemInputs={} itemOutputs={} fluidInputs={} fluidOutputs={} energyInputs={} energyOutputs={}",
                instanceId, foundMachine.registryName(), components.size(), itemInputs, itemOutputs, fluidInputs, fluidOutputs, energyInputs, energyOutputs);
    }

    private void resetMachine() {
        boolean wasFormed = isFormed();
        Identifier dropped = foundMachine == null ? null : foundMachine.registryName();
        boolean hadActive = active != null;
        Identifier activeRecipe = hadActive ? active.getRecipe().id() : null;
        foundMachine = null;
        foundPattern = null;
        controllerFacing = null;
        components.clear();
        if (active != null) {
            active = null;
            context = null;
            setActiveState(false);
        }
        if (wasFormed) setFormed(false);
        if (dropped != null || hadActive) {
            LOG.info("[Ctrl#{}] resetMachine: pos={} dropped={} clearedActiveRecipe={} wasFormed={}", instanceId, getBlockPos(), dropped, activeRecipe, wasFormed);
        }
        setChanged();
    }

    private void broadcastStateIfChanged(boolean activeBeforeTick) {
        boolean formed = isFormed();
        boolean activeNow = active != null;
        if (lastBroadcastFormed != null && lastBroadcastFormed == formed && lastBroadcastActive == activeNow && activeBeforeTick == activeNow) {
            return;
        }
        lastBroadcastFormed = formed;
        lastBroadcastActive = activeNow;
        if (!(level instanceof ServerLevel sl)) return;
        String name = active == null ? "" : active.getRecipe().id().toString();
        var pkt = new PktMachineStatePayload(getBlockPos(), name, formed);
        int sent = 0;
        for (var player : sl.getPlayers(p -> p.distanceToSqr(getBlockPos().getCenter()) < 64 * 64)) {
            ((ServerPlayer) player).connection.send(new ClientboundCustomPayloadPacket(pkt));
            sent++;
        }
        LOG.debug("[Ctrl#{}] broadcastState: pos={} recipe='{}' formed={} → {} player(s) within 64 blocks", instanceId, getBlockPos(), name, isFormed(), sent);
    }

    private void tryStartNewRecipe() {
        List<MachineRecipe> candidates = recipesForMachine();
        int index = 0;
        for (MachineRecipe recipe : candidates) {
            index++;
            RecipeCraftingContext candidate = new RecipeCraftingContext(this);
            boolean inputsOk = candidate.simulateInputs(recipe);
            boolean outputsOk = candidate.simulateOutputs(recipe);
            if (!inputsOk || !outputsOk) {
                continue;
            }
            ActiveMachineRecipe next = new ActiveMachineRecipe(recipe, 1);
            active = next;
            context = candidate;
            if (!next.start(context)) {
                active = null;
                context = null;
                LOG.info("[Ctrl#{}] tryStartNewRecipe: recipe={} refused during start; waiting for I/O at pos={}",
                        instanceId, recipe.id(), getBlockPos());
                continue;
            }
            setActiveState(true);
            LOG.info("[Ctrl#{}] tryStartNewRecipe: START recipe={} tickTime={} priority={} maxParallel={} (chosen {}/{} candidates)",
                    instanceId, recipe.id(), recipe.tickTime(), recipe.priority(), next.getMaxParallelism(), index, candidates.size());
            setChanged();
            return;
        }
        LOG.info("[Ctrl#{}] tryStartNewRecipe: no compatible recipe among {} candidates; waiting for I/O at pos={}", instanceId, candidates.size(), getBlockPos());
    }

    private void tickActiveRecipe() {
        if (active == null || context == null) return;
        int before = active.getTick();
        ActiveMachineRecipe.TickStatus status = active.tick(context);
        if (status == ActiveMachineRecipe.TickStatus.FINISHED) {
            LOG.info("[Ctrl#{}] tickActiveRecipe: recipe {} FINISHED after {} ticks (total {}) at pos={}; slot cleared",
                    instanceId, active.getRecipe().id(), active.getTick(), active.getTotalTick(), getBlockPos());
            active = null;
            context = null;
            setActiveState(false);
        } else if (status == ActiveMachineRecipe.TickStatus.WAITING) {
            LOG.debug("[Ctrl#{}] tickActiveRecipe: recipe {} WAITING ({} → {} of {}) at pos={}",
                    instanceId, active.getRecipe().id(), before, active.getTick(), active.getTotalTick(), getBlockPos());
            if (active.getRecipe().doesCancelRecipeOnPerTickFailure()) {
                LOG.info("[Ctrl#{}] tickActiveRecipe: recipe {} canceled after per-tick failure at pos={}; already consumed inputs are voided",
                        instanceId, active.getRecipe().id(), getBlockPos());
                active = null;
                context = null;
                setActiveState(false);
            }
        } else {
            LOG.debug("[Ctrl#{}] tickActiveRecipe: recipe {} CONTINUE ({} → {} of {}) at pos={}",
                    instanceId, active.getRecipe().id(), before, active.getTick(), active.getTotalTick(), getBlockPos());
        }
        setChanged();
    }

    private void setActiveState(boolean activeState) {
        if (level == null || level.isClientSide()) return;
        if (getBlockState().getValue(MachineControllerBlock.ACTIVE) != activeState) {
            level.setBlock(getBlockPos(), getBlockState().setValue(MachineControllerBlock.ACTIVE, activeState), 3);
        }
    }

    void bindDefaultMachine() {
        bindDefaultMachine(machineIdFromState(getBlockState()));
    }

    void bindDefaultMachine(Identifier machineId) {
        DefaultMachines.ensureRegistered();
        Machine resolved = cn.howxu.mmcr.api.machine.MachineRegistry.getMachine(machineId);
        LOG.info("[Ctrl#{}] bindDefaultMachine: resolving state-bound machineId={} → resolved={}", instanceId, machineId, resolved == null ? null : resolved.registryName());
        setMachine(resolved);
    }

    private List<MachineRecipe> recipesForMachine() {
        Map<Identifier, MachineRecipe> recipes = new LinkedHashMap<>();
        for (MachineRecipe recipe : RecipeRegistry.byMachine(machine)) {
            recipes.put(recipe.id(), recipe);
        }
        int fromRegistry = recipes.size();
        if (level instanceof ServerLevel sl) {
            for (RecipeHolder<?> holder : sl.recipeAccess().getRecipes()) {
                if (holder.value() instanceof MachineRecipe recipe
                        && recipe.machineId().equals(machine.registryName())) {
                    recipes.putIfAbsent(recipe.id(), recipe);
                }
            }
        }
        int fromVanilla = recipes.size() - fromRegistry;
        LOG.debug("[Ctrl#{}] recipesForMachine({}): total={} (RecipeRegistry={}, RecipeManager={})",
                instanceId, machine.registryName(), recipes.size(), fromRegistry, fromVanilla);
        return new ArrayList<>(recipes.values());
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        Identifier recipeId = active == null ? null : active.getRecipe().id();
        int tick = active == null ? -1 : active.getTick();
        int totalTick = active == null ? -1 : active.getTotalTick();
        if (active != null) {
            output.putBoolean("has_active", true);
            active.serialize(output.child("active_recipe"));
        } else {
            output.putBoolean("has_active", false);
        }
        LOG.debug("[Ctrl#{}] saveAdditional: pos={} hasActive={} recipe={} tick={}/{}", instanceId, getBlockPos(), active != null, recipeId, tick, totalTick);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        if (!input.getBooleanOr("has_active", false)) {
            LOG.debug("[Ctrl#{}] loadAdditional: pos={} no active recipe stored", instanceId, getBlockPos());
            active = null;
            context = null;
            return;
        }
        ActiveMachineRecipe restored = ActiveMachineRecipe.from(input.childOrEmpty("active_recipe"));
        if (restored.getRecipe() == null) {
            Identifier missing = restored.getRegistryName() == null ? null : Identifier.parse(restored.getRegistryName());
            LOG.warn("[Ctrl#{}] loadAdditional: stored recipe {} not found in registry; clearing slot", instanceId, missing);
            active = null;
            context = null;
            return;
        }
        active = restored;
        context = new RecipeCraftingContext(this);
        LOG.info("[Ctrl#{}] loadAdditional: pos={} restored active recipe={} tick={}/{}", instanceId, getBlockPos(), restored.getRecipe().id(), restored.getTick(), restored.getTotalTick());
        setChanged();
    }
}
