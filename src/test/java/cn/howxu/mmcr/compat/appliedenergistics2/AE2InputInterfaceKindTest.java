package cn.howxu.mmcr.compat.appliedenergistics2;

import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.AEKeyTypesInternal;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.api.AECapabilities;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IManagedGridNode;
import appeng.me.ManagedGridNode;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.*;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.AsyncOutputInterfaceKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.InputInterfaceKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.OutputInterfaceKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.PatternInterfaceKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.StockingInterfaceKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.*;
import com.mojang.serialization.Lifecycle;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.api.capability.facet.OperationFacet;
import cn.howxu.mmcr.api.capability.facet.ResourceFacet;
import cn.howxu.mmcr.internal.event.ModCapabilities;
import cn.howxu.mmcr.internal.capability.NativeStackSync;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.FluidHatchSize;
import cn.howxu.mmcr.internal.port.ItemBusSize;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.internal.port.PortFamilyIds;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the AE2 input interface kind and its shared storage host.
 *
 * @author howxu <dev@howxu.cn>
 */
class AE2InputInterfaceKindTest {
    @BeforeAll
    static void setup() throws Exception {
        TestBootstrap.bootstrap();
        if (!ae2KeyTypesAreInitialized()) initializeAE2KeyTypes();
        bindTestAE2InterfaceItem();
        bindTestEntityType();
        AE2BridgeBootstrap.installForTesting(new LoadedAE2Bridge());
        PortKinds.clearForTesting();
        PortKinds.register(StockingInterfaceKind.INSTANCE);
        PortKinds.register(OutputInterfaceKind.INSTANCE);
        PortKinds.register(PatternInterfaceKind.INSTANCE);
    }

    @AfterAll
    static void cleanup() {
        PortKinds.clearForTesting();
        AE2BridgeBootstrap.resetForTesting();
    }

    @Test
    void ae2KindCountsOneItemAndOneFluidInput() {
        IOPortKind kind = InputInterfaceKind.INSTANCE;

        assertThat(kind.id()).isEqualTo("ae2_me_input_interface");
        assertThat(kind.ioType()).isEqualTo(IOType.INPUT);
        assertThat(kind.families()).extracting(PortFamilyDescriptor::familyId)
                .containsExactlyInAnyOrder(PortFamilyIds.ITEM, PortFamilyIds.FLUID);
        assertThat(kind.definition().bindings()).extracting(binding -> binding.type().id())
                .containsExactlyInAnyOrder(PortFamilyIds.ITEM, PortFamilyIds.FLUID);
    }

    @Test
    void stockingKindCountsOneItemAndOneFluidInput() {
        IOPortKind kind = StockingInterfaceKind.INSTANCE;

        assertThat(kind.id()).isEqualTo("ae2_me_stocking_input_interface");
        assertThat(kind.ioType()).isEqualTo(IOType.INPUT);
        assertThat(kind.families()).extracting(PortFamilyDescriptor::familyId)
                .containsExactlyInAnyOrder(PortFamilyIds.ITEM, PortFamilyIds.FLUID);
        assertThat(kind.definition().bindings()).extracting(binding -> binding.type().id())
                .containsExactlyInAnyOrder(PortFamilyIds.ITEM, PortFamilyIds.FLUID);
    }

    @Test
    void ae2InputKindsOutrankEveryOrdinaryInputPort() {
        assertThat(InputInterfaceKind.INSTANCE.families())
                .extracting(PortFamilyDescriptor::detectionTier)
                .containsExactlyInAnyOrder(ItemBusSize.values().length, FluidHatchSize.values().length);
        assertThat(StockingInterfaceKind.INSTANCE.families())
                .extracting(PortFamilyDescriptor::detectionTier)
                .containsExactlyInAnyOrder(ItemBusSize.values().length, FluidHatchSize.values().length);
    }

