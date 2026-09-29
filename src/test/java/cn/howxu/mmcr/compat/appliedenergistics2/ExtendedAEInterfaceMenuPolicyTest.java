package cn.howxu.mmcr.compat.appliedenergistics2;

import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.AEKeyTypesInternal;
import appeng.menu.slot.AppEngSlot;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.compat.extendedae.loaded.kind.ExtendedInputInterfaceKind;
import cn.howxu.mmcr.compat.extendedae.loaded.kind.ExtendedOutputInterfaceKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.AsyncOutputInterfaceKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.AsyncOutputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.InputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.OutputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.util.InterfaceMenuPolicy;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.test.TestBootstrap;
import com.glodblock.github.extendedae.client.ExSemantics;
import com.glodblock.github.extendedae.container.ContainerExInterface;
import com.mojang.serialization.Lifecycle;
import java.lang.reflect.Constructor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies EAE interface slots use the shared output menu policy.
 *
 * @author howxu <dev@howxu.cn>
 */
class ExtendedAEInterfaceMenuPolicyTest {
    @BeforeAll
    static void setup() throws Exception {
        TestBootstrap.bootstrap();
        if (!ae2KeyTypesAreInitialized()) initializeAE2KeyTypes();
        bindTestAE2InterfaceItem();
        bindTestEntityType();
    }

    @Test
    void distinguishesOutputStorageWrappersByHostKind() {
        OutputInterfaceBlockEntity output = outputHost();
        AsyncOutputInterfaceBlockEntity asyncOutput = asyncOutputHost();
        InputInterfaceBlockEntity input = inputHost();
        var storageWrapper = output.getInterfaceLogic().getStorage().createMenuWrapper();

        assertThat(InterfaceMenuPolicy.isOutputStorageSlot(output, storageWrapper)).isTrue();
        assertThat(InterfaceMenuPolicy.isExtractableOutputStorageSlot(output, storageWrapper)).isTrue();
        assertThat(InterfaceMenuPolicy.isExtractableOutputStorageSlot(asyncOutput, storageWrapper)).isFalse();
        assertThat(InterfaceMenuPolicy.isOutputStorageSlot(input, storageWrapper)).isFalse();
    }

    @Test
    void recognizesAllExtendedAeStorageSlotGroups() {
        OutputInterfaceBlockEntity output = outputHost();
        Player player = testPlayer();
        EntityEquipment equipment = new EntityEquipment();
        setPlayerField(player, "equipment", equipment);
        Inventory playerInventory = new Inventory(player, equipment);
        setPlayerField(player, "inventory", playerInventory);
        ContainerExInterface menu = new ContainerExInterface(ContainerExInterface.TYPE, 0,
                playerInventory, output);

        assertStorageSlotsFollowPolicy(menu, output, ExSemantics.EX_2);
        assertStorageSlotsFollowPolicy(menu, output, ExSemantics.EX_4);
        assertStorageSlotsFollowPolicy(menu, output, ExSemantics.EX_6);
        assertStorageSlotsFollowPolicy(menu, output, ExSemantics.EX_8);
    }

    private static void assertStorageSlotsFollowPolicy(ContainerExInterface menu, OutputInterfaceBlockEntity output,
                                                       appeng.menu.SlotSemantic semantic) {
        assertThat(menu.getSlots(semantic)).isNotEmpty().allSatisfy(slot -> {
            assertThat(slot).isInstanceOf(AppEngSlot.class);
            assertThat(InterfaceMenuPolicy.isExtractableOutputStorageSlot(output,
                    ((AppEngSlot) slot).getInventory())).isTrue();
        });
    }

    private static OutputInterfaceBlockEntity outputHost() {
        return new OutputInterfaceBlockEntity(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState(),
                ExtendedOutputInterfaceKind.INSTANCE);
    }

    private static AsyncOutputInterfaceBlockEntity asyncOutputHost() {
        try {
            Constructor<AsyncOutputInterfaceBlockEntity> constructor = AsyncOutputInterfaceBlockEntity.class
                    .getDeclaredConstructor(BlockPos.class, net.minecraft.world.level.block.state.BlockState.class,
                            cn.howxu.mmcr.internal.port.IOPortKind.class);
            constructor.setAccessible(true);
            return constructor.newInstance(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState(),
                    AsyncOutputInterfaceKind.INSTANCE);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to construct async output interface test host", exception);
        }
    }

    private static InputInterfaceBlockEntity inputHost() {
        return new InputInterfaceBlockEntity(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState(),
                ExtendedInputInterfaceKind.INSTANCE);
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

    @SuppressWarnings("unchecked")
    private static void bindTestEntityType() {
        bindTestEntityType(ExtendedOutputInterfaceKind.INSTANCE.id(), ExtendedOutputInterfaceKind.INSTANCE.entityFactory());
        bindTestEntityType(AsyncOutputInterfaceKind.INSTANCE.id(), AsyncOutputInterfaceKind.INSTANCE.entityFactory());
        bindTestEntityType(ExtendedInputInterfaceKind.INSTANCE.id(), ExtendedInputInterfaceKind.INSTANCE.entityFactory());
    }

    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> void bindTestEntityType(String kind, BlockEntityType.BlockEntitySupplier<T> factory) {
        ResourceLocation id = MMCR.id(kind);
        MappedRegistry<BlockEntityType<?>> registry = (MappedRegistry<BlockEntityType<?>>) BuiltInRegistries.BLOCK_ENTITY_TYPE;
        registry.unfreeze(true);
        try {
            if (!registry.containsKey(id)) {
                Registry.register(registry, id, new BlockEntityType<>(factory, Blocks.IRON_BLOCK));
            }
        } finally {
            registry.freeze();
        }
        ModBlockEntities.BES.put(kind,
                DeferredHolder.create(Registries.BLOCK_ENTITY_TYPE, id));
    }

    @SuppressWarnings("unchecked")
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

    @SuppressWarnings("unchecked")
    private static Player testPlayer() {
        try {
            var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Player) ((sun.misc.Unsafe) field.get(null)).allocateInstance(TestPlayer.class);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to allocate test player", exception);
        }
    }

    private static void setPlayerField(Player player, String name, Object value) {
        try {
            for (Class<?> type = Player.class; type != null; type = type.getSuperclass()) {
                try {
                    var field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    field.set(player, value);
                    return;
                } catch (NoSuchFieldException ignored) {
                }
            }
            throw new NoSuchFieldException(name);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to initialize test player", exception);
        }
    }

    private static final class TestPlayer extends Player {
        private TestPlayer(Level level) {
            super(level, null);
        }

        @Override
        public GameType gameMode() {
            return GameType.SURVIVAL;
        }
    }
}
