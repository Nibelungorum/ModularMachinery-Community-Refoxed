package cn.howxu.mmcr.compat.kubejs;

import com.google.gson.JsonElement;
import dev.latvian.mods.kubejs.recipe.component.ListRecipeComponent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author howxu <dev@howxu.cn>
 */
class MachineRecipeSchemaTest {

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
}