    @Test
    void entityViewsExposeInputOnlyNativeHandlers() {
        InputInterfaceBlockEntity entity = ordinaryEntity();

        assertThat(entity.kind()).isSameAs(InputInterfaceKind.INSTANCE);
        assertThat(entity.ioType()).isEqualTo(IOType.INPUT);
        entity.getInterfaceLogic().getStorage().setStack(0,
                new GenericStack(AEItemKey.of(Items.IRON_INGOT), 4L));
        assertThat(((NativeStackSync.Item) entity.nativeItemHandler()).amount(0)).isEqualTo(4L);
        var capabilities = entity.capabilitySnapshot().capabilities();
        assertThat(capabilities).hasSize(2)
                .extracting(capability -> capability.type().id())
                .containsExactlyInAnyOrder(PortFamilyIds.ITEM, PortFamilyIds.FLUID);
        assertThat(capabilities).allSatisfy(capability -> {
            assertThat(capability.directions().supports(IOType.INPUT)).isTrue();
            assertThat(capability.directions().supports(IOType.OUTPUT)).isFalse();
            assertThat(capability.view().type()).isEqualTo(capability.type());
            assertThat(capability.view().directions().supports(IOType.INPUT)).isTrue();
            assertThat(capability.view().directions().supports(IOType.OUTPUT)).isFalse();
        });
        assertThat(entity.nativeItemHandler()).isNotNull();
        assertThat(entity.nativeFluidHandler()).isNotNull();
    }

    @Test
    void entityFactoryCreatesOrdinaryInterfaceHost() {
        var entity = InputInterfaceKind.INSTANCE.entityFactory()
                .create(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState());

        assertThat(entity).isExactlyInstanceOf(InputInterfaceBlockEntity.class);
    }

    @Test
    void entityFactoryCreatesStockingInterfaceHost() {
        var entity = StockingInterfaceKind.INSTANCE.entityFactory()
                .create(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState());

        assertThat(entity).isExactlyInstanceOf(StockingInterfaceBlockEntity.class);
    }

    @Test
    void outputKindExposesOneItemAndOneFluidOutput() {
        IOPortKind kind = OutputInterfaceKind.INSTANCE;

        assertThat(kind.id()).isEqualTo("ae2_me_output_interface");
        assertThat(kind.ioType()).isEqualTo(IOType.OUTPUT);
        assertThat(kind.families()).extracting(PortFamilyDescriptor::familyId)
                .containsExactlyInAnyOrder(PortFamilyIds.ITEM, PortFamilyIds.FLUID);
        assertThat(kind.definition().bindings()).extracting(binding -> binding.type().id())
                .containsExactlyInAnyOrder(PortFamilyIds.ITEM, PortFamilyIds.FLUID);
    }

    @Test
    void asyncOutputKindExposesOneItemAndOneFluidOutput() {
        IOPortKind kind = AsyncOutputInterfaceKind.INSTANCE;

        assertThat(kind.id()).isEqualTo("ae2_me_async_output_interface");
        assertThat(kind.ioType()).isEqualTo(IOType.OUTPUT);
        assertThat(kind.families()).extracting(PortFamilyDescriptor::familyId)
                .containsExactlyInAnyOrder(PortFamilyIds.ITEM, PortFamilyIds.FLUID);
        assertThat(kind.definition().bindings()).extracting(binding -> binding.type().id())
                .containsExactlyInAnyOrder(PortFamilyIds.ITEM, PortFamilyIds.FLUID);
    }

    @Test
    void outputKindUsesOutputAliasesAndMaxPlusOneTiers() {
        OutputInterfaceKind kind = OutputInterfaceKind.INSTANCE;

        assertThat(kind.families())
                .allSatisfy(family -> {
                    int expected = family.familyId().equals(PortFamilyIds.ITEM)
                            ? ItemBusSize.LUDICROUS.ordinal() + 1
                            : FluidHatchSize.VACUUM.ordinal() + 1;
                    assertThat(family.detectionTier()).isEqualTo(expected);
                })
                .extracting(PortFamilyDescriptor::countAliases)
                .containsExactlyInAnyOrder(List.of("item_output_bus"), List.of("fluid_output_hatch"));
    }

