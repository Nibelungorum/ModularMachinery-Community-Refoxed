package cn.howxu.mmcr.compat.appliedenergistics2.loaded;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.AEKeyTypesInternal;
import appeng.api.stacks.GenericStack;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.OutputInterfaceBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the output ticker's disconnected and inactive policies.
 *
 * @author howxu <dev@howxu.cn>
 */
class AE2OutputInterfaceTickerTest {
    @BeforeAll
    static void setup() throws Exception {
        TestBootstrap.bootstrap();
        if (!ae2KeyTypesAreInitialized()) initializeAE2KeyTypes();
        bindTestAE2InterfaceItem();
    }

    @Test
    void inactiveNodeSleepsWithoutChangingNativeInventory() {
        OutputInterfaceBlockEntity host = new OutputInterfaceBlockEntity(
                BlockPos.ZERO, outputState(), PortKinds.ITEM_OUTPUT);
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        host.getStorage().setStack(0, new GenericStack(iron, 64L));
        IGridTickable ticker = host.outputTicker;

        assertThat(ticker.tickingRequest(node(false), 1)).isEqualTo(TickRateModulation.SLEEP);
        assertThat(host.getStorage().getStack(0)).isEqualTo(new GenericStack(iron, 64L));
    }

    @Test
    void activeDisconnectedNodeSlowsWhileOutputRemainsAndSleepsWhenEmpty() {
        OutputInterfaceBlockEntity host = new OutputInterfaceBlockEntity(
                BlockPos.ZERO, outputState(), PortKinds.ITEM_OUTPUT);
        host.getStorage().setStack(0, new GenericStack(AEItemKey.of(Items.IRON_INGOT), 8L));
        IGridTickable ticker = host.outputTicker;
        IGridNode active = node(true);

        assertThat(ticker.getTickingRequest(active).isSleeping()).isFalse();
        assertThat(ticker.tickingRequest(active, 1)).isEqualTo(TickRateModulation.SLOWER);

        host.getStorage().clear();
        assertThat(ticker.getTickingRequest(active).isSleeping()).isTrue();
        assertThat(ticker.tickingRequest(active, 1)).isEqualTo(TickRateModulation.SLEEP);
    }

    private static BlockState outputState() {
        return ModBlocks.BLOCKS.get(PortKinds.ITEM_OUTPUT.id()).get().defaultBlockState();
    }

    private static IGridNode node(boolean active) {
        return (IGridNode) Proxy.newProxyInstance(IGridNode.class.getClassLoader(),
                new Class<?>[]{IGridNode.class},
                (_, method, _) -> method.getName().equals("isActive")
                        ? active : defaultValue(method.getReturnType()));
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

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        return null;
    }
}
