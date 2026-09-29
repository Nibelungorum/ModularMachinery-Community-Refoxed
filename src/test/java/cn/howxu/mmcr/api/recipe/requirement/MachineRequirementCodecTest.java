package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.test.TestBootstrap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author howxu <dev@howxu.cn>
 */
class MachineRequirementCodecTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        Fluids.WATER.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
    }

    @Test
    void codec_round_trips_all_builtin_requirement_types() {
        List<MachineRequirement> requirements = List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 2, ItemStack.EMPTY),
                new FluidRequirement(RecipeModifier.IOType.INPUT, FluidIngredient.of(Fluids.WATER), 250, FluidStack.EMPTY),
                new EnergyRequirement(RecipeModifier.IOType.INPUT, 40),
                SmartInterfaceRequirement.input("mode", 1F, 2F),
                LevelRequirement.input(ResourceLocation.parse("test:coil"), ResourceLocation.parse("test:kanthal")),
                StageRequirement.input(2));
        DynamicOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE,
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));

        for (MachineRequirement requirement : requirements) {
            JsonElement encoded = MachineRequirement.CODEC.encodeStart(ops, requirement).getOrThrow();
            assertThat(MachineRequirement.CODEC.parse(ops, encoded).getOrThrow()).isEqualTo(requirement);
            assertThat(RequirementHandlerRegistry.handlerFor(requirement.type())).isNotNull();
        }

        LevelRequirement level = LevelRequirement.input(ResourceLocation.parse("test:coil"), ResourceLocation.parse("test:kanthal"));
        JsonElement encoded = MachineRequirement.CODEC.encodeStart(ops, level).getOrThrow();
        assertThat(encoded.getAsJsonObject().get("type").getAsString()).isEqualTo("mmcr:level");
        assertThat(encoded.getAsJsonObject().get("level_type").getAsString()).isEqualTo("test:coil");
        assertThat(encoded.getAsJsonObject().get("level").getAsString()).isEqualTo("test:kanthal");

        StageRequirement stage = StageRequirement.input(2);
        encoded = MachineRequirement.CODEC.encodeStart(ops, stage).getOrThrow();
        JsonObject expected = new JsonObject();
        expected.addProperty("type", "mmcr:stage");
        expected.addProperty("io", "input");
        expected.addProperty("min_stage", 2);
        assertThat(encoded).isEqualTo(expected);
    }

    @Test
    void codec_rejects_an_unknown_requirement_type() {
        JsonObject encoded = new JsonObject();
        encoded.addProperty("type", "mmcr:unknown");

        var result = MachineRequirement.CODEC.parse(JsonOps.INSTANCE, encoded);

        assertThat(result.error()).isPresent();
    }

    @Test
    void codec_rejects_an_output_level_requirement() {
        JsonObject encoded = new JsonObject();
        encoded.addProperty("type", "mmcr:level");
        encoded.addProperty("io", "output");
        encoded.addProperty("level_type", "test:coil");
        encoded.addProperty("level", "test:kanthal");

        assertThatThrownBy(() -> MachineRequirement.CODEC.parse(JsonOps.INSTANCE, encoded))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Level requirements must use input direction");
    }

    @Test
    void codec_defaults_an_omitted_level_requirement_io_to_input() {
        JsonObject encoded = new JsonObject();
        encoded.addProperty("type", "mmcr:level");
        encoded.addProperty("level_type", "test:coil");
        encoded.addProperty("level", "test:kanthal");

        assertThat(MachineRequirement.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow())
                .isEqualTo(LevelRequirement.input(ResourceLocation.parse("test:coil"), ResourceLocation.parse("test:kanthal")));
    }

    @Test
    void stage_requirement_rejects_non_positive_minimum_stage() {
        assertThatThrownBy(() -> StageRequirement.input(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Stage minimum must be in [1, 64]");
    }

    @Test
    void stage_requirement_rejects_minimum_stage_above_64() {
        assertThatThrownBy(() -> StageRequirement.input(65))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Stage minimum must be in [1, 64]");
    }

    @Test
    void codec_rejects_an_output_stage_requirement() {
        JsonObject encoded = new JsonObject();
        encoded.addProperty("type", "mmcr:stage");
        encoded.addProperty("io", "output");
        encoded.addProperty("min_stage", 2);

        assertThatThrownBy(() -> MachineRequirement.CODEC.parse(JsonOps.INSTANCE, encoded))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Stage requirements must use input direction");
    }

    @Test
    void level_handler_ignores_capabilities_and_preserves_requested_parallelism() {
        var plan = new LevelRequirementHandler().plan(
                LevelRequirement.input(ResourceLocation.parse("test:coil"), ResourceLocation.parse("test:kanthal")),
                Collections.singletonList(null), new PlanningContext(4, 2));

        assertThat(plan.successful()).isTrue();
        assertThat(plan.requirementIndex()).isEqualTo(2);
        assertThat(plan.maxParallelism()).isEqualTo(4);
        assertThat(plan.operations()).isEmpty();
    }

    @Test
    void stage_handler_ignores_capabilities_and_preserves_requested_parallelism() {
        var plan = new StageRequirementHandler().plan(StageRequirement.input(2),
                Collections.singletonList(null), new PlanningContext(4, 2));

        assertThat(plan.successful()).isTrue();
        assertThat(plan.requirementIndex()).isEqualTo(2);
        assertThat(plan.maxParallelism()).isEqualTo(4);
        assertThat(plan.operations()).isEmpty();
    }

    @Test
    void test_scope_preserves_builtin_requirement_registrations() {
        RequirementHandlerRegistry.registerBuiltIns();

        try (var ignored = RequirementHandlerRegistry.openTestScope()) {
            assertThat(RequirementHandlerRegistry.typeFor(ItemRequirement.TYPE.id()))
                    .isSameAs(ItemRequirement.TYPE);
            assertThat(RequirementHandlerRegistry.typeFor(EnergyRequirement.TYPE.id()))
                    .isSameAs(EnergyRequirement.TYPE);
        }

        assertThat(RequirementHandlerRegistry.typeFor(ItemRequirement.TYPE.id()))
                .isSameAs(ItemRequirement.TYPE);
    }
}