    @Test
    void asyncOutputKindUsesOutputAliasesAndMaxPlusOneTiers() {
        AsyncOutputInterfaceKind kind = AsyncOutputInterfaceKind.INSTANCE;

        assertThat(kind.families())
                .allSatisfy(family -> {
                    int expected = family.familyId().equals(PortFamilyIds.ITEM)
                            ? ItemBusSize.LUDICROUS.ordinal() + 1
                            : FluidHatchSize.VACUUM.ordinal() + 1;
                    assertThat(family.detectionTier()).isEqualTo(expected);
                })
                .extracting(PortFamilyDescriptor::countAliases)
                .containsExactlyInAnyOrder(List.of("item_output_bus"), List.of("fluid_output_hatch"));
    }

    @Test
    void existingInputKindsRemainInputOnly() {
        assertThat(InputInterfaceKind.INSTANCE.ioType()).isEqualTo(IOType.INPUT);
        assertThat(StockingInterfaceKind.INSTANCE.ioType()).isEqualTo(IOType.INPUT);
    }

    @Test
    void everyAe2InterfaceWorldNodeRequiresAChannel() throws Exception {
        assertThat(initializationFlags(ordinaryEntity().getMainNode()))
                .contains(GridFlags.REQUIRE_CHANNEL);
        assertThat(initializationFlags(newStockingEntity().getMainNode()))
                .contains(GridFlags.REQUIRE_CHANNEL);
        assertThat(initializationFlags(newOutputEntity().getMainNode()))
                .contains(GridFlags.REQUIRE_CHANNEL);
        assertThat(initializationFlags(newAsyncOutputEntity().getMainNode()))
                .contains(GridFlags.REQUIRE_CHANNEL);
        assertThat(initializationFlags(newPatternEntity().getMainNode()))
                .contains(GridFlags.REQUIRE_CHANNEL);
    }

    @Test
    void entityFactoryCreatesOutputInterfaceHost() {
        var entity = OutputInterfaceKind.INSTANCE.entityFactory()
                .create(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState());

        assertThat(entity).isExactlyInstanceOf(OutputInterfaceBlockEntity.class);
    }

    @Test
    void entityFactoryCreatesAsyncOutputInterfaceHost() {
        var entity = AsyncOutputInterfaceKind.INSTANCE.entityFactory()
                .create(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState());

        assertThat(entity).isExactlyInstanceOf(AsyncOutputInterfaceBlockEntity.class);
    }

    @Test
    void entityFactoryCreatesPatternInterfaceHost() {
        var entity = PatternInterfaceKind.INSTANCE.entityFactory()
                .create(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState());

        assertThat(entity).isExactlyInstanceOf(PatternInterfaceBlockEntity.class);
    }

    @Test
    void outputEntityCapabilitiesExposeNativeCacheHandlers() {
        OutputInterfaceBlockEntity entity = newOutputEntity();

        var capabilities = entity.capabilitySnapshot().capabilities();
        assertThat(capabilities).hasSize(2)
                .extracting(capability -> capability.type().id())
                .containsExactlyInAnyOrder(PortFamilyIds.ITEM, PortFamilyIds.FLUID);
        assertThat(capabilities).allSatisfy(capability -> {
            assertThat(capability.directions().supports(IOType.OUTPUT)).isTrue();
            assertThat(capability.directions().supports(IOType.INPUT)).isFalse();
        });
        assertThat(entity.nativeItemHandler()).isNotNull();
        assertThat(entity.nativeFluidHandler()).isNotNull();
    }

    @Test
    void outputHostsAreInstancesOfOutputBaseClass() {
        OutputInterfaceBlockEntity outputEntity = newOutputEntity();
        AsyncOutputInterfaceBlockEntity asyncEntity = newAsyncOutputEntity();

        assertThat(outputEntity).isInstanceOf(OutputInterfaceBaseBlockEntity.class);
        assertThat(asyncEntity).isInstanceOf(OutputInterfaceBaseBlockEntity.class);
    }

