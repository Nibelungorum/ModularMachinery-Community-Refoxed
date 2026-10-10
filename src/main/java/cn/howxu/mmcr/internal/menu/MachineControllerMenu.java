package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.definition.TickBehavior;
import cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot.Kind;
import cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot.Role;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData.HeaderData;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData.LaneData;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData.RecipeData;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData.OutputData;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiServerSession;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.runtime.ControllerSyncRuntime;
import cn.howxu.mmcr.internal.runtime.ControllerRecipePresentation;
import cn.howxu.mmcr.internal.runtime.MachineStateSnapshot;
import cn.howxu.mmcr.internal.network.PktMachineStatePayload;
import cn.howxu.mmcr.registry.ModUIs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import net.minecraft.network.chat.Component;

public class MachineControllerMenu extends AbstractMachineMenu implements ControllerUiMenu {
    private static final ControllerSyncRuntime SYNC_RUNTIME = new ControllerSyncRuntime();

    private final MachineControllerBlockEntity owner;
    private final @Nullable ServerPlayer serverPlayer;
    private final Level level;
    private boolean wasFormedDuringSession;
    private final BlockPos pos;
    private final DataSlot formed;
    private final DataSlot active;
    private final DataSlot activeTick;
    private final DataSlot activeTotalTick;
    private final DataSlot redstonePaused;
    private final DataSlot parallelControllerCount;
    private final DataSlot factoryControllerPresent;
    private final DataSlot factoryThreadCount;
    private final DataSlot factoryActiveThreadCount;
    private final DataSlot installedModuleCount;
    private final DataSlot moduleConnected;
    private final DataSlot controllerRole;
    private int clientControllerRole;
    private @Nullable ResourceLocation clientMachineId;
    private @Nullable ResourceLocation clientConnectedHostId;
    private @Nullable PktMachineStatePayload clientSnapshot;
    private @Nullable PktMachineStatePayload lastSentSnapshot;
    private final ControllerMenuOpenData uiOpenData;
    private final @Nullable ControllerUiServerSession uiServerSession;
    private boolean playerInventoryVisible = true;

