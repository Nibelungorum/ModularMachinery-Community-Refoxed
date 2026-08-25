package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.runtime.ControllerSyncRuntime;
import cn.howxu.mmcr.internal.runtime.MachineStateSnapshot;
import cn.howxu.mmcr.internal.network.PktMachineStatePayload;
import cn.howxu.mmcr.registry.ModUIs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class MachineControllerMenu extends AbstractMachineMenu {
    private static final ControllerSyncRuntime SYNC_RUNTIME = new ControllerSyncRuntime();

    private final MachineControllerBlockEntity owner;
    private final Level level;
    private boolean wasFormedDuringSession;
    private final BlockPos pos;
    private final DataSlot formed;
    private final DataSlot active;
    private final DataSlot activeTick;
    private final DataSlot activeTotalTick;
    private final DataSlot lastFailure;
    private final DataSlot redstonePaused;
    private final DataSlot parallelControllerCount;
    private final DataSlot currentParallelism;
    private final DataSlot maxParallelism;
    private final DataSlot factoryControllerPresent;
    private final DataSlot factoryThreadCount;
    private final DataSlot factoryActiveThreadCount;
    private final DataSlot recipeLocked;
    private final DataSlot installedModuleCount;
    private final DataSlot moduleConnected;
    private final DataSlot controllerRole;
    private int clientControllerRole;
    private @Nullable Identifier clientMachineId;
    private @Nullable Identifier clientConnectedHostId;
    private @Nullable PktMachineStatePayload clientSnapshot;

    public MachineControllerMenu(int containerId, Inventory playerInv, MachineControllerBlockEntity owner) {
        super(ModUIs.MACHINE_CONTROLLER.get(), containerId);
        this.owner = owner;
        this.level = playerInv.player == null ? null : playerInv.player.level();
        this.pos = owner == null ? BlockPos.ZERO : owner.getBlockPos();
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
        this.lastFailure = addDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return failureCode(SYNC_RUNTIME.failureMessage(machineState(owner).failure())); }
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
        this.currentParallelism = addDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return machineState(owner).parallelism(); }
            @Override public void set(int value) {}
        });
        this.maxParallelism = addDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return machineState(owner).maxParallelism(); }
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
        this.recipeLocked = ControllerMenuState.addRecipeLockSlot(this, owner);
        this.installedModuleCount = ControllerMenuState.addInstalledModuleCountSlot(this, owner);
        this.moduleConnected = ControllerMenuState.addModuleConnectedSlot(this, owner);
        this.controllerRole = ControllerMenuState.addControllerRoleSlot(this, owner);
        this.clientControllerRole = controllerRoleSyncValue(owner);
        this.clientMachineId = machineIdFor(owner);
        this.clientConnectedHostId = owner == null ? null
                : machineState(owner).connectedHostId().isEmpty() ? null : Identifier.parse(machineState(owner).connectedHostId());
        addControllerPlayerSlots(playerInv);
    }

    public MachineControllerMenu(int containerId, Inventory playerInv, BlockPos pos, @Nullable Identifier machineId,
                                 @Nullable Identifier connectedHostId, int controllerRole, boolean formed, int installedModuleCount) {
        super(ModUIs.MACHINE_CONTROLLER.get(), containerId);
        this.owner = null;
        this.level = playerInv.player == null ? null : playerInv.player.level();
        this.pos = pos;
        this.formed = addDataSlot(DataSlot.standalone());
        this.active = addDataSlot(DataSlot.standalone());
        this.activeTick = addDataSlot(DataSlot.standalone());
        this.activeTotalTick = addDataSlot(DataSlot.standalone());
        this.lastFailure = addDataSlot(DataSlot.standalone());
        this.redstonePaused = addDataSlot(DataSlot.standalone());
        this.parallelControllerCount = addDataSlot(DataSlot.standalone());
        this.currentParallelism = addDataSlot(DataSlot.standalone());
        this.maxParallelism = addDataSlot(DataSlot.standalone());
        this.factoryControllerPresent = addDataSlot(DataSlot.standalone());
        this.factoryThreadCount = addDataSlot(DataSlot.standalone());
        this.factoryActiveThreadCount = addDataSlot(DataSlot.standalone());
        this.recipeLocked = addDataSlot(DataSlot.standalone());
        this.installedModuleCount = addDataSlot(DataSlot.standalone());
        this.moduleConnected = addDataSlot(DataSlot.standalone());
        this.controllerRole = addDataSlot(DataSlot.standalone());
        this.clientControllerRole = controllerRole;
        this.clientMachineId = machineId;
        this.clientConnectedHostId = connectedHostId;
        this.formed.set(formed ? 1 : 0);
        this.installedModuleCount.set(Math.max(0, installedModuleCount));
        this.moduleConnected.set(connectedHostId == null ? 0 : 1);
        this.controllerRole.set(controllerRole);
        addControllerPlayerSlots(playerInv);
    }

    public MachineControllerMenu(int containerId, Inventory playerInv, BlockPos pos) {
        this(containerId, playerInv, pos, null, null, 0, false, 0);
    }

    private void addControllerPlayerSlots(Inventory playerInv) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 131 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 189));
        }
    }

    public MachineControllerMenu(int containerId, Inventory playerInv) {
        this(containerId, playerInv, (MachineControllerBlockEntity) null);
    }

    public static MachineControllerMenu clientOpen(int containerId, Inventory playerInv) {
        return new MachineControllerMenu(containerId, playerInv);
    }

    public static MachineControllerMenu clientOpen(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        Identifier machineId = readOptionalIdentifier(buf);
        Identifier connectedHostId = readOptionalIdentifier(buf);
        int controllerRole = buf.readVarInt();
        boolean formed = buf.readBoolean();
        int installedModuleCount = buf.readVarInt();
        return new MachineControllerMenu(containerId, playerInv, pos, machineId, connectedHostId, controllerRole, formed, installedModuleCount);
    }

    public static void writeClientOpenData(RegistryFriendlyByteBuf buf, BlockPos pos, @Nullable Identifier machineId,
                                           @Nullable Identifier connectedHostId, int controllerRole, boolean formed,
                                           int installedModuleCount) {
        buf.writeBlockPos(pos);
        writeOptionalIdentifier(buf, machineId);
        writeOptionalIdentifier(buf, connectedHostId);
        buf.writeVarInt(controllerRole);
        buf.writeBoolean(formed);
        buf.writeVarInt(Math.max(0, installedModuleCount));
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

    public @Nullable Identifier machineId() {
        if (clientSnapshot != null) return identifierOrNull(clientSnapshot.machineId());
        MachineStateSnapshot state = localState();
        return state == null ? clientMachineId : identifierOrNull(state.machineId());
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

    public @Nullable String lastFailureMessage() {
        if (clientSnapshot != null) {
            String failure = SYNC_RUNTIME.failureMessage(clientSnapshot.failure());
            return failure.isEmpty() ? failureKey(lastFailure.get()) : failure;
        }
        MachineStateSnapshot state = localState();
        if (state != null) return SYNC_RUNTIME.failureMessage(state.failure());
        return failureKey(lastFailure.get());
    }

    public boolean isRedstonePaused() {
        if (clientSnapshot != null) return clientSnapshot.redstonePaused();
        MachineStateSnapshot state = localState();
        return state == null ? redstonePaused.get() != 0 : state.redstonePaused();
    }

    public int parallelControllerCount() {
        if (clientSnapshot != null) return parallelControllerCount.get();
        MachineStateSnapshot state = localState();
        return state == null ? parallelControllerCount.get() : state.parallelControllerCount();
    }

    public int currentParallelism() {
        if (clientSnapshot != null) return clientSnapshot.parallelism();
        MachineStateSnapshot state = localState();
        return state == null ? currentParallelism.get() : state.parallelism();
    }

    public int maxParallelism() {
        if (clientSnapshot != null) return clientSnapshot.maxParallelism();
        MachineStateSnapshot state = localState();
        return state == null ? Math.max(1, maxParallelism.get()) : state.maxParallelism();
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

    public boolean recipeLocked() {
        if (clientSnapshot != null) return clientSnapshot.recipeLocked();
        MachineStateSnapshot state = localState();
        return state == null ? recipeLocked.get() != 0 : state.recipeLocked();
    }

    public int installedModuleCount() {
        if (clientSnapshot != null) return clientSnapshot.installedModuleCount();
        MachineStateSnapshot state = localState();
        return state == null ? installedModuleCount.get() : state.installedModuleCount();
    }

    public Optional<Identifier> connectedHostId() {
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

    public void applyClientSnapshot(PktMachineStatePayload snapshot) {
        this.clientSnapshot = snapshot;
        this.clientMachineId = identifierOrNull(snapshot.machineId());
        this.clientControllerRole = snapshot.controllerRole();
        this.clientConnectedHostId = identifierOrNull(snapshot.connectedHostId());
        this.formed.set(snapshot.formed() ? 1 : 0);
        this.active.set(snapshot.active() ? 1 : 0);
        this.activeTick.set(snapshot.tick());
        this.activeTotalTick.set(snapshot.totalTick());
        this.lastFailure.set(failureCode(SYNC_RUNTIME.failureMessage(snapshot.failure())));
        this.redstonePaused.set(snapshot.redstonePaused() ? 1 : 0);
        this.currentParallelism.set(snapshot.parallelism());
        this.maxParallelism.set(snapshot.maxParallelism());
        this.factoryControllerPresent.set(snapshot.factoryControllerPresent() ? 1 : 0);
        this.factoryThreadCount.set(snapshot.factoryThreadCount());
        this.factoryActiveThreadCount.set(snapshot.activeFactoryThreadCount());
        this.parallelControllerCount.set(snapshot.parallelControllerCount());
        this.recipeLocked.set(snapshot.recipeLocked() ? 1 : 0);
        this.installedModuleCount.set(Math.max(0, snapshot.installedModuleCount()));
        this.moduleConnected.set(snapshot.moduleConnected() && this.clientConnectedHostId != null ? 1 : 0);
        this.controllerRole.set(snapshot.controllerRole());
    }

    public @Nullable String lockedRecipeId() {
        if (clientSnapshot != null) return clientSnapshot.recipeLocked() ? clientSnapshot.lockedRecipeId() : null;
        MachineStateSnapshot state = localState();
        if (state == null) return null;
        String lockedRecipe = state.lockedRecipeId();
        return lockedRecipe.isEmpty() ? null : lockedRecipe;
    }

    public List<String> foundLevelIds() {
        if (clientSnapshot != null) return clientSnapshot.foundLevelIds();
        MachineStateSnapshot state = localState();
        return state == null ? List.of() : state.foundLevelIds();
    }

    public BlockPos controllerPos() { return pos; }

    public static int controllerRoleSyncValue(MachineControllerBlockEntity controller) {
        return controller == null ? 0 : SYNC_RUNTIME.machineState(controller.runtimeSnapshot()).controllerRole();
    }

    private static @Nullable Identifier machineIdFor(@Nullable MachineControllerBlockEntity controller) {
        return controller == null ? null : identifierOrNull(SYNC_RUNTIME.machineState(controller.runtimeSnapshot()).machineId());
    }

    private static void writeOptionalIdentifier(RegistryFriendlyByteBuf buf, @Nullable Identifier id) {
        buf.writeBoolean(id != null);
        if (id != null) Identifier.STREAM_CODEC.encode(buf, id);
    }

    private static @Nullable Identifier readOptionalIdentifier(FriendlyByteBuf buf) {
        return buf.readBoolean() ? Identifier.STREAM_CODEC.decode(buf) : null;
    }

    public long totalStoredEnergy() {
        if (clientSnapshot != null) return clientSnapshot.totalStoredEnergy();
        MachineStateSnapshot state = localState();
        return state == null ? 0L : state.totalStoredEnergy();
    }

    public long totalCapacityEnergy() {
        if (clientSnapshot != null) return clientSnapshot.totalCapacityEnergy();
        MachineStateSnapshot state = localState();
        return state == null ? 0L : state.totalCapacityEnergy();
    }

    public FluidStack primaryFluid() {
        if (clientSnapshot != null) return clientSnapshot.primaryFluid();
        MachineStateSnapshot state = localState();
        return state == null ? FluidStack.EMPTY : state.primaryFluid();
    }

    public FluidStack primaryOutputFluid() {
        if (clientSnapshot != null) return clientSnapshot.primaryOutputFluid();
        MachineStateSnapshot state = localState();
        return state == null ? FluidStack.EMPTY : state.primaryOutputFluid();
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
        return SYNC_RUNTIME.machineState(controller.runtimeSnapshot());
    }

    private static @Nullable Identifier identifierOrNull(String value) {
        return value == null || value.isEmpty() ? null : Identifier.parse(value);
    }

    private static int failureCode(@Nullable String key) {
        if ("gui.mmcr.controller.failure.missing_input".equals(key)) return 1;
        if ("gui.mmcr.controller.failure.missing_output".equals(key)) return 2;
        if ("gui.mmcr.controller.failure.missing_energy".equals(key)) return 3;
        if ("gui.mmcr.controller.failure.level_insufficient".equals(key)) return 4;
        return 0;
    }

    private static @Nullable String failureKey(int code) {
        return switch (code) {
            case 1 -> "gui.mmcr.controller.failure.missing_input";
            case 2 -> "gui.mmcr.controller.failure.missing_output";
            case 3 -> "gui.mmcr.controller.failure.missing_energy";
            case 4 -> "gui.mmcr.controller.failure.level_insufficient";
            default -> null;
        };
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
