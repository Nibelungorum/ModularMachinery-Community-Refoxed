package cn.howxu.mmcr.compat.appliedenergistics2;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.AEKeyTypesInternal;
import appeng.api.stacks.GenericStack;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;
import appeng.me.ManagedGridNode;
import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.PatternInterfaceKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.OutputInterfaceBaseBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.util.InterfaceMenuPolicy;
import cn.howxu.mmcr.compat.appliedenergistics2.InterfaceScreenTitles;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.util.IOType;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.internal.runtime.ResourceAvailabilityNotifier;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the output host's direction-specific lifecycle contract.
 *
 * @author howxu <dev@howxu.cn>
 */
class AE2OutputInterfaceHostTest {
    @BeforeAll
    static void setup() throws Exception {
        TestBootstrap.bootstrap();
        if (!ae2KeyTypesAreInitialized()) initializeAE2KeyTypes();
        bindTestAE2InterfaceItem();
        bindTestPatternInterfaceEntityType();
    }

    @Test
    void saveChangesDoesNotNotifyInputControllers() {
        TestOutputHost host = new TestOutputHost();

        host.saveChanges();

        assertThat(host.inputNotifications).isZero();
    }

    @Test
    void removingTheHostDestroysItsManagedNode() {
        TestOutputHost host = new TestOutputHost();

        host.setRemoved();

        assertThat(host.isRemoved()).isTrue();
        assertThat(host.getMainNode().isReady()).isFalse();
    }

    @Test
    void outputMenuPolicyLocksOnlyOutputHosts() {
        assertThat(InterfaceMenuPolicy.isOutputHost(new TestOutputHost())).isTrue();
        assertThat(InterfaceMenuPolicy.isOutputHost(new Object())).isFalse();
    }

    @Test
    void outputMenuPolicyRecognizesTheReadOnlyCacheStorageRow() {
        TestOutputHost host = new TestOutputHost();
        host.getInterfaceLogic().getStorage().setStack(0,
                new GenericStack(AEItemKey.of(Items.IRON_INGOT), 4L));

        var storageWrapper = host.getInterfaceLogic().getStorage().createMenuWrapper();
        var configWrapper = host.getInterfaceLogic().getConfig().createMenuWrapper();

        assertThat(InterfaceMenuPolicy.isOutputStorageSlot(host, storageWrapper)).isTrue();
        assertThat(InterfaceMenuPolicy.isOutputStorageSlot(host, configWrapper)).isFalse();
        assertThat(ItemStack.matches(storageWrapper.getStackInSlot(0),
                Items.IRON_INGOT.getDefaultInstance().copyWithCount(4))).isTrue();
    }

    @Test
    void patternHostUsesTheNativeChannelledSmartCableNode() throws Exception {
        PatternInterfaceBlockEntity host = patternHost();

        assertThat(host).isExactlyInstanceOf(PatternInterfaceBlockEntity.class);
        assertThat(initializationFlags(host.getMainNode())).contains(GridFlags.REQUIRE_CHANNEL);
        assertThat(host.getCableConnectionType(Direction.NORTH)).isEqualTo(AECableType.SMART);
    }

    @Test
    void patternHostKeepsNativePatternAndReturnInventoriesSeparate() {
        PatternInterfaceBlockEntity host = patternHost();

        assertThat(host.getLogic().getPatternInv().size()).isEqualTo(9);
        assertThat(host.getLogic().getReturnInv().size()).isEqualTo(9);
        assertThat((Object) host.getLogic().getPatternInv()).isNotSameAs(host.getLogic().getReturnInv());
        assertThat(host.nativeItemHandler()).isNotNull();
        assertThat(host.nativeFluidHandler()).isNotNull();
    }

    @Test
    void patternHostExposesOnlyPatternReturnOutputViews() {
        PatternInterfaceBlockEntity host = patternHost();

        assertThat(host.capabilitySnapshot().capabilities()).hasSize(2);
        assertThat(host.capabilitySnapshot().capabilities())
                .filteredOn(capability -> capability.directions().supports(IOType.INPUT)
                        && !capability.directions().supports(IOType.OUTPUT))
                .isEmpty();
        assertThat(host.capabilitySnapshot().capabilities())
                .filteredOn(capability -> capability.directions().supports(IOType.OUTPUT)
                        && !capability.directions().supports(IOType.INPUT))
                .hasSize(2);
    }

