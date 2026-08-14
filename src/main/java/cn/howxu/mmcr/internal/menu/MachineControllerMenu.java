package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.internal.recipe.FactoryRecipeScheduler;
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

import java.util.Optional;

public class MachineControllerMenu extends AbstractMachineMenu {

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

    public MachineControllerMenu(int containerId, Inventory playerInv, MachineControllerBlockEntity owner) {
        super(ModUIs.MACHINE_CONTROLLER.get(), containerId);
        this.owner = owner;
        this.level = playerInv.player == null ? null : playerInv.player.level();
        this.pos = owner == null ? BlockPos.ZERO : owner.getBlockPos();
        wasFormedDuringSession = owner != null && owner.isFormed();
        this.formed = addDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return owner.isFormed() ? 1 : 0; }
            @Override public void set(int value) {}
        });
        this.active = addDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return owner.isRuntimeActive() ? 1 : 0; }
            @Override public void set(int value) {}
        });
        this.activeTick = addDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return activeRecipeTick(owner); }
            @Override public void set(int value) {}
        });
        this.activeTotalTick = addDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return activeRecipeTotalTick(owner); }
            @Override public void set(int value) {}
        });
        this.lastFailure = addDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return failureCode(owner.getLastFailureUnloc()); }
            @Override public void set(int value) {}
        });
        this.redstonePaused = addDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return owner.isRedstonePaused() ? 1 : 0; }
            @Override public void set(int value) {}
        });
        this.parallelControllerCount = addDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return owner.parallelControllerCount(); }
            @Override public void set(int value) {}
        });
        this.currentParallelism = addDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return owner.currentParallelism(); }
            @Override public void set(int value) {}
        });
        this.maxParallelism = addDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return owner.getMaxParallelism(); }
            @Override public void set(int value) {}
        });
        this.factoryControllerPresent = addDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return owner.getFactoryController() == null ? 0 : 1; }
            @Override public void set(int value) {}
        });
        this.factoryThreadCount = addDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() {
                var factory = owner.getFactoryController();
                return factory == null ? 0 : factory.threadCount();
            }
            @Override public void set(int value) {}
        });
        this.factoryActiveThreadCount = addDataSlot(owner == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() {
                var factory = owner.getFactoryController();
                return factory == null ? 0 : factory.activeThreadCount();
            }
            @Override public void set(int value) {}
        });
        this.recipeLocked = ControllerMenuState.addRecipeLockSlot(this, owner);
        this.installedModuleCount = ControllerMenuState.addInstalledModuleCountSlot(this, owner);
        this.moduleConnected = ControllerMenuState.addModuleConnectedSlot(this, owner);
        this.controllerRole = ControllerMenuState.addControllerRoleSlot(this, owner);
        this.clientControllerRole = controllerRoleSyncValue(owner);
        this.clientMachineId = machineIdFor(owner);
        this.clientConnectedHostId = owner == null ? null : owner.connectedHostId().orElse(null);
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
        MachineControllerBlockEntity controller = resolvedOwner();
        Machine machine = controller == null ? null : controller.getMachine();
        Identifier id = machine == null ? null : machine.registryName();
        return id == null ? clientMachineId : id;
    }

    public boolean isFormed() {
        MachineControllerBlockEntity controller = resolvedOwner();
        return controller == null ? formed.get() != 0 : controller.isFormed();
    }

    boolean wasFormedDuringSession() {
        if (owner != null && owner.isFormed()) wasFormedDuringSession = true;
        return wasFormedDuringSession;
    }

    public boolean hasActiveRecipe() {
        MachineControllerBlockEntity controller = resolvedOwner();
        return controller == null ? active.get() != 0 : controller.isRuntimeActive() || controller.hasClientActiveRecipe()
                || activeFactoryThread(controller) != null || active.get() != 0;
    }

    public int activeRecipeTick() {
        MachineControllerBlockEntity controller = resolvedOwner();
        return owner == null ? activeTick.get() : activeRecipeTick(controller);
    }

    public int activeRecipeTotalTick() {
        MachineControllerBlockEntity controller = resolvedOwner();
        return owner == null ? activeTotalTick.get() : activeRecipeTotalTick(controller);
    }

    private static int activeRecipeTick(@Nullable MachineControllerBlockEntity controller) {
        if (controller == null) return 0;
        if (controller.getActiveRecipe() != null) return controller.getTickCounter();
        FactoryRecipeScheduler.ThreadSnapshot thread = activeFactoryThread(controller);
        return thread == null ? 0 : thread.tick();
    }

    private static int activeRecipeTotalTick(@Nullable MachineControllerBlockEntity controller) {
        if (controller == null) return 0;
        if (controller.getActive() != null) return controller.getActive().getTotalTick();
        FactoryRecipeScheduler.ThreadSnapshot thread = activeFactoryThread(controller);
        return thread == null ? 0 : thread.totalTick();
    }

    private static @Nullable FactoryRecipeScheduler.ThreadSnapshot activeFactoryThread(MachineControllerBlockEntity controller) {
        var factory = controller.getFactoryController();
        if (factory == null) return null;
        return factory.threadSnapshots(controller).stream()
                .filter(FactoryRecipeScheduler.ThreadSnapshot::active)
                .findFirst()
                .orElse(null);
    }

    public @Nullable String lastFailureMessage() {
        MachineControllerBlockEntity controller = resolvedOwner();
        if (controller != null && controller.getLastFailureUnloc() != null) return controller.getLastFailureUnloc();
        return failureKey(lastFailure.get());
    }

    public boolean isRedstonePaused() {
        MachineControllerBlockEntity controller = resolvedOwner();
        return (controller != null && controller.isRedstonePaused()) || redstonePaused.get() != 0;
    }

    public int parallelControllerCount() {
        if (owner == null) return parallelControllerCount.get();
        MachineControllerBlockEntity controller = resolvedOwner();
        return controller == null ? parallelControllerCount.get() : controller.parallelControllerCount();
    }

    public int currentParallelism() {
        if (owner == null) return currentParallelism.get();
        MachineControllerBlockEntity controller = resolvedOwner();
        return controller == null ? currentParallelism.get() : controller.currentParallelism();
    }

    public int maxParallelism() {
        if (owner == null) return Math.max(1, maxParallelism.get());
        MachineControllerBlockEntity controller = resolvedOwner();
        return controller == null ? Math.max(1, maxParallelism.get()) : controller.getMaxParallelism();
    }

    public boolean hasFactoryController() {
        if (owner == null) return factoryControllerPresent.get() != 0;
        MachineControllerBlockEntity controller = resolvedOwner();
        return controller == null ? factoryControllerPresent.get() != 0 : controller.getFactoryController() != null;
    }

    public int factoryThreadCount() {
        if (owner == null) return factoryThreadCount.get();
        var factory = owner.getFactoryController();
        return factory == null ? 0 : factory.threadCount();
    }

    public int factoryActiveThreadCount() {
        if (owner == null) return factoryActiveThreadCount.get();
        var factory = owner.getFactoryController();
        return factory == null ? 0 : factory.activeThreadCount();
    }

    public boolean recipeLocked() {
        if (owner == null && level != null && level.getBlockEntity(pos) instanceof MachineControllerBlockEntity controller
                && controller.hasClientRecipeLock()) return true;
        if (owner == null) return recipeLocked.get() != 0;
        MachineControllerBlockEntity controller = resolvedOwner();
        var factory = controller.getFactoryController();
        if (factory == null) return controller.recipeLocked();
        return factory.threadSnapshots(controller).stream().findFirst()
                .map(FactoryRecipeScheduler.ThreadSnapshot::locked).orElse(false);
    }

    public int installedModuleCount() {
        if (owner == null) return installedModuleCount.get();
        MachineControllerBlockEntity controller = resolvedOwner();
        return controller == null ? installedModuleCount.get() : controller.installedModuleCount();
    }

    public Optional<Identifier> connectedHostId() {
        if (owner == null) return moduleConnected.get() == 0 ? Optional.empty() : Optional.ofNullable(clientConnectedHostId);
        MachineControllerBlockEntity controller = resolvedOwner();
        return controller == null ? Optional.ofNullable(clientConnectedHostId) : controller.connectedHostId();
    }

    public boolean isHostController() {
        MachineControllerBlockEntity controller = resolvedOwner();
        if (controller != null) return controllerRoleSyncValue(controller) == 1;
        return controllerRole.get() == 1 || clientControllerRole == 1;
    }

    public boolean isModuleController() {
        MachineControllerBlockEntity controller = resolvedOwner();
        if (controller != null) return controllerRoleSyncValue(controller) == 2;
        return controllerRole.get() == 2 || clientControllerRole == 2;
    }

    public void applyModuleStatus(int installedModuleCount, boolean moduleConnected, @Nullable Identifier connectedHostId) {
        this.installedModuleCount.set(Math.max(0, installedModuleCount));
        this.moduleConnected.set(moduleConnected && connectedHostId != null ? 1 : 0);
        this.clientConnectedHostId = moduleConnected ? connectedHostId : null;
    }

    public void applyClientControllerState(@Nullable Identifier machineId, int controllerRole, int installedModuleCount,
                                           boolean moduleConnected, @Nullable Identifier connectedHostId) {
        this.clientMachineId = machineId;
        this.clientControllerRole = controllerRole;
        this.controllerRole.set(controllerRole);
        applyModuleStatus(installedModuleCount, moduleConnected, connectedHostId);
    }

    public @Nullable String lockedRecipeId() {
        MachineControllerBlockEntity controller = resolvedOwner();
        if (controller == null) return null;
        if (owner == null && controller.hasClientRecipeLock()) return controller.clientLockedRecipeId();
        var factory = controller.getFactoryController();
        if (factory == null) return controller.lockedRecipeId() == null ? null : controller.lockedRecipeId().toString();
        return factory.threadSnapshots(controller).stream().findFirst()
                .map(FactoryRecipeScheduler.ThreadSnapshot::lockedRecipeId).filter(id -> !id.isEmpty()).orElse(null);
    }

    public BlockPos controllerPos() { return pos; }

    public static int controllerRoleSyncValue(MachineControllerBlockEntity controller) {
        Machine machine = controller == null ? null : controller.getMachine();
        if (machine == null && controller != null) machine = controller.getFoundMachine();
        if (machine == null) return 0;
        if (machine.isHost()) return 1;
        if (machine.isModule()) return 2;
        return 0;
    }

    static int controllerRoleSyncValue(@Nullable Identifier id) {
        Machine machine = id == null ? null : MachineRegistry.getMachine(id);
        if (machine == null) return 0;
        if (machine.isHost()) return 1;
        if (machine.isModule()) return 2;
        return 0;
    }

    private static @Nullable Identifier machineIdFor(@Nullable MachineControllerBlockEntity controller) {
        Machine machine = controller == null ? null : controller.getMachine();
        return machine == null ? null : machine.registryName();
    }

    private static void writeOptionalIdentifier(RegistryFriendlyByteBuf buf, @Nullable Identifier id) {
        buf.writeBoolean(id != null);
        if (id != null) Identifier.STREAM_CODEC.encode(buf, id);
    }

    private static @Nullable Identifier readOptionalIdentifier(FriendlyByteBuf buf) {
        return buf.readBoolean() ? Identifier.STREAM_CODEC.decode(buf) : null;
    }

    public long totalStoredEnergy() {
        MachineControllerBlockEntity controller = resolvedOwner();
        return controller == null ? 0L : controller.totalStoredEnergy();
    }

    public long totalCapacityEnergy() {
        MachineControllerBlockEntity controller = resolvedOwner();
        return controller == null ? 0L : controller.totalCapacityEnergy();
    }

    public FluidStack primaryFluid() {
        MachineControllerBlockEntity controller = resolvedOwner();
        return controller == null ? FluidStack.EMPTY : controller.primaryFluid();
    }

    public FluidStack primaryOutputFluid() {
        MachineControllerBlockEntity controller = resolvedOwner();
        return controller == null ? FluidStack.EMPTY : controller.primaryOutputFluid();
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
