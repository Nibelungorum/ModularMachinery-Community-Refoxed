package cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.me.storage.NullInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import cn.howxu.mmcr.MMCR;
import appeng.api.stacks.AEItemKey;
import appeng.api.util.AECableType;
import appeng.core.definitions.AEBlocks;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.me.helpers.BlockEntityNodeListener;
import appeng.me.helpers.IGridConnectedBlockEntity;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.PatternInterfaceCraftingMachine;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.PendingPatternQueue;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2NativeAdapters;
import cn.howxu.mmcr.compat.appmek.AppMekBridge;
import appeng.api.crafting.IPatternDetails;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.HostedPatternDispatcher;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.HostedPatternInventory;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.PatternLogicKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu.AE2MenuTypes;
import cn.howxu.mmcr.compat.extendedae.ExtendedAEContributor;
import cn.howxu.mmcr.compat.extendedae.ExtendedAEContributorBootstrap;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.runtime.PatternStartReservation;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Nameable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MMCR IO port hosting AE2's native pattern-provider logic.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PatternInterfaceBlockEntity extends IOPortBlockEntity
        implements MemoryCardHost, PatternProviderLogicHost, IGridConnectedBlockEntity, PatternInterfaceHost, Nameable {
    private static final IGridNodeListener<PatternInterfaceBlockEntity> NODE_LISTENER =
            new BlockEntityNodeListener<>() {
                @Override
                public void onGridChanged(PatternInterfaceBlockEntity host, IGridNode node) {
                    host.onNetworkChanged();
                }
            };

    private final IOPortKind kind;
    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this, NODE_LISTENER)
            .setInWorldNode(true);
    private final PatternProviderLogic logic;
    private final PatternInterfaceCraftingMachine craftingMachine = new PatternInterfaceCraftingMachine(this);
    private final AtomicInteger nextPatternController = new AtomicInteger();
    private final IItemHandler itemHandler;
    private final IFluidHandler fluidHandler;
    private final CapabilitySnapshot capabilitySnapshot;
    private final PendingPatternQueue pendingPatterns = new PendingPatternQueue();
    private long lastLegacyRefundTick = Long.MIN_VALUE;
    private @Nullable GenericStackInv legacyReturns;
    private final HostedPatternInventory hosted = new HostedPatternInventory(this::onHostedChange);
    private final HostedPatternDispatcher dispatcher = new HostedPatternDispatcher(this);
    private Set<AEItemKey> publishedDefinitions = Set.of();
    private boolean dismantled;
    private @Nullable Component customName;

    @Override
    public Component getName() {
        return customName != null ? customName : Component.translatable("container.mmcr." + kind.id());
    }

    @Override
    public @Nullable Component getCustomName() {
        return customName;
    }

    public void setCustomName(@Nullable Component name) {
        if (((PatternLogicKind) kind).readOnlyPatterns()) return;
        customName = name == null || name.getString().isBlank() ? null : name.copy();
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public PendingPatternQueue pendingPatterns() { return pendingPatterns; }

    public HostedPatternInventory hostedPatterns() { return hosted; }

    public HostedPatternDispatcher hostedDispatcher() { return dispatcher; }

    public boolean hostedOnline() {
        return !dismantled && !isRemoved() && level instanceof ServerLevel && mainNode.isActive();
    }

    public boolean acceptingHostedInputs() {
        return hostedOnline() && !dispatcher.working() && pendingPatterns.isEmpty() && legacyReturns == null;
    }

    public List<MachineControllerBlockEntity> hostedControllers() {
        if (level == null) return List.of();
        return linkedControllerPositions().stream().sorted(BlockPos::compareTo).filter(level::hasChunkAt)
                .map(level::getBlockEntity).filter(MachineControllerBlockEntity.class::isInstance)
                .map(MachineControllerBlockEntity.class::cast).toList();
    }

    public void syncHostedPatterns(List<IPatternDetails> present) {
        if (dismantled) return;
        Set<AEItemKey> current = new HashSet<>();
        for (var pattern : present) current.add(pattern.getDefinition());
        if (!hosted.isEmpty() && !current.containsAll(publishedDefinitions)) {
            Set<AEItemKey> removed = new HashSet<>(publishedDefinitions);
            removed.removeAll(current);
            hosted.retire(removed);
        }
        publishedDefinitions = current;
    }

    public void dispatchHostedPatterns() {
        if (isRemoved() || dismantled || dispatcher.working() || hosted.isEmpty() || !(level instanceof ServerLevel)) return;
        dispatcher.run(level.getGameTime());
        if (hostedOnline()) hosted.refund(networkStorage(), IActionSource.ofMachine(this), false);
    }

    private void onHostedChange() {
        // Escrow is not an ordinary capability. Waking ordinary inputs here would invalidate
        // a controller's rejection merely because its own probe closed the admission gate.
        setChanged();
        if (level instanceof ServerLevel server) HostedPatternDispatcher.enqueue(server, worldPosition);
    }

    public PatternInterfaceBlockEntity(BlockPos pos, BlockState state, IOPortKind kind) {
        super(typeForKind(kind), pos, state);
        this.kind = kind;
        PatternLogicKind logicKind = (PatternLogicKind) kind;
        logic = logicKind.createPatternLogic(mainNode, this);
        AppMekBridge.get().configureInventory(logic.getReturnInv());
        itemHandler = AE2NativeAdapters.items(logic.getReturnInv());
        fluidHandler = AE2NativeAdapters.fluids(logic.getReturnInv());
        capabilitySnapshot = new CapabilitySnapshot(kind.definition().bindings().stream()
                .map(this::createCapability)
                .toList());
    }

    @Override
    public IOType ioType() {
        return IOType.INPUT;
    }

    @Override
    public IOPortKind kind() {
        return kind;
    }

    @Override
    public IManagedGridNode getMainNode() {
        return mainNode;
    }

    @Override
    public PatternProviderLogic getLogic() {
        return logic;
    }

    @Override
    public boolean isVisibleInTerminal() {
        return !((PatternLogicKind) kind).readOnlyPatterns()
                && PatternProviderLogicHost.super.isVisibleInTerminal();
    }

    @Override
    public Component memoryCardSettingsSource() {
        return getMainMenuIcon().getHoverName();
    }

    @Override
    public void exportMemoryCardSettings(DataComponentMap.Builder builder, @Nullable Player player) {
        if (((PatternLogicKind) kind).readOnlyPatterns()) return;
        MemoryCardHost.super.exportMemoryCardSettings(builder, player);
        logic.exportSettings(builder);
    }

    @Override
    public void importMemoryCardSettings(DataComponentMap input, @Nullable Player player) {
        if (((PatternLogicKind) kind).readOnlyPatterns()) return;
        MemoryCardHost.super.importMemoryCardSettings(input, player);
        logic.importSettings(input, player);
    }

    public PatternInterfaceCraftingMachine craftingMachine() {
        return craftingMachine;
    }

    /** Reserves the next eligible linked controller without participating in AE2 provider priority. */
    public PatternStartReservation reservePatternStart(List<MachineOutput> patternOutputs,
                                                       List<MachineCapability> requestCapabilities) {
        if (level == null) return PatternStartReservation.unavailable();
        return MachineControllerBlockEntity.reserveNextPatternStart(hostedControllers(), nextPatternController,
                getBlockPos(), patternOutputs, requestCapabilities);
    }

    @Override
    public BlockEntity getBlockEntity() {
        return this;
    }

    @Override
    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(AE2MenuTypes.typeFor(kind), player, locator);
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        MenuOpener.returnTo(AE2MenuTypes.typeFor(kind), player, subMenu.getLocator());
    }

    @Override
    public EnumSet<Direction> getTargets() {
        return EnumSet.allOf(Direction.class);
    }

    @Override
    public AEItemKey getTerminalIcon() {
        return AEItemKey.of(AEBlocks.PATTERN_PROVIDER.stack());
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        if (((PatternLogicKind) kind).readOnlyPatterns()) return PatternProviderLogicHost.super.getTerminalGroup();
        // Keep the display identity separate from getTerminalIcon(), also used by EAEP for upgrade compatibility.
        return new PatternContainerGroup(AEItemKey.of(getBlockState().getBlock().asItem()), getName(), List.of());
    }

    @Override
    public ItemStack getMainMenuIcon() {
        ExtendedAEContributor contributor = ExtendedAEContributorBootstrap.contributor();
        ItemStack icon = contributor.mainMenuIcon(kind);
        if (icon != null) return icon;
        ItemStack fallback = AEBlocks.PATTERN_PROVIDER.stack();
        fallback.set(DataComponents.CUSTOM_NAME, Component.translatable("container.mmcr." + kind.id()));
        return fallback;
    }

    @Override
    public void saveChanges() {
        notifyStorageChanged();
    }

    @Override
    public AECableType getCableConnectionType(Direction direction) {
        return AECableType.SMART;
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        onNetworkChanged();
    }

    @Override
    public IItemHandler nativeItemHandler() {
        return itemHandler;
    }

    @Override
    public IFluidHandler nativeFluidHandler() {
        return fluidHandler;
    }

    /** Notifies linked controllers after AE2's native return inventory drains. */
    public void onNativeReturnInventoryDrained() {
        notifyStorageChanged();
    }

    @Override
    protected void tick() {
        super.tick();
        if (!hosted.isEmpty() && level instanceof ServerLevel server) {
            HostedPatternDispatcher.enqueue(server, worldPosition);
        }
        refundLegacyInputs();
    }

    /** Old escrow is refunded without requiring an active grid, never used to start a recipe. */
    public void refundLegacyInputs() {
        if (level == null || level.isClientSide() || isRemoved() || pendingPatterns.isEmpty() && legacyReturns == null) return;
        if (lastLegacyRefundTick == level.getGameTime()) return;
        lastLegacyRefundTick = level.getGameTime();
        pendingPatterns.rebaseClock(level.getGameTime());
        try {
            if (legacyReturns != null) refundLegacyReturns();
            pendingPatterns.refundNext(this);
        } catch (RuntimeException exception) {
            MMCR.LOG.warn("Native legacy pattern refund notification interrupted at {}", getBlockPos(), exception);
        }
    }

    /** Only loaded for the previous native handoff format. Both owners change before notification. */
    private void refundLegacyReturns() {
        var returns = logic.getReturnInv();
        returns.beginBatch();
        try {
            for (int slot = 0; slot < legacyReturns.size(); slot++) {
                var stack = legacyReturns.getStack(slot);
                if (stack == null) continue;
                long moved = returns.insert(stack.what(), stack.amount(), Actionable.MODULATE, IActionSource.ofMachine(this));
                if (moved > 0) {
                    legacyReturns.extract(slot, stack.what(), moved, Actionable.MODULATE);
                    setChanged();
                }
            }
            if (legacyReturns.isEmpty()) legacyReturns = null;
        } finally {
            returns.endBatch();
        }
    }

    @Override
    protected void runAutoIOCycle() {
        // Pattern capabilities do not expose TransferFacet; AE2's native logic handles returns.
    }

    @Override
    public CapabilitySnapshot capabilitySnapshot() {
        return capabilitySnapshot;
    }

    @Override
    public void saveAdditional(CompoundTag output, HolderLookup.Provider registries) {
        super.saveAdditional(output, registries);
        if (customName != null) {
            output.putString("CustomName", Component.Serializer.toJson(customName, registries));
        }
        mainNode.saveToNBT(output);
        logic.writeToNBT(output, registries);
        hosted.write(output, registries, level == null ? 0L : level.getGameTime());
        if (legacyReturns != null) legacyReturns.writeToChildTag(output, "pattern_pending_returns", registries);
        if (!pendingPatterns.isEmpty()) pendingPatterns.write(output, registries, level == null ? 0L : level.getGameTime());
    }

    @Override
    protected void loadAdditional(CompoundTag input, HolderLookup.Provider registries) {
        beginLoadingAdditional();
        try {
            super.loadAdditional(input, registries);
            customName = ((PatternLogicKind) kind).readOnlyPatterns() || !input.contains("CustomName", 8)
                    ? null : parseCustomNameSafe(input.getString("CustomName"), registries);
            mainNode.loadFromNBT(input);
            logic.readFromNBT(input, registries);
            hosted.read(input, registries);
            legacyReturns = input.contains("pattern_pending_returns", Tag.TAG_LIST) ? AE2NativeAdapters.restoreRequestInventory(
                    input.getList("pattern_pending_returns", Tag.TAG_COMPOUND), registries) : null;
            pendingPatterns.read(input, registries);
        } finally {
            endLoadingAdditional();
        }
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput components) {
        super.applyImplicitComponents(components);
        customName = ((PatternLogicKind) kind).readOnlyPatterns() ? null : components.get(DataComponents.CUSTOM_NAME);
    }

    @Override
    public void removeComponentsFromTag(CompoundTag output) {
        super.removeComponentsFromTag(output);
        output.remove("CustomName");
        output.remove(HostedPatternInventory.SAVE_KEY);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.CUSTOM_NAME, customName);
        if (level != null && (!pendingPatterns.isEmpty() || !logic.getReturnInv().isEmpty()
                || legacyReturns != null)) {
            CompoundTag data = new CompoundTag();
            if (!pendingPatterns.isEmpty()) pendingPatterns.write(data, level.registryAccess(), level.getGameTime());
            data.put(PatternProviderLogic.NBT_RETURN_INV, logic.getReturnInv().writeToTag(level.registryAccess()));
            if (legacyReturns != null) legacyReturns.writeToChildTag(data, "pattern_pending_returns", level.registryAccess());
            data.putString("id", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(getType()).toString());
            components.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(data));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.remove("pending_patterns");
        tag.remove("pattern_pending_returns");
        tag.remove(HostedPatternInventory.SAVE_KEY);
        return tag;
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        GridHelper.onFirstTick(this, blockEntity -> {
            if (blockEntity.getLevel() != null && !blockEntity.isRemoved() && !blockEntity.dismantled) {
                blockEntity.mainNode.create(blockEntity.getLevel(), blockEntity.getBlockPos());
                blockEntity.logic.updatePatterns();
            }
        });
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        mainNode.destroy();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        mainNode.destroy();
    }

    @Override
    public void dropContents() {
        if (dismantled || level == null || level.isClientSide()) return;
        boolean online = hostedOnline();
        dismantled = true;
        hosted.refund(online ? networkStorage() : NullInventory.of(), IActionSource.ofMachine(this), true);
        List<ItemStack> drops = new ArrayList<>();
        logic.addDrops(drops);
        for (ItemStack stack : drops) {
            Block.popResource(level, worldPosition, stack);
        }
        // The native clear mixin retains carrier-owned returns, including when destruction reenters a batch.
        logic.clearContent();
    }

    private void onNetworkChanged() {
        dispatcher.networkChanged();
        if (!hosted.isEmpty()) onHostedChange();
        if (!logic.getReturnInv().isEmpty() || logic.isBusy()) logic.onMainNodeStateChanged();
    }

    private MEStorage networkStorage() {
        IGrid grid = mainNode.getGrid();
        return grid == null ? NullInventory.of() : grid.getStorageService().getInventory();
    }
}
