package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot.Kind;
import cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot.Role;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData.HeaderData;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData.LaneData;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData.RecipeData;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData.OutputData;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiServerSession;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.runtime.ControllerSyncRuntime;
import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import cn.howxu.mmcr.internal.runtime.FactorySnapshot;
import cn.howxu.mmcr.internal.runtime.MachineStateSnapshot;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import net.minecraft.resources.ResourceLocation;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModUIs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.function.ToIntFunction;
import net.minecraft.network.chat.Component;

/**
 * Dedicated controller menu for formed machines containing a factory scheduler.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FactoryControllerMenu extends AbstractMachineMenu implements ControllerUiMenu {
    private static final int FACTORY_PLAYER_INVENTORY_X = 112;
    private static final int FACTORY_PLAYER_INVENTORY_Y_OFFSET = 1;
    private static final ControllerSyncRuntime SYNC_RUNTIME = new ControllerSyncRuntime();

    private final BlockPos controllerPos;
    private final DataSlot formed;
    private final DataSlot redstonePaused;
    private final MachineControllerBlockEntity owner;
    private final ServerPlayer player;
    private final Level level;
    private FactorySnapshot snapshot;
    private FactorySnapshot lastSentSnapshot;
    private String selectedThreadId = "base";
    private final ControllerMenuOpenData uiOpenData;
    private final @Nullable ControllerUiServerSession uiServerSession;
    private boolean playerInventoryVisible = true;
    private @Nullable MachineStateSnapshot broadcastState;

    public FactoryControllerMenu(int containerId, Inventory inventory, MachineControllerBlockEntity owner, ServerPlayer player) {
        super(ModUIs.FACTORY_CONTROLLER.get(), containerId);
        this.owner = owner;
        this.player = inventory.player instanceof ServerPlayer serverPlayer ? serverPlayer : player;
        this.level = inventory.player == null ? null : inventory.player.level();
        controllerPos = owner == null ? BlockPos.ZERO : owner.getBlockPos();
        uiOpenData = ControllerMenuOpenData.forOwner(owner, level, Kind.FACTORY);
        uiServerSession = owner != null && owner.getLevel() != null && this.player != null
                ? new ControllerUiServerSession(this, owner, this.player) : null;
        formed = addStateSlot(owner, state -> state.formed() ? 1 : 0);
        addStateSlot(owner, state -> state.active() ? 1 : 0);
        redstonePaused = addStateSlot(owner, state -> state.redstonePaused() ? 1 : 0);
        addStateSlot(owner, MachineStateSnapshot::parallelControllerCount);
        ControllerMenuState.addControllerPlayerSlots(this, inventory,
                FACTORY_PLAYER_INVENTORY_X, FACTORY_PLAYER_INVENTORY_Y_OFFSET);
        bindInputIsolation(InputIsolationMenuState.forController(owner));
        snapshot = FactorySnapshot.empty();
        if (owner != null) {
            ControllerRuntimeSnapshot runtime = owner.runtimeSnapshot();
            if (SYNC_RUNTIME.factoryControllerPresent(runtime)) {
                snapshot = SYNC_RUNTIME.factoryState(runtime, owner.currentRecipePoolId());
            }
        }
    }

    public FactoryControllerMenu(int containerId, Inventory inventory, MachineControllerBlockEntity owner) {
        this(containerId, inventory, owner, null);
    }

    public FactoryControllerMenu(int containerId, Inventory inventory, ControllerMenuOpenData openData) {
        super(ModUIs.FACTORY_CONTROLLER.get(), containerId);
        this.owner = null;
        this.player = null;
        this.level = inventory.player == null ? null : inventory.player.level();
        this.uiOpenData = openData;
        this.uiServerSession = null;
        this.controllerPos = openData.pos();
        formed = addStateSlot(null, state -> state.formed() ? 1 : 0);
        addStateSlot(null, state -> state.active() ? 1 : 0);
        redstonePaused = addStateSlot(null, state -> state.redstonePaused() ? 1 : 0);
        addStateSlot(null, MachineStateSnapshot::parallelControllerCount);
        formed.set(openData.formed() ? 1 : 0);
        ControllerMenuState.addControllerPlayerSlots(this, inventory,
                FACTORY_PLAYER_INVENTORY_X, FACTORY_PLAYER_INVENTORY_Y_OFFSET);
        bindInputIsolation(InputIsolationMenuState.forController(null));
        snapshot = FactorySnapshot.empty();
    }

    public static FactoryControllerMenu clientOpen(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        return new FactoryControllerMenu(containerId, inventory, ControllerMenuOpenData.read(buffer));
    }

    private DataSlot addStateSlot(@Nullable MachineControllerBlockEntity controller,
                                  ToIntFunction<MachineStateSnapshot> getter) {
        return addDataSlot(controller == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() {
                MachineStateSnapshot state = broadcastState == null
                        ? SYNC_RUNTIME.machineState(controller.runtimeSnapshot()) : broadcastState;
                return getter.applyAsInt(state);
            }
            @Override public void set(int value) {}
        });
    }

    @Override
    protected DataSlot addDataSlot(DataSlot slot) {
        if (uiServerSession == null) return super.addDataSlot(slot);
        return super.addDataSlot(new DataSlot() {
            private int lastValue;
            @Override public int get() {
                if (!uiServerSession.closed()) lastValue = slot.get();
                return lastValue;
            }
            @Override public void set(int value) { slot.set(value); }
        });
    }

    public static FactoryControllerMenu clientOpen(int containerId, Inventory inventory) {
        return new FactoryControllerMenu(containerId, inventory, ControllerMenuOpenData.legacy(
                inventory.player == null ? null : inventory.player.level(), BlockPos.ZERO, null, null,
                0, false, 0, Kind.FACTORY));
    }

    @Override public ControllerMenuOpenData uiOpenData() { return uiOpenData; }
    @Override public @Nullable ControllerUiServerSession uiServerSession() { return uiServerSession; }
    @Override public boolean playerInventoryVisible() { return playerInventoryVisible; }
    @Override public void setPlayerInventoryVisible(boolean visible) { playerInventoryVisible = visible; }

    /** Compatibility projection of stored legacy values, with no block-entity lookup. */
    public ControllerUiSnapshotData legacyUiSnapshot() {
        ResourceLocation id = machineId();
        int role = snapshot.machineId().isEmpty() ? uiOpenData.role().ordinal() : snapshot.controllerRole();
        HeaderData header = new HeaderData(id, Kind.FACTORY, Role.values()[role],
                snapshot.machineName().isEmpty() ? Component.literal(id.toString()) : Component.translatable(snapshot.machineName()),
                snapshot.formed() || uiOpenData.formed(), snapshot.activeLaneCount() > 0, snapshot.paused(), snapshot.machineId().isEmpty()
                ? uiOpenData.installedModuleCount() : snapshot.installedModuleCount(), connectedHostId().orElse(null),
                snapshot.matchedStage(), snapshot.stageCount(), snapshot.foundLevelIds().stream().map(ResourceLocation::parse).toList(),
                snapshot.parallelSlots(), snapshot.maxParallelism(), snapshot.laneLimit(), snapshot.activeLaneCount(),
                recipePoolIds(), currentRecipePoolId(), snapshot.failure(), false, Map.of(), List.of());
        List<LaneData> lanes = snapshot.presentationLanes().stream().map(thread -> {
            var recipe = thread.presentation();
            return new LaneData(thread.laneId(), thread.index(), thread.baseThread(), thread.coreThread(), thread.active(),
                    thread.recipeId().isEmpty() ? null : ResourceLocation.parse(thread.recipeId()), thread.tick(), thread.totalTick(),
                    thread.parallelism(), thread.failure(), List.of(), new RecipeData(recipe.outputs().stream()
                    .map(output -> new OutputData(output.output(), output.amount())).toList(), recipe.energyInputPerTick(),
                    recipe.energyOutputPerTick(), recipe.heatOutputPerTick(), recipe.durationTicks(), recipe.parallelism()));
        }).toList();
        return new ControllerUiSnapshotData(uiOpenData.sessionId(), 0, true, uiOpenData.dimension(), controllerPos, header, lanes);
    }

    @Override
    public void removed(Player player) {
        if (player instanceof ServerPlayer && uiServerSession != null) uiServerSession.close();
        super.removed(player);
    }

    public BlockPos controllerPos() { return controllerPos; }
    public MachineControllerBlockEntity resolvedOwner() {
        if (owner != null) return owner;
        if (level == null) return null;
        BlockEntity blockEntity = level.getBlockEntity(controllerPos);
        return blockEntity instanceof MachineControllerBlockEntity controller ? controller : null;
    }
    public boolean isFormed() { return snapshot.formed() || formed.get() != 0; }
    public boolean isRedstonePaused() { return snapshot.paused() || redstonePaused.get() != 0; }
    public int activeThreadCount() { return snapshot.activeLaneCount(); }
    public int threadCount() { return snapshot.laneLimit(); }
    public long currentParallelism() {
        FactoryRuntime.ThreadSnapshot thread = selectedThread();
        return thread.active() ? thread.parallelism() : 0L;
    }
    public long maxParallelism() { return snapshot.maxParallelism(); }
    public String machineName() { return snapshot.machineName(); }
    public @Nullable ResourceLocation machineId() {
        return snapshot.machineId().isEmpty() ? uiOpenData.machineId() : ResourceLocation.tryParse(snapshot.machineId());
    }
    public @Nullable ResourceLocation currentRecipePoolId() {
        return snapshot.recipePoolId().isEmpty() ? null : ResourceLocation.tryParse(snapshot.recipePoolId());
    }
    public List<ResourceLocation> recipePoolIds() { return MachineRegistry.recipePoolsForMachine(machineId()); }
    public boolean isModuleController() { return snapshot.machineId().isEmpty()
            ? uiOpenData.role().ordinal() == 2 : snapshot.controllerRole() == 2; }
    public Optional<ResourceLocation> connectedHostId() {
        return snapshot.machineId().isEmpty() ? uiOpenData.connectedHostId()
                : Optional.ofNullable(snapshot.connectedHostId().isEmpty()
                ? null : ResourceLocation.tryParse(snapshot.connectedHostId()));
    }
    public int parallelSlots() { return snapshot.parallelSlots(); }
    public int matchedStage() { return snapshot.matchedStage(); }
    public int stageCount() { return snapshot.stageCount(); }
    public List<String> foundLevelIds() { return snapshot.foundLevelIds(); }
    public String lastFailureUnloc() {
        String threadFailure = SYNC_RUNTIME.failureMessage(selectedFailure());
        return threadFailure.isEmpty() ? SYNC_RUNTIME.failureMessage(snapshot.failure()) : threadFailure;
    }
    public List<FactoryRuntime.ThreadSnapshot> threads() { return snapshot.presentationLanes(); }

    public void applySnapshot(FactorySnapshot snapshot) {
        this.snapshot = snapshot;
        selectedThreadId = selectedThread().laneId();
    }

    public void markSnapshotSent(FactorySnapshot snapshot) {
        lastSentSnapshot = snapshot;
    }

    public FactoryRuntime.ThreadSnapshot selectedThread() {
        return snapshot.presentationLanes().stream().filter(thread -> thread.laneId().equals(selectedThreadId)).findFirst()
                .or(() -> snapshot.presentationLanes().stream().filter(FactoryRuntime.ThreadSnapshot::baseThread).findFirst())
                .orElseGet(() -> snapshot.presentationLanes().isEmpty()
                        ? FactoryRuntime.ThreadSnapshot.idleBase() : snapshot.presentationLanes().getFirst());
    }

    public @Nullable ExecutionStatus selectedFailure() {
        return selectedThread().failure();
    }

    public int selectedThreadIndex() { return selectedThread().index(); }

    public void selectThread(int index) {
        snapshot.presentationLanes().stream().filter(thread -> thread.index() == index).findFirst()
                .ifPresent(thread -> selectedThreadId = thread.laneId());
    }

    @Override
    public void broadcastChanges() {
        if (uiServerSession != null && uiServerSession.closed()) {
            // Keep vanilla inventory/carried synchronization without querying controller DataSlots.
            super.broadcastChanges();
            return;
        }
        ControllerRuntimeSnapshot broadcastRuntime = owner == null ? null : owner.runtimeSnapshot();
        try {
            broadcastState = broadcastRuntime == null ? null : SYNC_RUNTIME.machineState(broadcastRuntime);
            super.broadcastChanges();
            if (uiServerSession != null) {
                uiServerSession.broadcastChanges();
                return;
            }
            if (owner == null || !SYNC_RUNTIME.factoryControllerPresent(broadcastRuntime)) return;
            FactorySnapshot next = SYNC_RUNTIME.factoryState(broadcastRuntime, owner.currentRecipePoolId());
            applySnapshot(next);
            if (player != null && !next.equals(lastSentSnapshot)) {
                owner.sendFactoryControllerState(player);
                lastSentSnapshot = next;
            }
        } finally {
            broadcastState = null;
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return MenuSupport.noopQuickMove();
    }

    @Override
    public boolean stillValid(Player player) {
        return MenuSupport.stillValidWithin(player, controllerPos)
                && MenuSupport.controllerStillPresentAndFormed(owner);
    }
}
