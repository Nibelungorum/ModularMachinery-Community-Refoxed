package cn.howxu.mmcr.compat.appliedenergistics2.loaded;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.AEKeyTypesInternal;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the executable adaptive policy of the AE2 output ticker.
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
    void tickerBatchesBothViewsAndAdaptsFromFastToSlowToSleep() {
        ItemResource iron = ItemResource.of(Items.IRON_INGOT);
        FluidResource water = FluidResource.of(Fluids.WATER);
        FakeMEStorage network = new FakeMEStorage(Map.of(
                Objects.requireNonNull(AEItemKey.of(iron)), 64L,
                Objects.requireNonNull(AEFluidKey.of(water)), 192L));
        OutputInterfaceBlockEntity host = new OutputInterfaceBlockEntity(
                BlockPos.ZERO,
                outputState(),
                PortKinds.ITEM_OUTPUT,
                () -> network,
                source());
        host.getStorage().setStack(0, new GenericStack(AEItemKey.of(iron), 64L));
        host.getStorage().setStack(1, new GenericStack(AEFluidKey.of(water), 200L));
        IGridNode activeNode = activeNode();
        IGridTickable ticker = host.outputTicker;

        assertThat(ticker.tickingRequest(activeNode, 1)).isEqualTo(TickRateModulation.FASTER);
        assertThat(network.amount(AEItemKey.of(iron))).isEqualTo(64L);
        assertThat(network.amount(AEFluidKey.of(water))).isEqualTo(192L);
        assertThat(host.itemStorage().amount(0)).isZero();
        assertThat(host.fluidStorage().amount(1)).isEqualTo(8L);
        assertThat(network.modulatedAmount()).isEqualTo(256L);

        assertThat(ticker.tickingRequest(activeNode, 1)).isEqualTo(TickRateModulation.SLOWER);

        host.getStorage().clear();
        assertThat(ticker.getTickingRequest(activeNode).isSleeping()).isTrue();
        assertThat(ticker.tickingRequest(activeNode, 1)).isEqualTo(TickRateModulation.SLEEP);
    }

    private static BlockState outputState() {
        return ModBlocks.BLOCKS.get(PortKinds.ITEM_OUTPUT.id()).get().defaultBlockState();
    }

    private static IGridNode activeNode() {
        return (IGridNode) Proxy.newProxyInstance(IGridNode.class.getClassLoader(),
                new Class<?>[]{IGridNode.class},
                (_, method, _) -> method.getName().equals("isActive")
                        ? true : defaultValue(method.getReturnType()));
    }

    private static IActionSource source() {
        IEnergyService energy = (IEnergyService) Proxy.newProxyInstance(
                IEnergyService.class.getClassLoader(), new Class<?>[]{IEnergyService.class},
                (_, method, _) -> method.getName().equals("extractAEPower") ? 1_000_000D
                        : defaultValue(method.getReturnType()));
        IGrid[] grid = new IGrid[1];
        IGridNode node = (IGridNode) Proxy.newProxyInstance(
                IGridNode.class.getClassLoader(), new Class<?>[]{IGridNode.class},
                (_, method, _) -> method.getName().equals("getGrid") ? grid[0]
                        : defaultValue(method.getReturnType()));
        grid[0] = (IGrid) Proxy.newProxyInstance(
                IGrid.class.getClassLoader(), new Class<?>[]{IGrid.class},
                (_, method, _) -> method.getName().equals("getEnergyService") ? energy
                        : defaultValue(method.getReturnType()));
        return IActionSource.ofMachine(() -> node);
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

    private static final class FakeMEStorage implements MEStorage {
        private final Map<AEKey, Long> amounts = new HashMap<>();
        private final Map<AEKey, Long> capacities;
        private long modulatedAmount;

        private FakeMEStorage(Map<AEKey, Long> capacities) {
            this.capacities = new HashMap<>(capacities);
        }

        @Override
        public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            long current = amounts.getOrDefault(key, 0L);
            long available = Math.max(0L, capacities.getOrDefault(key, 0L) - current);
            long inserted = Math.min(amount, available);
            if (mode == Actionable.MODULATE && inserted > 0L) {
                amounts.put(key, current + inserted);
                modulatedAmount += inserted;
            }
            return inserted;
        }

        @Override
        public Component getDescription() {
            return Component.literal("test");
        }

        private long amount(AEKey key) {
            return amounts.getOrDefault(key, 0L);
        }

        private long modulatedAmount() {
            return modulatedAmount;
        }
    }
}