    @Test
    void stockingHostIsNotAnOutputBaseInstance() {
        StockingInterfaceBlockEntity stockingEntity = newStockingEntity();

        assertThat(stockingEntity).isNotInstanceOf(OutputInterfaceBaseBlockEntity.class);
    }

    @Test
    void outputHostsAreNotStockingInstances() {
        OutputInterfaceBlockEntity outputEntity = newOutputEntity();
        AsyncOutputInterfaceBlockEntity asyncEntity = newAsyncOutputEntity();

        assertThat(outputEntity).isNotInstanceOf(StockingInterfaceBlockEntity.class);
        assertThat(asyncEntity).isNotInstanceOf(StockingInterfaceBlockEntity.class);
    }

    @Test
    void asyncOutputEntityCapabilitiesAreOutputOnly() {
        AsyncOutputInterfaceBlockEntity entity = newAsyncOutputEntity();

        var capabilities = entity.capabilitySnapshot().capabilities();
        assertThat(capabilities).hasSize(2)
                .extracting(capability -> capability.type().id())
                .containsExactlyInAnyOrder(PortFamilyIds.ITEM, PortFamilyIds.FLUID);
        assertThat(capabilities).allSatisfy(capability -> {
            assertThat(capability.directions().supports(IOType.OUTPUT)).isTrue();
            assertThat(capability.directions().supports(IOType.INPUT)).isFalse();
        });
    }

    @Test
    void outputCapabilitiesAreProjectedToNativeHandlersForCacheExtraction() {
        OutputInterfaceBlockEntity entity = newOutputEntity();
        RegisterCapabilitiesEvent event = capabilityEvent();
        ModCapabilities.register(event);

        var state = Blocks.IRON_BLOCK.defaultBlockState();
        var level = LevelStub.create(Blocks.IRON_BLOCK, 1, 1, 1, BlockPos.ZERO);
        assertThat(ModCapabilities.ITEM_BLOCK.getCapability(level, BlockPos.ZERO, state, entity, Direction.NORTH))
                .isNotNull();
        assertThat(ModCapabilities.FLUID_BLOCK.getCapability(level, BlockPos.ZERO, state, entity, Direction.NORTH))
                .isNotNull();
        assertThat(AECapabilities.GENERIC_INTERNAL_INV.getCapability(level, BlockPos.ZERO, state, entity,
                Direction.NORTH)).isNull();
        assertThat(AECapabilities.ME_STORAGE.getCapability(level, BlockPos.ZERO, state, entity,
                Direction.NORTH)).isNull();
        assertThat(AECapabilities.IN_WORLD_GRID_NODE_HOST.getCapability(level, BlockPos.ZERO, state, entity,
                null)).isSameAs(entity);
    }

    @Test
    void patternEntityExposesItsNativeReturnInventory() {
        PatternInterfaceBlockEntity entity = newPatternEntity();
        RegisterCapabilitiesEvent event = capabilityEvent();
        ModCapabilities.register(event);

        var state = Blocks.IRON_BLOCK.defaultBlockState();
        var level = LevelStub.create(Blocks.IRON_BLOCK, 1, 1, 1, BlockPos.ZERO);

        assertThat(AECapabilities.GENERIC_INTERNAL_INV.getCapability(level, BlockPos.ZERO, state, entity,
                Direction.NORTH)).isSameAs(entity.getLogic().getReturnInv());
    }

    @Test
    void stockingEntityCapabilitiesUseNetworkNativeHandlers() {
        StockingInterfaceBlockEntity entity = newStockingEntity();

        var capabilities = entity.capabilitySnapshot().capabilities();
        assertThat(capabilities).hasSize(2)
                .extracting(capability -> capability.type().id())
                .containsExactlyInAnyOrder(PortFamilyIds.ITEM, PortFamilyIds.FLUID);
        assertThat(capabilities).allSatisfy(capability -> {
            assertThat(capability.directions().supports(IOType.INPUT)).isTrue();
            assertThat(capability.directions().supports(IOType.OUTPUT)).isFalse();
            assertThat(capability.facet(ResourceFacet.class)).isPresent();
            assertThat(capability.facet(OperationFacet.class)).isPresent();
        });
        assertThat(entity.nativeItemHandler()).isNotNull();
        assertThat(entity.nativeFluidHandler()).isNotNull();
    }

