package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.recipe.RecipeIoValidation;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.create.CreateRecipeTypes;
import cn.howxu.mmcr.compat.create.StressRequirement;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.serialization.JsonOps;
import dev.latvian.mods.rhino.ContextFactory;
import dev.latvian.mods.rhino.ScriptableObject;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises stress helper payloads through the registered codec and real script recipe builder.
 * @author howxu <dev@howxu.cn>
 */
class CreateStressKubeJSApiTest {
    private static final ResourceLocation POOL = ResourceLocation.parse("test:stress_script_pool");

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        CreateRecipeTypes.register();
        MachineRegistry.register(new DynamicMachine(POOL, "Stress Script Machine", new BlockArray(Map.of())));
    }

    @Test
    void helper_custom_io_decodes_to_real_stress_requirements_and_preserves_tags_and_direction() {
        var api = new KubeJSApi();
        var input = api.stressInput(8, 32, List.of("drive"));
        var output = api.stressOutput(16, -64, List.of("generator"));
        var expectedInput = StressRequirement.input(8, 32, List.of("drive"));
        var expectedOutput = StressRequirement.output(16, -64, List.of("generator"));
        assertThat(RecipeIoValidation.decodeRequirement(input)).isEqualTo(expectedInput);
        assertThat(RecipeIoValidation.decodeRequirement(output)).isEqualTo(expectedOutput);
        assertThat(MachineRequirement.CODEC.parse(JsonOps.INSTANCE, output.payload()).getOrThrow()).isEqualTo(expectedOutput);
        assertThat(input.ioType()).isEqualTo(IOType.INPUT);
        assertThat(output.ioType()).isEqualTo(IOType.OUTPUT);

        var definition = MachineRecipeBuilder.recipe(ResourceLocation.parse("test:stress_custom"))
                .recipePool(POOL).custom(input).custom(output).build();
        assertThat(definition.requirements()).containsExactly(expectedInput, expectedOutput);
        assertThat(definition.customOutputs()).isEmpty();
    }

    @Test
    void rhino_helper_declarations_reach_runtime_requirements_without_output_stack_conversion() {
        var builder = new MachineRecipeBuilderJS("test:stress_rhino").recipePool(POOL.toString());
        var context = new ContextFactory().enter();
        var scope = context.initStandardObjects();
        ScriptableObject.putProperty(scope, "mmcr", new KubeJSApi(), context);
        ScriptableObject.putProperty(scope, "builder", builder, context);
        context.evaluateString(scope, """
                const demand = mmcr.stressInput(8, 32);
                const generation = mmcr.stressOutput(16, -64);
                builder.addRequirement(demand);
                builder.addRequirement(generation);
                """, "stress-helpers", 1, null);
        var recipe = builder.createObject();
        assertThat(recipe.runtimeRequirements()).containsExactly(
                StressRequirement.input(8, 32, List.of()), StressRequirement.output(16, -64, List.of()));
        assertThat(recipe.machineOutputs()).isEmpty();
    }

    @Test
    void rhino_rejects_implicit_string_and_boolean_numeric_coercion_and_preserves_tagged_helpers() {
        var builder = new MachineRecipeBuilderJS("test:stress_rhino_tags").recipePool(POOL.toString());
        var context = new ContextFactory().enter();
        var scope = context.initStandardObjects();
        ScriptableObject.putProperty(scope, "mmcr", new KubeJSApi(), context);
        ScriptableObject.putProperty(scope, "builder", builder, context);
        context.evaluateString(scope, """
                builder.addRequirement(mmcr.stressInput(8, 32, ['drive']));
                builder.addRequirement(mmcr.stressOutput(16, -64, ['generator']));
                """, "stress-tagged-helpers", 1, null);
        assertThat(builder.createObject().runtimeRequirements()).containsExactly(
                StressRequirement.input(8, 32, List.of("drive")),
                StressRequirement.output(16, -64, List.of("generator")));
        for (String source : List.of("mmcr.stressInput('8', 32)", "mmcr.stressInput(8, '32')",
                "mmcr.stressOutput('16', -64)", "mmcr.stressOutput(16, '-64')",
                "mmcr.stressOutput(16, true)", "mmcr.stressInput(null, 32)")) {
            assertThatThrownBy(() -> context.evaluateString(scope, source, "stress-invalid-number", 1, null))
                    .hasMessageContaining("Stress parameters must be numbers");
        }
    }

    @Test
    void helpers_reject_invalid_values_and_custom_io_direction_mismatch_using_real_codec() {
        var api = new KubeJSApi();
        assertThatThrownBy(() -> api.stressInput(8, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> api.stressOutput(16, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> api.stressOutput(Double.NaN, -64)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> api.customRecipeIo("create:stress", IOType.INPUT,
                api.stressOutput(16, -64).payload())).isInstanceOf(IllegalArgumentException.class);
        var malformed = api.stressOutput(16, -64).payload().getAsJsonObject();
        malformed.remove("rpm");
        assertThatThrownBy(() -> api.customRecipeIo("create:stress", IOType.OUTPUT, malformed))
                .isInstanceOf(IllegalStateException.class);
    }
}
