package cn.howxu.mmcr.compat.jade;

import cn.howxu.mmcr.api.recipe.MachineOutputAmount;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.compat.botania.ManaOutput;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.impl.ui.ItemStackElement;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the transported recipe total and decorative icon without querying pool inventory.
 * @author howxu <dev@howxu.cn>
 */
class BotaniaControllerOutputPresentationTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        ResourceLocation id = ResourceLocation.parse("botania:creative_pool");
        MappedRegistry<Item> items = (MappedRegistry<Item>) BuiltInRegistries.ITEM;
        if (!items.containsKey(id)) {
            items.unfreeze();
            try { Registry.register(items, id, new Item(new Item.Properties())); }
            finally { items.freeze(); }
        }
    }

    @Test
    void controllerJadeUsesOnlyServerRecipeOutputAndPreservesExactLongManaWithCreativePoolIcon() {
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
            assertThat(text).containsExactly(Component.translatable("jade.mmcr.machine_controller.recipe_output"),
                    Component.translatable("gui.mmcr.mana.exact", "9,223,372,036,854,775,807"));
            assertThat(elements.stream().filter(ItemStackElement.class::isInstance).map(ItemStackElement.class::cast).toList())
                    .singleElement().satisfies(icon -> {
                        assertThat(BuiltInRegistries.ITEM.getKey(icon.getItem().getItem())).isEqualTo(ResourceLocation.parse("botania:creative_pool"));
                        assertThat(icon.getItem().getCount()).isEqualTo(1);
                    });
            text.clear();
            elements.clear();
            RecipeOutputCodec.write(data, List.of());
            RecipeOutputComponentProvider.INSTANCE.appendTooltip(tooltip, accessor, null);
            assertThat(text).isEmpty();
            assertThat(elements).isEmpty();
        }
    }
}
