package cn.howxu.mmcr.compat.jade;

import cn.howxu.mmcr.api.recipe.MachineOutputAmount;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.compat.botania.ManaOutput;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.impl.ui.ItemStackElement;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies mana stays hidden in Jade while other recipe outputs remain visible.
 * @author howxu <dev@howxu.cn>
 */
class BotaniaControllerOutputPresentationTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void controllerJadeHidesManaWithoutAnEmptyHeadingAndKeepsMixedItemOutputs() {
        try (var scope = OutputRegistry.openTestScope()) {
            OutputRegistry.register(ManaOutput.TYPE);
            CompoundTag data = new CompoundTag();
            RecipeOutputCodec.write(data, List.of(new MachineOutputAmount(new ManaOutput(10_000), Long.MAX_VALUE)));
            BlockAccessor accessor = (BlockAccessor) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{BlockAccessor.class}, (proxy, method, arguments) -> {
                        if (method.getName().equals("getServerData")) return data;
                        throw new AssertionError("Controller output must not query native inventory: " + method.getName());
                    });
            List<Component> text = new ArrayList<>();
            List<Object> elements = new ArrayList<>();
            ITooltip tooltip = (ITooltip) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{ITooltip.class}, (proxy, method, arguments) -> {
                        if (method.getName().equals("add") || method.getName().equals("append")) {
                            if (arguments[0] instanceof Component component) text.add(component);
                            else elements.add(arguments[0]);
                        }
                        return null;
                    });
            RecipeOutputComponentProvider.INSTANCE.appendTooltip(tooltip, accessor, null);
            assertThat(text).isEmpty();
            assertThat(elements).isEmpty();
            RecipeOutputCodec.write(data, List.of(
                    new MachineOutputAmount(new ManaOutput(10_000), Long.MAX_VALUE),
                    new MachineOutputAmount(new MachineOutput.ItemOutput(new ItemStack(Items.DIAMOND), 1F), 7L)));
            RecipeOutputComponentProvider.INSTANCE.appendTooltip(tooltip, accessor, null);
            assertThat(text).hasSize(2);
            assertThat(text.getFirst()).isEqualTo(Component.translatable("jade.mmcr.machine_controller.recipe_output"));
            assertThat(elements.stream().filter(ItemStackElement.class::isInstance).map(ItemStackElement.class::cast).toList())
                    .singleElement().satisfies(icon -> assertThat(icon.getItem().getItem()).isEqualTo(Items.DIAMOND));
            text.clear();
            elements.clear();
            RecipeOutputCodec.write(data, List.of());
            RecipeOutputComponentProvider.INSTANCE.appendTooltip(tooltip, accessor, null);
            assertThat(text).isEmpty();
            assertThat(elements).isEmpty();
        }
    }
}
