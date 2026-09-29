package cn.howxu.mmcr.compat.appliedenergistics2;

import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.AEKeyTypesInternal;
import appeng.api.stacks.GenericStack;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.InputInterfaceKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.InterfaceLogicKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.InputInterfaceBlockEntity;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies interface hosts obtain their storage profile from their port kind.
 *
 * @author howxu <dev@howxu.cn>
 */
class ExtendedAEInterfaceKindTest {
    @BeforeAll
    static void setup() throws Exception {
        TestBootstrap.bootstrap();
        if (!ae2KeyTypesAreInitialized()) initializeAE2KeyTypes();
        bindTestAE2InterfaceItem();
        bindTestEntityType();
    }

    @Test
    void nativeKindRetainsNineSlotStorage() {
        InputInterfaceBlockEntity input = InputInterfaceKind.INSTANCE.entityFactory()
                .create(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState());

        assertThat(input.getInterfaceLogic().getStorage().size()).isEqualTo(9);
    }

    @Test
    void hostUsesTheKindProvidedLogic() {
        InterfaceLogicKind expandedKind = testInputKind((node, host, icon) ->
                new InterfaceLogic(node, host, icon, 36));
        InputInterfaceBlockEntity input = (InputInterfaceBlockEntity) expandedKind.entityFactory()
                .create(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState());

        input.getInterfaceLogic().getConfig().setStack(35,
                new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1));

        assertThat(input.getInterfaceLogic().getConfig().getKey(35))
                .isEqualTo(AEItemKey.of(Items.IRON_INGOT));
    }

    private static InterfaceLogicKind testInputKind(InterfaceLogicFactory logicFactory) {
        return new InterfaceLogicKind() {
            @Override
            public String id() {
                return InputInterfaceKind.INSTANCE.id();
            }

            @Override
            public cn.howxu.mmcr.util.IOType ioType() {
                return InputInterfaceKind.INSTANCE.ioType();
            }

            @Override
            public java.util.List<cn.howxu.mmcr.internal.port.PortFamilyDescriptor> families() {
                return InputInterfaceKind.INSTANCE.families();
            }

            @Override
            public net.minecraft.world.level.block.entity.BlockEntityType.BlockEntitySupplier<InputInterfaceBlockEntity> entityFactory() {
                return (pos, state) -> new InputInterfaceBlockEntity(pos, state, this);
            }

            @Override
            public cn.howxu.mmcr.api.port.PortDefinition definition() {
                return InputInterfaceKind.INSTANCE.definition();
            }

            @Override
            public java.util.List<String> modDependencies() {
                return InputInterfaceKind.INSTANCE.modDependencies();
            }

            @Override
            public InterfaceLogic createInterfaceLogic(IManagedGridNode node, InterfaceLogicHost host, Item icon) {
                return logicFactory.create(node, host, icon);
            }
        };
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
        ResourceLocation id = MMCR.id(InputInterfaceKind.INSTANCE.id());
        MappedRegistry<BlockEntityType<?>> registry = (MappedRegistry<BlockEntityType<?>>) BuiltInRegistries.BLOCK_ENTITY_TYPE;
        registry.unfreeze(true);
        try {
            if (!registry.containsKey(id)) {
                Registry.register(registry, id,
                        new BlockEntityType<>(InputInterfaceKind.INSTANCE.entityFactory(), Blocks.IRON_BLOCK));
            }
        } finally {
            registry.freeze();
        }
        ModBlockEntities.BES.put(InputInterfaceKind.INSTANCE.id(),
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

    @FunctionalInterface
    private interface InterfaceLogicFactory {
        InterfaceLogic create(IManagedGridNode node, InterfaceLogicHost host, Item icon);
    }
}
