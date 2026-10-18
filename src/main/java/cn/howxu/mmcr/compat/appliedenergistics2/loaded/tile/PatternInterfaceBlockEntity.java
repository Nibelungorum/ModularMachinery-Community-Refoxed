package cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.runtime.CraftingStateSnapshot;
import cn.howxu.mmcr.internal.runtime.StructureSnapshot;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import net.minecraft.resources.ResourceLocation;
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
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.PatternBatchPlanning;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.PendingPatternQueue;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2NativeAdapters;
import cn.howxu.mmcr.compat.appmek.AppMekBridge;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.PatternLogicKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu.AE2MenuTypes;
import cn.howxu.mmcr.compat.extendedae.ExtendedAEContributor;
import cn.howxu.mmcr.compat.extendedae.ExtendedAEContributorBootstrap;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.runtime.PatternStartReservation;
import cn.howxu.mmcr.internal.runtime.PatternStartBatchReservation;
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
import net.minecraft.nbt.CompoundTag;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongFunction;

/**
 * MMCR IO port hosting AE2's native pattern-provider logic.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PatternInterfaceBlockEntity extends IOPortBlockEntity
        implements MemoryCardHost, PatternProviderLogicHost, IGridConnectedBlockEntity, PatternInterfaceHost {
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
    private @Nullable BatchPublicationKey batchPublicationKey;
    private boolean batchPublicationSuspended;

    public PendingPatternQueue pendingPatterns() {
        return pendingPatterns;
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
        List<MachineControllerBlockEntity> controllers = linkedControllerPositions().stream()
                .sorted(BlockPos::compareTo)
                .map(level::getBlockEntity)
                .filter(MachineControllerBlockEntity.class::isInstance)
                .map(MachineControllerBlockEntity.class::cast)
                .toList();
        return MachineControllerBlockEntity.reserveNextPatternStart(controllers, nextPatternController,
                getBlockPos(), patternOutputs, requestCapabilities);
    }

    /** Reserves a batch across linked controllers without changing AE2 provider selection. */
    public PatternStartBatchReservation reservePatternStarts(List<MachineOutput> patternOutputs, long requestedParallelism,
                                                             LongFunction<List<MachineCapability>> capabilitiesForParallelism) {
        if (level == null) return PatternStartBatchReservation.unavailable();
        List<MachineControllerBlockEntity> controllers = linkedControllerPositions().stream()
                .sorted(BlockPos::compareTo)
                .map(level::getBlockEntity)
                .filter(MachineControllerBlockEntity.class::isInstance)
                .map(MachineControllerBlockEntity.class::cast)
                .toList();
        return MachineControllerBlockEntity.reserveNextPatternStarts(controllers, nextPatternController,
                getBlockPos(), patternOutputs, requestedParallelism, capabilitiesForParallelism);
    }

    /** Returns the currently idle linked-controller capacity expressed in pattern operations. */
    public long maxPatternBatchSize() {
        if (level == null) return 0L;
        long capacity = 0L;
        for (BlockPos controllerPos : linkedControllerPositions()) {
            if (!(level.getBlockEntity(controllerPos) instanceof MachineControllerBlockEntity controller)) continue;
            var snapshot = controller.runtimeSnapshot();
            if (!snapshot.structure().formed() || controller.isRedstonePaused()) continue;
            long controllerCapacity;
            if (controller.hasFactoryController()) {
                int factoryLaneLimit = snapshot.factory().laneLimit();
                int factoryLaneCount = snapshot.factory().activeLaneCount();
                long machineThreadLimit = Math.max(1, controller.effectiveFactoryThreadLimit());
                long boundedLaneLimit = Math.min(factoryLaneLimit, machineThreadLimit);
                controllerCapacity = batchCapacity(boundedLaneLimit - factoryLaneCount,
                        Math.max(1L, snapshot.maxParallelism()));
            } else {
                controllerCapacity = snapshot.crafting().recipeId() == null ? snapshot.maxParallelism() : 0L;
            }
            if (controllerCapacity > Long.MAX_VALUE - capacity) return Long.MAX_VALUE;
            capacity += controllerCapacity;
        }
        return capacity;
    }

    public int factoryLaneLimit() {
        for (BlockPos controllerPos : linkedControllerPositions()) {
            if (!(level.getBlockEntity(controllerPos) instanceof MachineControllerBlockEntity controller)) continue;
            return controller.effectiveFactoryThreadLimit();
        }
        return 1;
    }

    /** Returns the recipe-specific thread cap for the recipe whose outputs match. */
    public long maxRecipeBatchSize(List<MachineOutput> patternOutputs) {
        if (level == null) return 0L;
        long capacity = 0L;
        for (BlockPos controllerPos : linkedControllerPositions()) {
            if (!(level.getBlockEntity(controllerPos) instanceof MachineControllerBlockEntity controller)) continue;
            var snapshot = controller.runtimeSnapshot();
            if (!snapshot.structure().formed() || controller.isRedstonePaused()) continue;
            long parallelism = Math.max(1L, snapshot.maxParallelism());
            List<MachineRecipe> recipes = controller.recipesForMachine();
            for (int i = 0; i < recipes.size(); i++) {
                MachineRecipe recipe = recipes.get(i);
                if (recipe.maxThreads() <= 0) continue;
                if (!controller.patternOutputsMatch(recipe, snapshot, patternOutputs)) continue;
                if (!controller.hasFactoryController()) {
                    if (parallelism > capacity) capacity = parallelism;
                    continue;
                }
                int activeForRecipe = 0;
                ResourceLocation recipeId = recipe.id();
                List<CraftingStateSnapshot> lanes = snapshot.factory().lanes();
                for (int j = 0; j < lanes.size(); j++) {
                    ResourceLocation laneRecipeId = lanes.get(j).recipeId();
                    if (laneRecipeId != null && laneRecipeId.equals(recipeId)) activeForRecipe++;
                }
                long cap = batchCapacity((long) recipe.maxThreads() - activeForRecipe, parallelism);
                if (cap > capacity) capacity = cap;
            }
        }
        return capacity;
    }

    public int factoryLaneCount() {
        for (BlockPos controllerPos : linkedControllerPositions()) {
            if (!(level.getBlockEntity(controllerPos) instanceof MachineControllerBlockEntity controller)) continue;
            return controller.runtimeSnapshot().factory().activeLaneCount();
        }
        return 0;
    }

    private static long batchCapacity(long threads, long parallelism) {
        if (threads <= 0L) return 0L;
        return parallelism > Long.MAX_VALUE / threads ? Long.MAX_VALUE : threads * parallelism;
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
        craftingMachine.flushPendingReturns();
        refreshBatchPatterns();
        refundLegacyInputs();
    }

    /** Refreshes future planning profiles only; already captured calculations and orders are immutable. */
    public void refreshBatchPatterns() {
        if (!(level instanceof ServerLevel serverLevel) || !serverLevel.getServer().isSameThread()
                || isRemoved() || batchPublicationSuspended || !PatternBatchPlanning.isBatchHost(this)) return;
        IGrid grid = mainNode.getGrid();
        if (grid == null) {
            batchPublicationKey = null;
            PatternBatchPlanning.remove(this);
            return;
        }
        Set<BlockPos> links = Set.copyOf(linkedControllerPositions());
        List<ControllerBatchVersion> controllers = new ArrayList<>();
        for (BlockPos position : links.stream().sorted(BlockPos::compareTo).toList()) {
            if (!level.hasChunkAt(position)
                    || !(level.getBlockEntity(position) instanceof MachineControllerBlockEntity controller)
                    || controller.isRemoved()) continue;
            var snapshot = controller.currentRuntimeSnapshot();
            ResourceLocation pool = controller.currentRecipePoolId();
            List<ResourceLocation> eligible = snapshot.factoryControllerPresent()
                    ? controller.recipesForMachine().stream().filter(controller::supportsPatternRecipe)
                    .map(MachineRecipe::id).toList() : List.of();
            controllers.add(new ControllerBatchVersion(controller, controller.lifecycleEpoch(), snapshot.structure(),
                    snapshot.capabilityVersion(), snapshot.modifierVersion(), snapshot.stateVersion(),
                    snapshot.upgradeContentRevision(), pool, RecipeRegistry.catalogForPool(pool).version(),
                    snapshot.maxParallelism(), controller.effectiveFactoryThreadLimit(),
                    snapshot.linkedPortPositions().contains(worldPosition), snapshot.moduleConnectionStatus(), eligible));
        }
        var key = new BatchPublicationKey(grid, links, List.copyOf(controllers));
        if (key.equals(batchPublicationKey)) return;
        batchPublicationKey = key;
        logic.updatePatterns();
    }

    /** Static eligibility only, excluding occupancy, progress, energy and return fullness.
     * @author howxu <dev@howxu.cn>
     */
    private record ControllerBatchVersion(MachineControllerBlockEntity controller, long lifecycle,
                                          StructureSnapshot structure, long capability, long modifier, long state,
                                          long upgrade, @Nullable ResourceLocation pool, long catalog, long maxParallelism,
                                          int threadLimit, boolean linked, ModuleConnectionStatus modules,
                                          List<ResourceLocation> eligibleFactoryRecipes) {
    }

    /** @author howxu <dev@howxu.cn> */
    private record BatchPublicationKey(IGrid grid, Set<BlockPos> links, List<ControllerBatchVersion> controllers) {
    }

    /** Old escrow is refunded without requiring an active grid, never used to start a recipe. */
    public void refundLegacyInputs() {
        if (level == null || level.isClientSide() || isRemoved() || pendingPatterns.isEmpty()) return;
        if (lastLegacyRefundTick == level.getGameTime()) return;
        lastLegacyRefundTick = level.getGameTime();
        pendingPatterns.rebaseClock(level.getGameTime());
        try {
            pendingPatterns.refundNext(this);
        } catch (RuntimeException exception) {
            MMCR.LOG.warn("Native legacy pattern refund notification interrupted at {}", getBlockPos(), exception);
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
        mainNode.saveToNBT(output);
        craftingMachine.savePendingReturns(output, registries);
        logic.writeToNBT(output, registries);
        pendingPatterns.write(output, registries, level == null ? 0L : level.getGameTime());
    }

    @Override
    protected void loadAdditional(CompoundTag input, HolderLookup.Provider registries) {
        beginLoadingAdditional();
        try {
            super.loadAdditional(input, registries);
            mainNode.loadFromNBT(input);
            logic.readFromNBT(input, registries);
            craftingMachine.loadPendingReturns(input, registries);
            pendingPatterns.read(input, registries);
        } finally {
            endLoadingAdditional();
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (level != null && (!pendingPatterns.isEmpty() || !logic.getReturnInv().isEmpty()
                || craftingMachine.hasPendingReturns())) {
            CompoundTag data = new CompoundTag();
            pendingPatterns.write(data, level.registryAccess(), level.getGameTime());
            data.put(PatternProviderLogic.NBT_RETURN_INV, logic.getReturnInv().writeToTag(level.registryAccess()));
            craftingMachine.saveCarrierReturns(data, level.registryAccess());
            data.putString("id", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(getType()).toString());
            components.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(data));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.remove("pending_patterns");
        tag.remove("pattern_pending_returns");
        return tag;
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        batchPublicationSuspended = false;
        batchPublicationKey = null;
        GridHelper.onFirstTick(this, blockEntity -> {
            if (blockEntity.getLevel() != null && !blockEntity.isRemoved() && !blockEntity.batchPublicationSuspended) {
                blockEntity.mainNode.create(blockEntity.getLevel(), blockEntity.getBlockPos());
                blockEntity.logic.updatePatterns();
            }
        });
    }

    @Override
    public void onChunkUnloaded() {
        stopBatchPublication();
        super.onChunkUnloaded();
        mainNode.destroy();
    }

    @Override
    public void setRemoved() {
        stopBatchPublication();
        super.setRemoved();
        mainNode.destroy();
    }

    @Override
    public void dropContents() {
        if (level == null || level.isClientSide()) return;
        List<ItemStack> drops = new ArrayList<>();
        logic.addDrops(drops);
        for (ItemStack stack : drops) {
            Block.popResource(level, worldPosition, stack);
        }
        // The native clear mixin retains carrier-owned returns, including when destruction reenters a batch.
        logic.clearContent();
    }

    private void onNetworkChanged() {
        batchPublicationKey = null;
        refreshBatchPatterns();
        if (!logic.getReturnInv().isEmpty() || logic.isBusy()) logic.onMainNodeStateChanged();
    }

    private void stopBatchPublication() {
        batchPublicationSuspended = true;
        batchPublicationKey = null;
        if (level instanceof ServerLevel) PatternBatchPlanning.remove(this);
    }

}
