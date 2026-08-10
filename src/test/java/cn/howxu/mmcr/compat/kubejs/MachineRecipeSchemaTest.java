package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.level.LevelModifier;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import com.google.gson.JsonElement;
import dev.latvian.mods.kubejs.recipe.component.ListRecipeComponent;
import dev.latvian.mods.kubejs.recipe.component.StringComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

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

    @Test
    void builder_rejects_level_outside_declared_type() {
        var coilType = Identifier.parse("test:coil");
        var laserType = Identifier.parse("test:laser");
        MachineLevelRegistry.beginRegistration();
        MachineLevelRegistry.registerType(new LevelType(coilType, Component.literal("Coils")));
        MachineLevelRegistry.registerType(new LevelType(laserType, Component.literal("Lasers")));
        MachineLevelRegistry.registerLevel(new MachineLevel(Identifier.parse("test:laser"), laserType, 1,
                new BlockPredicate.OfBlockState(Blocks.GOLD_BLOCK.defaultBlockState()), ItemStack.EMPTY, LevelModifier.IDENTITY));

        assertThatIllegalArgumentException().isThrownBy(
                () -> new MachineRecipeBuilderJS("test:recipe").requiresLevel("test:coil", "test:laser"));
    }

    @Test
    void schema_exposes_requires_level_function_with_two_string_arguments() {
        var function = MachineRecipeSchema.SCHEMA.functions.get("requiresLevel");

        assertThat(function.arguments()).containsExactly(StringComponent.ID, StringComponent.ID);
    }
}