    @Test
    void stockingCapabilityIsNotProjectedToNativeHandlers() {
        StockingInterfaceBlockEntity entity = newStockingEntity();
        RegisterCapabilitiesEvent event = capabilityEvent();
        ModCapabilities.register(event);

        var state = Blocks.IRON_BLOCK.defaultBlockState();
        var level = LevelStub.create(Blocks.IRON_BLOCK, 1, 1, 1, BlockPos.ZERO);
        assertThat(ModCapabilities.ITEM_BLOCK.getCapability(level, BlockPos.ZERO, state, entity, Direction.NORTH))
                .isNull();
        assertThat(ModCapabilities.FLUID_BLOCK.getCapability(level, BlockPos.ZERO, state, entity, Direction.NORTH))
                .isNull();
        assertThat(AECapabilities.GENERIC_INTERNAL_INV.getCapability(level, BlockPos.ZERO, state, entity,
                Direction.NORTH)).isNull();
        assertThat(AECapabilities.ME_STORAGE.getCapability(level, BlockPos.ZERO, state, entity,
                Direction.NORTH)).isNull();
        assertThat(AECapabilities.IN_WORLD_GRID_NODE_HOST.getCapability(level, BlockPos.ZERO, state, entity,
                null)).isSameAs(entity);
    }

    @Test
    void configMarkersAlwaysUseOneResource() {
        StockingInterfaceBlockEntity entity = newStockingEntity();

        entity.getInterfaceLogic().getConfig().setStack(0,
                new GenericStack(AEItemKey.of(Items.IRON_INGOT), 64L));

        assertThat(entity.getInterfaceLogic().getConfig().getAmount(0)).isEqualTo(1L);
    }

    @Test
    void fluidMarkersDefaultToOneBucket() {
        StockingInterfaceBlockEntity entity = newStockingEntity();

        entity.getInterfaceLogic().getConfig().setStack(1,
                new GenericStack(AEFluidKey.of(Fluids.WATER), 1L));

        assertThat(entity.getInterfaceLogic().getConfig().getAmount(1)).isEqualTo(1_000L);
    }

    @Test
    void normalizedConfigMarkerCanStillBeCleared() {
        StockingInterfaceBlockEntity entity = newStockingEntity();
        var config = entity.getInterfaceLogic().getConfig();

        config.setStack(0, new GenericStack(AEItemKey.of(Items.IRON_INGOT), 64L));
        assertThat(config.getAmount(0)).isEqualTo(1L);

        config.setStack(0, null);

        assertThat(config.getStack(0)).isNull();
    }

    @Test
    void stockingSaveWithoutGridClearsStorageMirror() {
        StockingInterfaceBlockEntity entity = newStockingEntity();
        entity.getInterfaceLogic().getConfig().setStack(0,
                new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1L));
        entity.getInterfaceLogic().getStorage().setStack(0,
                new GenericStack(AEItemKey.of(Items.IRON_INGOT), 8L));

        entity.saveChanges();

