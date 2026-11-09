package cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.runtime.CraftingStateSnapshot;
import cn.howxu.mmcr.internal.runtime.StructureSnapshot;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import net.minecraft.resources.Identifier;
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
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.PatternBatchPlanning;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.PendingPatternQueue;
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
import cn.howxu.mmcr.internal.runtime.PatternStartBatchReservation;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongFunction;

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
    private final PendingPatternQueue pendingPatterns = new PendingPatternQueue(this::onPendingPatternsCommitted);
    private long lastLegacyRefundTick = Long.MIN_VALUE;
    private @Nullable BatchPublicationKey batchPublicationKey;
    private boolean batchPublicationSuspended;
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

    public PendingPatternQueue pendingPatterns() {
        return pendingPatterns;
    }

    private void onPendingPatternsCommitted() {
        setChanged();
        returnInventoryNotificationPending = true;
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
                Identifier recipeId = recipe.id();
                List<CraftingStateSnapshot> lanes = snapshot.factory().lanes();
                for (int j = 0; j < lanes.size(); j++) {
                    Identifier laneRecipeId = lanes.get(j).recipeId();
                    if (laneRecipeId != null && laneRecipeId.equals(recipeId)) activeForRecipe++;
                }
                long cap = batchCapacity((long) recipe.maxThreads() - activeForRecipe, parallelism);
                if (cap > capacity) capacity = cap;
            }
        }
        return capacity;
    }

    private static long batchCapacity(long threads, long parallelism) {
        if (threads <= 0L) return 0L;
        return parallelism > Long.MAX_VALUE / threads ? Long.MAX_VALUE : threads * parallelism;
    }

    public int factoryLaneCount() {
        for (BlockPos controllerPos : linkedControllerPositions()) {
            if (!(level.getBlockEntity(controllerPos) instanceof MachineControllerBlockEntity controller)) continue;
            return controller.runtimeSnapshot().factory().activeLaneCount();
        }
        return 0;
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
        refreshBatchPatterns();
        refundLegacyInputs();
        if (returnInventoryNotificationPending) {
            returnInventoryNotificationPending = false;
            notifyStorageChanged();
        }
    }

    /** Refresh only future provider/profile lookups; captured calculations and existing jobs stay unchanged. */
    public void refreshBatchPatterns() {
        if (!(level instanceof ServerLevel serverLevel) || !serverLevel.getServer().isSameThread()
                || isRemoved() || batchPublicationSuspended
                || !PatternBatchPlanning.isBatchHost(this)) return;
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
            Identifier pool = controller.currentRecipePoolId();
            // Reuse the runtime's static lane/core eligibility, not occupancy or another lane-rule approximation.
            List<Identifier> eligible = snapshot.factoryControllerPresent()
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
        // The mixin publishes before updatePatterns' native ICraftingProvider.requestUpdate call.
        logic.updatePatterns();
    }

    /** Only static rule/version inputs belong here: active recipes, energy, progress and return fullness do not.
     * @author howxu <dev@howxu.cn>
     */
    private record ControllerBatchVersion(MachineControllerBlockEntity controller, long lifecycle,
                                          StructureSnapshot structure, long capability, long modifier, long state,
                                          long upgrade, @Nullable Identifier pool, long catalog, long maxParallelism,
                                          int threadLimit, boolean linked, ModuleConnectionStatus modules,
                                          List<Identifier> eligibleFactoryRecipes) {
    }

    /** Kept on the host/main thread only, never included in a calculation snapshot.
     * @author howxu <dev@howxu.cn>
     */
    private record BatchPublicationKey(IGrid grid, Set<BlockPos> links, List<ControllerBatchVersion> controllers) {
    }

    /** Legacy inputs are refunds, never recipe requests, and need no active grid. */
    public void refundLegacyInputs() {
        if (level == null || level.isClientSide() || isRemoved() || pendingPatterns.isEmpty()) return;
        if (lastLegacyRefundTick == level.getGameTime()) return;
        lastLegacyRefundTick = level.getGameTime();
        pendingPatterns.rebaseClock(level.getGameTime());
        try (Transaction transaction = Transaction.openRoot()) {
            if (pendingPatterns.refundNext(this, transaction)) transaction.commit();
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
        pendingPatterns.write(output, level == null ? 0L : level.getGameTime());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        beginLoadingAdditional();
        try {
            super.loadAdditional(input);
            customName = ((PatternLogicKind) kind).readOnlyPatterns() ? null
                    : input.read("CustomName", ComponentSerialization.CODEC).orElse(null);
            mainNode.deserialize(input);
            logic.readFromNBT(input);
            pendingPatterns.read(input);
        } finally {
            endLoadingAdditional();
        }
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
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.CUSTOM_NAME, customName);
        if ((!pendingPatterns.isEmpty() || !logic.getReturnInv().isEmpty()) && level != null) {
            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            pendingPatterns.write(output, level.getGameTime());
            logic.getReturnInv().writeToTag(output.childrenList(PatternProviderLogic.NBT_RETURN_INV));
            components.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(getType(), output.buildResult()));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.remove("pending_patterns");
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
        batchPublicationKey = null;
        refreshBatchPatterns();
        if (!logic.getReturnInv().isEmpty() || logic.isBusy()) logic.onMainNodeStateChanged();
    }

    private void stopBatchPublication() {
        batchPublicationSuspended = true;
        batchPublicationKey = null;
        if (level instanceof ServerLevel) PatternBatchPlanning.remove(this);
    }

    private MEStorage networkStorage() {
        IGrid grid = mainNode.getGrid();
        return grid == null ? NullInventory.of() : grid.getStorageService().getInventory();
    }
}