    public MachineControllerMenu(int containerId, Inventory playerInv, MachineControllerBlockEntity owner) {
        super(ModUIs.MACHINE_CONTROLLER.get(), containerId);
        this.owner = owner;
        this.serverPlayer = playerInv.player instanceof ServerPlayer player ? player : null;
        this.level = playerInv.player == null ? null : playerInv.player.level();
        this.pos = owner == null ? BlockPos.ZERO : owner.getBlockPos();
        this.uiOpenData = ControllerMenuOpenData.forOwner(owner, level, Kind.NORMAL);
        this.uiServerSession = owner != null && owner.getLevel() != null && serverPlayer != null
                ? new ControllerUiServerSession(this, owner, serverPlayer) : null;
        wasFormedDuringSession = owner != null && machineState(owner).formed();
        this.formed = addDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return machineState(owner).formed() ? 1 : 0; }
            @Override public void set(int value) {}
        });
        this.active = addDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return machineState(owner).active() ? 1 : 0; }
            @Override public void set(int value) {}
        });
        this.activeTick = addDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return machineState(owner).tick(); }
            @Override public void set(int value) {}
        });
        this.activeTotalTick = addDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return machineState(owner).totalTick(); }
            @Override public void set(int value) {}
        });
        this.redstonePaused = addDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return machineState(owner).redstonePaused() ? 1 : 0; }
            @Override public void set(int value) {}
        });
        this.parallelControllerCount = addDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return machineState(owner).parallelControllerCount(); }
            @Override public void set(int value) {}
        });
        this.factoryControllerPresent = addDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return machineState(owner).factoryControllerPresent() ? 1 : 0; }
            @Override public void set(int value) {}
        });
        this.factoryThreadCount = addDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return machineState(owner).factoryThreadCount(); }
            @Override public void set(int value) {}
        });
        this.factoryActiveThreadCount = addDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return machineState(owner).activeFactoryThreadCount(); }
            @Override public void set(int value) {}
        });
        this.installedModuleCount = ControllerMenuState.addInstalledModuleCountSlot(this, owner);
        this.moduleConnected = ControllerMenuState.addModuleConnectedSlot(this, owner);
        this.controllerRole = ControllerMenuState.addControllerRoleSlot(this, owner);
        this.clientControllerRole = controllerRoleSyncValue(owner);
        this.clientMachineId = machineIdFor(owner);
        this.clientConnectedHostId = owner == null ? null
                : machineState(owner).connectedHostId().isEmpty() ? null : ResourceLocation.parse(machineState(owner).connectedHostId());
        addControllerPlayerSlots(playerInv);
    }

    public MachineControllerMenu(int containerId, Inventory playerInv, BlockPos pos, @Nullable ResourceLocation machineId,
                                 @Nullable ResourceLocation connectedHostId, int controllerRole, boolean formed, int installedModuleCount) {
        this(containerId, playerInv, ControllerMenuOpenData.legacy(
                playerInv.player == null ? null : playerInv.player.level(), pos, machineId, connectedHostId,
                controllerRole, formed, installedModuleCount, Kind.NORMAL));
    }

    public MachineControllerMenu(int containerId, Inventory playerInv, ControllerMenuOpenData openData) {
        super(ModUIs.MACHINE_CONTROLLER.get(), containerId);
        this.owner = null;
        this.serverPlayer = null;
        this.level = playerInv.player == null ? null : playerInv.player.level();
        this.uiOpenData = openData;
        this.uiServerSession = null;
        this.pos = openData.pos();
        this.formed = addDataSlot(DataSlot.standalone());
        this.active = addDataSlot(DataSlot.standalone());
        this.activeTick = addDataSlot(DataSlot.standalone());
        this.activeTotalTick = addDataSlot(DataSlot.standalone());
        this.redstonePaused = addDataSlot(DataSlot.standalone());
        this.parallelControllerCount = addDataSlot(DataSlot.standalone());
        this.factoryControllerPresent = addDataSlot(DataSlot.standalone());
        this.factoryThreadCount = addDataSlot(DataSlot.standalone());
        this.factoryActiveThreadCount = addDataSlot(DataSlot.standalone());
        this.installedModuleCount = addDataSlot(DataSlot.standalone());
        this.moduleConnected = addDataSlot(DataSlot.standalone());
        this.controllerRole = addDataSlot(DataSlot.standalone());
        this.clientControllerRole = openData.role().ordinal();
        this.clientMachineId = openData.machineId();
        this.clientConnectedHostId = openData.connectedHostId().orElse(null);
        this.formed.set(openData.formed() ? 1 : 0);
        this.installedModuleCount.set(openData.installedModuleCount());
        this.moduleConnected.set(openData.connectedHostId().isPresent() ? 1 : 0);
        this.controllerRole.set(openData.role().ordinal());
        addControllerPlayerSlots(playerInv);
    }

    public MachineControllerMenu(int containerId, Inventory playerInv, BlockPos pos) {
        this(containerId, playerInv, pos, null, null, 0, false, 0);
    }

    private void addControllerPlayerSlots(Inventory playerInv) {
        ControllerMenuState.addControllerPlayerSlots(this, playerInv);
    }

    public MachineControllerMenu(int containerId, Inventory playerInv) {
        this(containerId, playerInv, (MachineControllerBlockEntity) null);
    }

    public static MachineControllerMenu clientOpen(int containerId, Inventory playerInv) {
        return new MachineControllerMenu(containerId, playerInv);
    }

    public static MachineControllerMenu clientOpen(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        return new MachineControllerMenu(containerId, playerInv, ControllerMenuOpenData.read(buf));
    }

    public static void writeClientOpenData(RegistryFriendlyByteBuf buf, BlockPos pos, @Nullable ResourceLocation machineId,
                                           @Nullable ResourceLocation connectedHostId, int controllerRole, boolean formed,
                                           int installedModuleCount) {
        ControllerMenuOpenData.write(buf, ControllerMenuOpenData.legacy(null, pos, machineId, connectedHostId,
                controllerRole, formed, installedModuleCount, Kind.NORMAL));
    }

    @Override public ControllerMenuOpenData uiOpenData() { return uiOpenData; }
    @Override public @Nullable ControllerUiServerSession uiServerSession() { return uiServerSession; }
    @Override public boolean playerInventoryVisible() { return playerInventoryVisible; }
    @Override public void setPlayerInventoryVisible(boolean visible) { playerInventoryVisible = visible; }

    /** Compatibility projection for no-buffer callers. Never resolves a live block entity.
     * Session-backed screens read the session instead.
     */
    public ControllerUiSnapshotData legacyUiSnapshot() {
        PktMachineStatePayload value = clientSnapshot;
        ResourceLocation id = value == null ? uiOpenData.machineId() : identifierOrNull(value.machineId());
        if (id == null) id = uiOpenData.machineId();
        Machine machine = MachineRegistry.getMachine(id);
        Kind kind = machine != null && machine.behavior() instanceof TickBehavior ? Kind.TICK : uiOpenData.kind();
        int role = value == null ? uiOpenData.role().ordinal() : value.controllerRole();
        HeaderData header = new HeaderData(id, kind, Role.values()[role],
                machine == null ? Component.literal(id.toString()) : machine.displayName(),
                value == null ? uiOpenData.formed() : value.formed(), value != null && value.active(),
                value != null && value.redstonePaused(), value == null ? uiOpenData.installedModuleCount() : value.installedModuleCount(),
                value == null ? uiOpenData.connectedHostId().orElse(null)
                        : value.moduleConnected() ? identifierOrNull(value.connectedHostId()) : null,
                value == null ? 0 : value.matchedStage(), value == null ? 1 : value.stageCount(),
                value == null ? List.of() : value.foundLevelIds().stream().map(ResourceLocation::parse).toList(),
                value == null ? 0 : value.parallelControllerCount(), value == null ? 1 : value.maxParallelism(),
                0, 0, MachineRegistry.recipePoolsForMachine(id), value == null ? null : identifierOrNull(value.recipePoolId()),
                value == null ? null : value.failure(), false, Map.of(), List.of());
        ControllerRecipePresentation recipe = value == null ? ControllerRecipePresentation.empty() : value.recipePresentation();
        List<LaneData> lanes = kind == Kind.TICK ? List.of() : List.of(new LaneData("base", 0, true, false,
                value != null && value.active(), value == null ? null : identifierOrNull(value.recipeName()),
                value == null ? 0 : value.tick(), value == null ? 0 : value.totalTick(), value == null ? 0 : value.parallelism(),
                value == null ? null : value.failure(), List.of(), new RecipeData(recipe.outputs().stream()
                .map(output -> new OutputData(output.output(), output.amount())).toList(), recipe.energyInputPerTick(),
                recipe.energyOutputPerTick(), recipe.heatOutputPerTick(), recipe.durationTicks(), recipe.parallelism())));
        return new ControllerUiSnapshotData(uiOpenData.sessionId(), 0, value != null, uiOpenData.dimension(), pos, header, lanes);
    }

    @Override
    public void removed(Player player) {
        if (player instanceof ServerPlayer && uiServerSession != null) uiServerSession.close();
        super.removed(player);
    }

    public MachineControllerBlockEntity owner() {
        return owner;
    }

    public MachineControllerBlockEntity resolvedOwner() {
        if (owner != null) return owner;
        if (level == null) return null;
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof MachineControllerBlockEntity controller ? controller : null;
    }

    public @Nullable ResourceLocation machineId() {
        if (clientSnapshot != null) return identifierOrNull(clientSnapshot.machineId());
        MachineStateSnapshot state = localState();
        return state == null ? clientMachineId : state.machineId().isEmpty()
                ? uiOpenData.machineId() : identifierOrNull(state.machineId());
    }

    public @Nullable ResourceLocation currentRecipePoolId() {
        if (clientSnapshot != null) return identifierOrNull(clientSnapshot.recipePoolId());
        return owner == null ? null : owner.currentRecipePoolId();
    }

    public List<ResourceLocation> recipePoolIds() {
        return MachineRegistry.recipePoolsForMachine(machineId());
    }

    public boolean isTickMachine() {
        ResourceLocation machineId = machineId();
        if (machineId == null) return false;
        Machine machine = MachineRegistry.getMachine(machineId);
        return machine != null && machine.behavior() instanceof TickBehavior;
    }

    public boolean isFormed() {
        if (clientSnapshot != null) return clientSnapshot.formed();
        MachineStateSnapshot state = localState();
        return state == null ? formed.get() != 0 : state.formed();
    }

    boolean wasFormedDuringSession() {
        if (isFormed()) wasFormedDuringSession = true;
        return wasFormedDuringSession;
    }

    public boolean hasActiveRecipe() {
        if (clientSnapshot != null) return clientSnapshot.active();
        MachineStateSnapshot state = localState();
        return state == null ? active.get() != 0 : state.active();
    }

    public int activeRecipeTick() {
        if (clientSnapshot != null) return clientSnapshot.tick();
        MachineStateSnapshot state = localState();
        return state == null ? activeTick.get() : state.tick();
    }

    public int activeRecipeTotalTick() {
        if (clientSnapshot != null) return clientSnapshot.totalTick();
        MachineStateSnapshot state = localState();
        return state == null ? activeTotalTick.get() : state.totalTick();
    }

    public @Nullable ResourceLocation activeRecipeId() {
        String recipeId = clientSnapshot != null ? clientSnapshot.recipeName() : "";
        return recipeId.isEmpty() ? null : ResourceLocation.tryParse(recipeId);
    }

    public ControllerRecipePresentation recipePresentation() {
        if (clientSnapshot != null) return clientSnapshot.recipePresentation();
        MachineStateSnapshot state = localState();
        return state == null ? ControllerRecipePresentation.empty() : state.recipePresentation();
    }

    public @Nullable String lastFailureMessage() {
        String failure;
        if (clientSnapshot != null) {
            failure = SYNC_RUNTIME.failureMessage(clientSnapshot.failure());
        } else {
            MachineStateSnapshot state = localState();
            if (state == null) return null;
            failure = SYNC_RUNTIME.failureMessage(state.failure());
        }
        return failure.isEmpty() ? null : failure;
    }

    public boolean isRedstonePaused() {
        if (clientSnapshot != null) return clientSnapshot.redstonePaused();
        MachineStateSnapshot state = localState();
        return state == null ? redstonePaused.get() != 0 : state.redstonePaused();
    }

    public int parallelControllerCount() {
        if (clientSnapshot != null) return clientSnapshot.parallelControllerCount();
        MachineStateSnapshot state = localState();
        return state == null ? parallelControllerCount.get() : state.parallelControllerCount();
    }

    public long currentParallelism() {
        if (clientSnapshot != null) return clientSnapshot.parallelism();
        MachineStateSnapshot state = localState();
        return state == null ? 0L : state.parallelism();
    }

    public long maxParallelism() {
        if (clientSnapshot != null) return clientSnapshot.maxParallelism();
        MachineStateSnapshot state = localState();
        return state == null ? 1L : state.maxParallelism();
    }

    public boolean hasFactoryController() {
        if (clientSnapshot != null) return factoryControllerPresent.get() != 0;
        MachineStateSnapshot state = localState();
        return state == null ? factoryControllerPresent.get() != 0 : state.factoryControllerPresent();
    }

    public int factoryThreadCount() {
        if (clientSnapshot != null) return factoryThreadCount.get();
        MachineStateSnapshot state = localState();
        return state == null ? factoryThreadCount.get() : state.factoryThreadCount();
    }

    public int factoryActiveThreadCount() {
        if (clientSnapshot != null) return factoryActiveThreadCount.get();
        MachineStateSnapshot state = localState();
        return state == null ? factoryActiveThreadCount.get() : state.activeFactoryThreadCount();
    }

    public int installedModuleCount() {
        if (clientSnapshot != null) return clientSnapshot.installedModuleCount();
        MachineStateSnapshot state = localState();
        return state == null ? installedModuleCount.get() : state.installedModuleCount();
    }

    public Optional<ResourceLocation> connectedHostId() {
        if (clientSnapshot != null) return Optional.ofNullable(identifierOrNull(clientSnapshot.connectedHostId()));
        MachineStateSnapshot state = localState();
        if (state == null) return moduleConnected.get() == 0 ? Optional.empty() : Optional.ofNullable(clientConnectedHostId);
        return state.moduleConnected() ? Optional.ofNullable(identifierOrNull(state.connectedHostId())) : Optional.empty();
    }

    public boolean isHostController() {
        return controllerRoleValue() == 1;
    }

    public boolean isModuleController() {
        return controllerRoleValue() == 2;
    }

    static int resolvedControllerRole(int localRole, int syncedRole, int initialRole) {
        return localRole != 0 ? localRole : syncedRole != 0 ? syncedRole : initialRole;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (uiServerSession != null) {
            uiServerSession.broadcastChanges();
            return;
        }
        if (owner == null || serverPlayer == null) return;
        PktMachineStatePayload next = PktMachineStatePayload.from(pos, owner.runtimeSnapshot(),
                owner.currentRecipePoolId());
        var update = PktMachineStatePayload.nextUpdate(next, lastSentSnapshot);
        if (update == null) return;
        serverPlayer.connection.send(new ClientboundCustomPayloadPacket(update));
        lastSentSnapshot = next;
    }

    public void applyClientProgress(String recipeName, int tick, int totalTick) {
        if (clientSnapshot == null || !clientSnapshot.recipeName().equals(recipeName)
                || clientSnapshot.totalTick() != totalTick) return;
        clientSnapshot = clientSnapshot.withProgress(tick, totalTick);
        activeTick.set(tick);
        activeTotalTick.set(totalTick);
    }

    public void applyClientSnapshot(PktMachineStatePayload snapshot) {
        this.clientSnapshot = snapshot;
        this.clientMachineId = identifierOrNull(snapshot.machineId());
        this.clientControllerRole = snapshot.controllerRole();
        this.clientConnectedHostId = identifierOrNull(snapshot.connectedHostId());
        this.formed.set(snapshot.formed() ? 1 : 0);
        this.active.set(snapshot.active() ? 1 : 0);
        this.activeTick.set(snapshot.tick());
        this.activeTotalTick.set(snapshot.totalTick());
        this.redstonePaused.set(snapshot.redstonePaused() ? 1 : 0);
        this.factoryControllerPresent.set(snapshot.factoryControllerPresent() ? 1 : 0);
        this.factoryThreadCount.set(snapshot.factoryThreadCount());
        this.factoryActiveThreadCount.set(snapshot.activeFactoryThreadCount());
        this.parallelControllerCount.set(snapshot.parallelControllerCount());
        this.installedModuleCount.set(Math.max(0, snapshot.installedModuleCount()));
        this.moduleConnected.set(snapshot.moduleConnected() && this.clientConnectedHostId != null ? 1 : 0);
        this.controllerRole.set(snapshot.controllerRole());
    }

    public List<String> foundLevelIds() {
        if (clientSnapshot != null) return clientSnapshot.foundLevelIds();
        MachineStateSnapshot state = localState();
        return state == null ? List.of() : state.foundLevelIds();
    }

    public BlockPos controllerPos() { return pos; }

    public int matchedStage() {
        if (clientSnapshot != null) return clientSnapshot.matchedStage();
        MachineStateSnapshot state = localState();
        return state == null ? 0 : state.matchedStage();
    }

    public int stageCount() {
        if (clientSnapshot != null) return clientSnapshot.stageCount();
        MachineStateSnapshot state = localState();
        return state == null ? 1 : state.stageCount();
    }

    public static int controllerRoleSyncValue(MachineControllerBlockEntity controller) {
        return controller == null ? 0 : SYNC_RUNTIME.machineState(controller.runtimeSnapshot()).controllerRole();
    }

    private static @Nullable ResourceLocation machineIdFor(@Nullable MachineControllerBlockEntity controller) {
        return controller == null ? null : identifierOrNull(SYNC_RUNTIME.machineState(controller.runtimeSnapshot()).machineId());
    }

    private @Nullable MachineStateSnapshot localState() {
        MachineControllerBlockEntity controller = resolvedOwner();
        return controller == null ? null : machineState(controller);
    }

    private int controllerRoleValue() {
        if (clientSnapshot != null) return clientSnapshot.controllerRole();
        MachineStateSnapshot state = localState();
        return state == null ? resolvedControllerRole(0, controllerRole.get(), clientControllerRole) : state.controllerRole();
    }

    private static MachineStateSnapshot machineState(MachineControllerBlockEntity controller) {
        return SYNC_RUNTIME.machineState(controller.runtimeSnapshot(), controller.currentRecipePoolId());
    }

    private static @Nullable ResourceLocation identifierOrNull(String value) {
        return value == null || value.isEmpty() ? null : ResourceLocation.parse(value);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return MenuSupport.noopQuickMove();
    }

    @Override
    public boolean stillValid(Player player) {
        if (owner == null) return true;
        if (!MenuSupport.stillValidWithin(player, owner.getBlockPos())) return false;
        return !wasFormedDuringSession() || MenuSupport.controllerStillPresentAndFormed(owner);
    }
}
