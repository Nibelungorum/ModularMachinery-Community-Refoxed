package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.test.TestBootstrap;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.latvian.mods.kubejs.recipe.component.ListRecipeComponent;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author howxu <dev@howxu.cn>
 */
class MachineRecipeSchemaTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }


    @Test
    void schema_exposes_modifiers_as_excluded_optional_raw_json_list() {
        var modifiers = MachineRecipeSchema.MODIFIERS;

        assertThat(MachineRecipeSchema.SCHEMA.keys).contains(modifiers);
        assertThat(MachineRecipeSchema.SCHEMA.includedKeys).doesNotContain(modifiers);
        assertThat(modifiers.name).isEqualTo("modifiers");
        assertThat(modifiers.excluded).isTrue();
        assertThat(modifiers.optional()).isTrue();
        assertThat(modifiers.optional.getInformativeValue()).isEqualTo(List.of());
        assertThat(modifiers.component).isInstanceOfSatisfying(ListRecipeComponent.class, component -> {
            assertThat(component.component()).isSameAs(MachineRecipeSchema.JSON_ELEMENT);
            assertThat(component.codec().parse(com.mojang.serialization.JsonOps.INSTANCE, new com.google.gson.JsonArray()).getOrThrow())
                    .isInstanceOf(List.class);
        });
        assertThat(MachineRecipeSchema.JSON_ELEMENT.typeInfo().asClass()).isEqualTo(JsonElement.class);
    }

    @Test
    void schema_exposes_parallel_opt_in_keys() {
        assertThat(MachineRecipeSchema.SCHEMA.keys).contains(
                MachineRecipeSchema.PARALLELIZED,
                MachineRecipeSchema.MAX_THREADS);
        assertThat(MachineRecipeSchema.PARALLELIZED.name).isEqualTo("parallelized");
        assertThat(MachineRecipeSchema.PARALLELIZED.optional()).isTrue();
        assertThat(MachineRecipeSchema.PARALLELIZED.optional.getInformativeValue()).isEqualTo(false);
        assertThat(MachineRecipeSchema.MAX_THREADS.name).isEqualTo("max_threads");
        assertThat(MachineRecipeSchema.MAX_THREADS.optional()).isTrue();
        assertThat(MachineRecipeSchema.MAX_THREADS.optional.getInformativeValue()).isEqualTo(1);
    }

    @Test
    void builder_creates_component_bearing_item_output() {
        Items.DIAMOND_SWORD.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        MachineRecipeBuilderJS builder = new MachineRecipeBuilderJS(MMCR.id("better_sword"));

        builder.itemOutputWithComponents("minecraft:diamond_sword", 1, json("""
                {
                  'minecraft:custom_name': { text: 'Better钻石剑' }
                }
                """));

        var output = builder.outputs.getFirst();
        assertThat(output.getHoverName().getString()).isEqualTo("Better钻石剑");
        assertThat(output.get(DataComponents.CUSTOM_NAME)).isNotNull();
        assertThatThrownBy(() -> builder.itemOutputWithComponents("minecraft:diamond_sword", 1,
                json("{ 'minecraft:custom_name': 42 }")))
                .isInstanceOf(IllegalStateException.class);
    }

    private static JsonElement json(String value) {
        return JsonParser.parseString(value);
    }
}
