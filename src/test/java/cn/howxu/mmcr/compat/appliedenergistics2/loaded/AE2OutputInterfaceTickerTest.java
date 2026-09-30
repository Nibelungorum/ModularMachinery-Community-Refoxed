package cn.howxu.mmcr.compat.appliedenergistics2.loaded;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import cn.howxu.mmcr.compat.appliedenergistics2.AE2TestFixtures;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.OutputInterfaceBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
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
        AE2TestFixtures.ensureAE2KeyTypesInitialized();
        AE2TestFixtures.bindAE2InterfaceItem();
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
                (proxy, method, args) -> method.getName().equals("isActive")
                        ? active : defaultValue(method.getReturnType()));
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
