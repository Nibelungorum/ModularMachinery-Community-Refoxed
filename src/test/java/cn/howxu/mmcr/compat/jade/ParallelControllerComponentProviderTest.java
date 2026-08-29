package cn.howxu.mmcr.compat.jade;

import cn.howxu.mmcr.internal.block.ParallelControllerBlock;
import cn.howxu.mmcr.internal.item.InterfaceTooltips;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Jade tooltip for standalone parallel controllers.
 *
 * @author howxu <dev@howxu.cn>
 */
class ParallelControllerComponentProviderTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void jade_tooltip_matches_parallel_controller_tooltip() {
        ParallelControllerBlock block = (ParallelControllerBlock) ModBlocks.BLOCKS
                .get("parallel_controller_pro").get();
        List<Component> added = new ArrayList<>();
        ITooltip tooltip = (ITooltip) Proxy.newProxyInstance(
                ITooltip.class.getClassLoader(), new Class<?>[]{ITooltip.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("add") && args != null && args.length == 1
                            && args[0] instanceof Component component) {
                        added.add(component);
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        BlockAccessor accessor = (BlockAccessor) Proxy.newProxyInstance(
                BlockAccessor.class.getClassLoader(), new Class<?>[]{BlockAccessor.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getBlock")) return block;
                    throw new UnsupportedOperationException(method.getName());
                });

        ParallelControllerComponentProvider.INSTANCE.appendTooltip(tooltip, accessor, null);

        assertThat(added).containsExactlyElementsOf(InterfaceTooltips.tooltipLines(block));
    }
}
