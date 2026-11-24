package cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import cn.howxu.mmcr.MMCR;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;
import appeng.core.definitions.AEBlocks;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.me.helpers.BlockEntityNodeListener;
import appeng.me.helpers.IGridConnectedBlockEntity;
import appeng.me.storage.NullInventory;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.PatternInterfaceCraftingMachine;
import appeng.api.crafting.IPatternDetails;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.HostedPatternDispatcher;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.HostedPatternInventory;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2ResourceFamilies;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2ResourceFamily;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.PatternLogicKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu.AE2MenuTypes;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.OutputResourceStorage;
import cn.howxu.mmcr.compat.extendedae.ExtendedAEContributor;
import cn.howxu.mmcr.compat.extendedae.ExtendedAEContributorBootstrap;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.PatternRequestResourceStorage;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.PatternReturnResourceStorage;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.runtime.PatternStartReservation;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.PatternRefundIndex;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.Nameable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
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
    private final Map<AE2ResourceFamily<?>, OutputResourceStorage<?>> outputStorages = new LinkedHashMap<>();
    private final Map<AE2ResourceFamily<?>, PatternReturnResourceStorage<?>> returnStorages = new LinkedHashMap<>();
    private final CapabilitySnapshot capabilitySnapshot;
    private boolean returnInventoryNotificationPending;
    private final HostedPatternInventory hosted = new HostedPatternInventory(this::onHostedChange);
    private final HostedPatternDispatcher dispatcher = new HostedPatternDispatcher(this);
    private Set<AEItemKey> publishedDefinitions = Set.of();
    private static final String REFUND_INSTANCE = "hosted_refund_instance";
    private @Nullable UUID refundInstance;
    private @Nullable GlobalPos refundMaster;
    private boolean refundReady;
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

    public HostedPatternInventory hostedPatterns() { return hosted; }

    public HostedPatternDispatcher hostedDispatcher() { return dispatcher; }

    public boolean hostedOnline() {
        return !dismantled && !isRemoved() && level instanceof ServerLevel && mainNode.isActive();
    }

    public boolean acceptingHostedInputs() { return hostedOnline() && refundInitialized() && !dispatcher.working(); }

    private boolean isMirror() { return ((PatternLogicKind) kind).readOnlyPatterns(); }
    private boolean refundInitialized() { return !isMirror() || refundReady; }
    private GlobalPos location(ServerLevel server) { return GlobalPos.of(server.dimension(), worldPosition); }

    public void setRefundMaster(@Nullable GlobalPos master) {
        refundMaster = master;
        if (refundReady && level instanceof ServerLevel server) restoreRefundMember(server);
    }

    private void restoreRefundMember(ServerLevel server) {
        if (!isMirror() || dismantled || isRemoved()) return;
        refundReady = false;
        var index = server.getServer().getDataStorage().get(PatternRefundIndex.TYPE);
        var pos = location(server);
        var old = index == null ? null : index.member(pos);
        if (old != null && old.requested() && old.instance().equals(refundInstance)) {
            markLocalHostedRefund();
            index.acknowledge(pos, old.instance());
        }
        refundReady = true;
        publishRefundMember(server);
    }

    private void publishRefundMember(ServerLevel server) {
        if (!refundReady || dismantled || isRemoved()) return;
        var storage = server.getServer().getDataStorage();
        var pos = location(server);
        if (refundMaster != null && !hosted.isEmpty()) {
            if (refundInstance == null) {
                refundInstance = UUID.randomUUID();
                setChanged();
            }
            storage.computeIfAbsent(PatternRefundIndex.TYPE).observe(pos, refundInstance, refundMaster, true);
        } else {
            var index = storage.get(PatternRefundIndex.TYPE);
            var old = index == null ? null : index.member(pos);
            if (old != null) index.forget(pos, old.instance());
        }
    }

    public boolean hasGroupHostedMaterials() {
        if (!hosted.isEmpty()) return true;
        if (!(level instanceof ServerLevel server) || isMirror()) return false;
        var index = server.getServer().getDataStorage().get(PatternRefundIndex.TYPE);
        return index != null && index.hasMembers(location(server));
    }

    public void requestGroupHostedRefund() {
        if (!(level instanceof ServerLevel server) || isRemoved() || dismantled || isMirror()) return;
        var index = server.getServer().getDataStorage().get(PatternRefundIndex.TYPE);
        var loaded = new ArrayList<PatternInterfaceBlockEntity>();
        loaded.add(this);
        if (index != null) {
            for (var member : index.request(location(server))) {
                var target = server.getServer().getLevel(member.mirror().dimension());
                if (target == null || !target.hasChunkAt(member.mirror().pos())) continue;
                if (target.getBlockEntity(member.mirror().pos()) instanceof PatternInterfaceBlockEntity mirror
                        && mirror.refundReady && member.instance().equals(mirror.refundInstance)) {
                    loaded.add(mirror);
                }
            }
        }
        // Inserting into an ME network can call back into another member. Freeze everyone first.
        for (var host : loaded) {
            host.markLocalHostedRefund();
            if (index != null && host != this) index.acknowledge(host.location((ServerLevel) host.level), host.refundInstance);
        }
        for (var host : loaded) host.tryLocalHostedRefund();
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
        if (isRemoved() || dismantled || !refundInitialized() || dispatcher.working() || hosted.isEmpty() || !(level instanceof ServerLevel)) return;
        dispatcher.run(level.getGameTime());
        tryLocalHostedRefund();
    }

    public void markLocalHostedRefund() { hosted.retireAll(); }

    public void tryLocalHostedRefund() {
        if (hostedOnline()) hosted.refund(networkStorage(), IActionSource.ofMachine(this), false);
    }

    private void onHostedChange() {
        // Escrow is not an ordinary capability. Waking ordinary inputs here would invalidate
        // a controller's rejection merely because its own probe closed the admission gate.
        setChanged();
        if (level instanceof ServerLevel server) {
            publishRefundMember(server);
            HostedPatternDispatcher.enqueue(server, worldPosition);
        }
    }

    public PatternInterfaceBlockEntity(BlockPos pos, BlockState state, IOPortKind kind) {
        super(typeForKind(kind), pos, state);
        this.kind = kind;
        PatternLogicKind logicKind = (PatternLogicKind) kind;
        logic = logicKind.createPatternLogic(mainNode, this);
        // PatternProviderLogic must return completed pattern outputs to the grid itself so AE2 can settle the craft.
        for (var family : AE2ResourceFamilies.all()) {
            outputStorages.put(family, family.patternOutputView(logic.getReturnInv(), () -> null,
                    IActionSource.ofMachine(this), this::onNativeReturnInventoryDrained));
            returnStorages.put(family, family.patternReturnView(logic.getReturnInv()));
        }
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
        return getMainMenuIcon().getItemName();
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
        if (Transaction.getCurrentOpenedTransaction() != null) {
            returnInventoryNotificationPending = true;
            return;
        }
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
    public ResourceStorage<ItemResource> itemStorage() {
        return itemInputStorage();
    }

    @Override
    public ResourceStorage<FluidResource> fluidStorage() {
        return fluidInputStorage();
    }

    public ResourceStorage<ItemResource> itemInputStorage() {
        return AE2ResourceFamilies.ITEM.patternInputView(networkStorage());
    }

    public ResourceStorage<FluidResource> fluidInputStorage() {
        return AE2ResourceFamilies.FLUID.patternInputView(networkStorage());
    }

    public PatternRequestResourceStorage<ItemResource> itemRequestStorage(KeyCounter[] inputHolders) {
        return AE2ResourceFamilies.ITEM.patternRequestView(inputHolders);
    }

    public PatternRequestResourceStorage<FluidResource> fluidRequestStorage(KeyCounter[] inputHolders) {
        return AE2ResourceFamilies.FLUID.patternRequestView(inputHolders);
    }

    public OutputResourceStorage<ItemResource> itemOutputStorage() {
        return resourceStorage(AE2ResourceFamilies.ITEM);
    }

    public OutputResourceStorage<FluidResource> fluidOutputStorage() {
        return resourceStorage(AE2ResourceFamilies.FLUID);
    }

    public PatternReturnResourceStorage<ItemResource> itemReturnStorage() {
        return returnStorage(AE2ResourceFamilies.ITEM);
    }

    public PatternReturnResourceStorage<FluidResource> fluidReturnStorage() {
        return returnStorage(AE2ResourceFamilies.FLUID);
    }

    @SuppressWarnings("unchecked")
    public <R> OutputResourceStorage<R> resourceStorage(AE2ResourceFamily<R> family) {
        return (OutputResourceStorage<R>) outputStorages.get(family);
    }

    @SuppressWarnings("unchecked")
    public <R> PatternReturnResourceStorage<R> returnStorage(AE2ResourceFamily<R> family) {
        return (PatternReturnResourceStorage<R>) returnStorages.get(family);
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
        if (returnInventoryNotificationPending) {
            returnInventoryNotificationPending = false;
            notifyStorageChanged();
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
    public void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("CustomName", ComponentSerialization.CODEC, customName);
        mainNode.serialize(output);
        logic.writeToNBT(output);
        hosted.write(output, level == null ? 0L : level.getGameTime());
        output.storeNullable(REFUND_INSTANCE, UUIDUtil.CODEC, refundInstance);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        boolean live = refundReady;
        refundReady = false;
        beginLoadingAdditional();
        try {
            super.loadAdditional(input);
            customName = ((PatternLogicKind) kind).readOnlyPatterns() ? null
                    : input.read("CustomName", ComponentSerialization.CODEC).orElse(null);
            mainNode.deserialize(input);
            logic.readFromNBT(input);
            hosted.read(input);
            refundInstance = input.read(REFUND_INSTANCE, UUIDUtil.CODEC).orElse(null);
        } finally {
            endLoadingAdditional();
        }
        if (live && level instanceof ServerLevel server) restoreRefundMember(server);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        customName = ((PatternLogicKind) kind).readOnlyPatterns() ? null : components.get(DataComponents.CUSTOM_NAME);
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("CustomName");
        output.discard(HostedPatternInventory.SAVE_KEY);
        output.discard(REFUND_INSTANCE);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.CUSTOM_NAME, customName);
        if (!logic.getReturnInv().isEmpty() && level != null) {
            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            logic.getReturnInv().writeToTag(output.childrenList(PatternProviderLogic.NBT_RETURN_INV));
            components.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(getType(), output.buildResult()));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.remove(HostedPatternInventory.SAVE_KEY);
        tag.remove(REFUND_INSTANCE);
        return tag;
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        GridHelper.onFirstTick(this, blockEntity -> {
            if (blockEntity.getLevel() != null && !blockEntity.isRemoved() && !blockEntity.dismantled) {
                if (blockEntity.getLevel() instanceof ServerLevel server) blockEntity.restoreRefundMember(server);
                blockEntity.mainNode.create(blockEntity.getLevel(), blockEntity.getBlockPos());
                blockEntity.logic.updatePatterns();
            }
        });
    }

    @Override
    public void onChunkUnloaded() {
        refundReady = false;
        super.onChunkUnloaded();
        mainNode.destroy();
    }

    @Override
    public void setRemoved() {
        refundReady = false;
        super.setRemoved();
        mainNode.destroy();
    }

    @Override
    public void dropContents() {
        if (dismantled || level == null || level.isClientSide()) return;
        boolean online = hostedOnline();
        dismantled = true;
        if (refundInstance != null && level instanceof ServerLevel server) {
            var index = server.getServer().getDataStorage().get(PatternRefundIndex.TYPE);
            if (index != null) index.forget(location(server), refundInstance);
        }
        hosted.refund(online ? networkStorage() : NullInventory.of(), IActionSource.ofMachine(this), true);
        var inventory = logic.getReturnInv();
        var returns = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        inventory.writeToTag(returns.childrenList(PatternProviderLogic.NBT_RETURN_INV));
        List<ItemStack> drops = new ArrayList<>();
        logic.addDrops(drops);
        for (ItemStack stack : drops) {
            Block.popResource(level, worldPosition, stack);
        }
        logic.clearContent();
        // preRemoveSideEffects may run before playerDestroy builds loot. Preserve the carrier's
        // sole material owner while clearing native patterns/sendList to make repeat calls safe.
        inventory.readFromTag(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), returns.buildResult())
                .childrenListOrEmpty(PatternProviderLogic.NBT_RETURN_INV));
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