    @Test
    void serverTickNotifiesLinkedOutputCapacitySearchesAfterNativeReturnInventoryDrain() {
        PatternInterfaceBlockEntity host = patternHost();
        RecordingController controller = new RecordingController(new BlockPos(1, 0, 0),
                ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState());
        var level = LevelStub.create(Map.of(
                host.getBlockPos(), host.getBlockState().getBlock(),
                controller.getBlockPos(), controller.getBlockState().getBlock()), List.of(host, controller));
        host.setLevel(level);
        controller.setLevel(level);
        host.linkControllerAppearance(controller.getBlockPos(), null);

        AEItemKey gold = AEItemKey.of(Items.GOLD_INGOT);
        host.getLogic().getReturnInv().setStack(0, new GenericStack(gold, 2L));
        host.serverTick();
        host.linkControllerAppearance(controller.getBlockPos(), null);
        controller.notifiedOutputResources.clear();

        assertThat(host.getLogic().getReturnInv().injectIntoNetwork(new MEStorage() {
            @Override
            public long insert(AEKey key, long amount, appeng.api.config.Actionable mode, IActionSource source) {
                return amount;
            }

            @Override
            public Component getDescription() {
                return Component.literal("test");
            }
        }, IActionSource.empty(), ignored -> {})).isTrue();

        host.serverTick();

        assertThat(controller.notifiedOutputResources).singleElement().satisfies(resource ->
                assertThat(AEItemKey.of((ItemStack) resource)).isEqualTo(gold));
    }

    @Test
    void patternProviderTitleChangesOnlyForTheMmcrHost() {
        var original = net.minecraft.network.chat.Component.translatable("gui.ae2.PatternProvider");

        assertThat(InterfaceScreenTitles.titleFor(patternHost(), original))
                .isEqualTo(net.minecraft.network.chat.Component.translatable(
                        "container.mmcr.ae2_me_pattern_interface"));
        assertThat(InterfaceScreenTitles.titleFor(new Object(), original)).isSameAs(original);
    }

    private static final class TestOutputHost extends OutputInterfaceBaseBlockEntity {
        private int inputNotifications;

        private TestOutputHost() {
            super(BlockPos.ZERO, ModBlocks.BLOCKS.get(PortKinds.ITEM_OUTPUT.id()).get().defaultBlockState(),
                    PortKinds.ITEM_OUTPUT);
        }

        @Override
        public CapabilitySnapshot capabilitySnapshot() {
            return new CapabilitySnapshot(List.of());
        }

        @Override
        protected void notifyControllerOfInputChange() {
            inputNotifications++;
        }
    }

    private static PatternInterfaceBlockEntity patternHost() {
        return PatternInterfaceKind.INSTANCE.entityFactory()
                .create(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState());
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

    private static void bindTestPatternInterfaceEntityType() {
        ResourceLocation id = MMCR.id(PatternInterfaceKind.INSTANCE.id());
        MappedRegistry<BlockEntityType<?>> registry = (MappedRegistry<BlockEntityType<?>>) BuiltInRegistries.BLOCK_ENTITY_TYPE;
        registry.unfreeze(true);
        try {
            if (!registry.containsKey(id)) {
                Registry.register(registry, id,
                        new BlockEntityType<>(PatternInterfaceKind.INSTANCE.entityFactory(), Blocks.IRON_BLOCK));
            }
        } finally {
            registry.freeze();
        }
        ModBlockEntities.BES.put(PatternInterfaceKind.INSTANCE.id(),
                DeferredHolder.create(Registries.BLOCK_ENTITY_TYPE, id));
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

    private static void initializeAE2KeyTypes() {
        MappedRegistry<AEKeyType> registry = new MappedRegistry<>(AEKeyType.REGISTRY_KEY, Lifecycle.stable());
        AEKeyTypesInternal.setRegistry(registry);
        Registry.register(registry, AEKeyType.items().getId(), AEKeyType.items());
        Registry.register(registry, AEKeyType.fluids().getId(), AEKeyType.fluids());
        registry.freeze();
    }

    /**
     * Records availability callbacks issued by the pattern port.
     *
     * @author howxu <dev@howxu.cn>
     */
    private static final class RecordingController extends MachineControllerBlockEntity {
        private final List<Object> notifiedOutputResources = new ArrayList<>();

        private RecordingController(BlockPos pos, BlockState state) {
            super(pos, state);
        }

        @Override
        public void notifyResourceAvailability(ResourceAvailabilityNotifier.Reason reason, Object resource) {
            if (reason == ResourceAvailabilityNotifier.Reason.OUTPUT_CAPACITY) {
                notifiedOutputResources.add(resource);
            }
        }
    }
}