        assertThat(entity.getInterfaceLogic().getStorage().getStack(0)).isNull();
        assertThat(((NativeStackSync.Item) entity.nativeItemHandler()).amount(0)).isZero();
    }

    private static InputInterfaceBlockEntity ordinaryEntity() {
        return InputInterfaceKind.INSTANCE.entityFactory()
                .create(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState());
    }

    private static StockingInterfaceBlockEntity newStockingEntity() {
        try {
            Constructor<StockingInterfaceBlockEntity> constructor =
                    StockingInterfaceBlockEntity.class.getDeclaredConstructor(
                            BlockPos.class, BlockState.class,
                            IOPortKind.class);
            constructor.setAccessible(true);
            return constructor.newInstance(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState(),
                    StockingInterfaceKind.INSTANCE);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to construct stocking interface test host", exception);
        }
    }

    private static OutputInterfaceBlockEntity newOutputEntity() {
        try {
            Constructor<OutputInterfaceBlockEntity> constructor =
                    OutputInterfaceBlockEntity.class.getDeclaredConstructor(
                            BlockPos.class, BlockState.class,
                            IOPortKind.class);
            constructor.setAccessible(true);
            return constructor.newInstance(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState(),
                    OutputInterfaceKind.INSTANCE);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to construct output interface test host", exception);
        }
    }

    private static AsyncOutputInterfaceBlockEntity newAsyncOutputEntity() {
        try {
            Constructor<AsyncOutputInterfaceBlockEntity> constructor =
                    AsyncOutputInterfaceBlockEntity.class.getDeclaredConstructor(
                            BlockPos.class, BlockState.class,
                            IOPortKind.class);
            constructor.setAccessible(true);
            return constructor.newInstance(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState(),
                    AsyncOutputInterfaceKind.INSTANCE);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to construct async output interface test host", exception);
        }
    }

    private static PatternInterfaceBlockEntity newPatternEntity() {
        return PatternInterfaceKind.INSTANCE.entityFactory()
                .create(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState());
    }

    private static RegisterCapabilitiesEvent capabilityEvent() {
        try {
            Constructor<RegisterCapabilitiesEvent> constructor = RegisterCapabilitiesEvent.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to create capability registration event", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<GridFlags> initializationFlags(IManagedGridNode node) throws Exception {
        Field initDataField = ManagedGridNode.class.getDeclaredField("initData");
        initDataField.setAccessible(true);
        Object initData = initDataField.get(node);
        if (initData == null) throw new AssertionError("AE2 node was initialized before flag inspection");

        Field flagsField = initData.getClass().getDeclaredField("flags");
        flagsField.setAccessible(true);
        return Set.copyOf((Set<GridFlags>) flagsField.get(initData));
    }

    private static boolean ae2KeyTypesAreInitialized() {
        try {
            return !AEKeyTypes.getAll().isEmpty();
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    private static void bindTestEntityType() {
        bindTestEntityType(InputInterfaceKind.INSTANCE);
        bindTestEntityType(StockingInterfaceKind.INSTANCE);
        bindTestEntityType(OutputInterfaceKind.INSTANCE);
        bindTestEntityType(AsyncOutputInterfaceKind.INSTANCE);
        bindTestEntityType(PatternInterfaceKind.INSTANCE);
    }

    private static void bindTestEntityType(IOPortKind kind) {
        ResourceLocation id = MMCR.id(kind.id());
        MappedRegistry<BlockEntityType<?>> registry = (MappedRegistry<BlockEntityType<?>>) BuiltInRegistries.BLOCK_ENTITY_TYPE;
        registry.unfreeze(true);
        try {
            if (!registry.containsKey(id)) {
                Registry.register(registry, id,
                        new BlockEntityType<>(kind.entityFactory(), Blocks.IRON_BLOCK));
            }
        } finally {
            registry.freeze();
        }
        ModBlockEntities.BES.put(kind.id(),
                DeferredHolder.create(Registries.BLOCK_ENTITY_TYPE, id));
    }

    private static void bindTestAE2InterfaceItem() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("ae2", "interface");
        MappedRegistry<Item> registry = (MappedRegistry<Item>) BuiltInRegistries.ITEM;
        registry.unfreeze(true);
        try {
            if (!registry.containsKey(id)) {
                Registry.register(registry, id, new Item(new Item.Properties().setId(
                        ResourceKey.create(Registries.ITEM, id))));
            }
        } finally {
            registry.freeze();
        }
    }

    private static void initializeAE2KeyTypes() {
        MappedRegistry<AEKeyType> registry = new MappedRegistry<>(AEKeyType.REGISTRY_KEY, Lifecycle.stable());
        AEKeyTypesInternal.setRegistry(registry);
        Registry.register(registry, AEKeyType.items().getId(), AEKeyType.items());
        Registry.register(registry, AEKeyType.fluids().getId(), AEKeyType.fluids());
        registry.freeze();
    }
}
