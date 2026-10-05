package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.recipe.CustomRecipeIo;
import cn.howxu.mmcr.api.recipe.RecipeIoValidation;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.compat.pneumaticcraft.AirRequirement;
import cn.howxu.mmcr.compat.pneumaticcraft.PneumaticIds;
import cn.howxu.mmcr.compat.pneumaticcraft.PneumaticRecipeTypes;
import cn.howxu.mmcr.test.TestBootstrap;
import dev.latvian.mods.rhino.ContextFactory;
import dev.latvian.mods.rhino.ScriptableObject;
import dev.latvian.mods.rhino.Wrapper;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real Rhino conversion and canonical payload tests for script air declarations.
 * @author howxu <dev@howxu.cn>
 */
class PneumaticAirScriptTest {
    private static final ResourceLocation POOL = ResourceLocation.parse("test:pneumatic_script_pool");

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        PneumaticRecipeTypes.register();
        MachineRegistry.register(new DynamicMachine(POOL, "Pneumatic Script Machine", new BlockArray(Map.of())));
    }

    @Test
    void java_helpers_and_payloads_decode_to_canonical_requirements_without_custom_outputs() {
        var api = new KubeJSApi();
        var input = api.airInput(10_000_000_000L, 4.25F, List.of("drive"));
        var output = api.airOutput(Long.MAX_VALUE, List.of("generator"));
        var expectedInput = AirRequirement.input(10_000_000_000L, 4.25F, List.of("drive"));
        var expectedOutput = AirRequirement.output(Long.MAX_VALUE, List.of("generator"));

        assertThat(RecipeIoValidation.decodeRequirement(input)).isEqualTo(expectedInput);
        assertThat(RecipeIoValidation.decodeRequirement(output)).isEqualTo(expectedOutput);
        assertThat(input.ioType()).isEqualTo(IOType.INPUT);
        assertThat(output.ioType()).isEqualTo(IOType.OUTPUT);
        assertThat(output.payload().getAsJsonObject().has("min_pressure")).isFalse();
        var definition = MachineRecipeBuilder.recipe(ResourceLocation.parse("test:air_custom"))
                .recipePool(POOL).custom(input).custom(output).build();
        assertThat(definition.requirements()).containsExactly(expectedInput, expectedOutput);
        assertThat(definition.customOutputs()).isEmpty();
        assertThatThrownBy(() -> api.customRecipeIo(PneumaticIds.AIR.toString(), IOType.INPUT, output.payload()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rhino_helpers_keep_long_rates_fractional_pressure_and_tags_through_the_script_builder() {
        var context = new ContextFactory().enter();
        var scope = context.initStandardObjects();
        var builder = new MachineRecipeBuilderJS("test:pneumatic_rhino").recipePool(POOL.toString());
        ScriptableObject.putProperty(scope, "mmcr", new KubeJSApi(), context);
        ScriptableObject.putProperty(scope, "builder", builder, context);
        context.evaluateString(scope, """
                builder.addRequirement(mmcr.airInput(10000000000, 4.25, ['drive']));
                builder.addRequirement(mmcr.airOutput(10000000001, ['generator']));
                builder.addRequirement(mmcr.airInput(0, 8.5));
                builder.addRequirement(mmcr.airOutput(0));
                builder.addRequirement(mmcr.airOutput(9223372036854774784));
                """, "pneumatic-helpers", 1, null);

        var recipe = builder.createObject();
        assertThat(recipe.runtimeRequirements()).containsExactly(
                AirRequirement.input(10_000_000_000L, 4.25F, List.of("drive")),
                AirRequirement.output(10_000_000_001L, List.of("generator")),
                AirRequirement.input(0, 8.5F), AirRequirement.output(0),
                AirRequirement.output(9_223_372_036_854_774_784L));
        assertThat(recipe.machineOutputs()).isEmpty();
    }

    @Test
    void real_rhino_expression_results_decode_via_recipe_io_validation() {
        var context = new ContextFactory().enter();
        var scope = context.initStandardObjects();
        ScriptableObject.putProperty(scope, "mmcr", new KubeJSApi(), context);
        Object inputValue = context.evaluateString(scope, "mmcr.airInput(10000000000, 4.25, ['drive'])",
                "pneumatic-input-expression", 1, null);
        Object outputValue = context.evaluateString(scope, "mmcr.airOutput(10000000001, ['generator'])",
                "pneumatic-output-expression", 1, null);
        var input = (CustomRecipeIo) ((Wrapper) inputValue).unwrap();
        var output = (CustomRecipeIo) ((Wrapper) outputValue).unwrap();
        assertThat(RecipeIoValidation.decodeRequirement(input)).isEqualTo(AirRequirement.input(10_000_000_000L, 4.25F, List.of("drive")));
        assertThat(RecipeIoValidation.decodeRequirement(output)).isEqualTo(AirRequirement.output(10_000_000_001L, List.of("generator")));
        assertThat(input.ioType()).isEqualTo(IOType.INPUT);
        assertThat(output.ioType()).isEqualTo(IOType.OUTPUT);
    }

    @Test
    void rhino_rejects_coerced_fractional_nonfinite_negative_and_out_of_long_range_rates() {
        var context = new ContextFactory().enter();
        var scope = context.initStandardObjects();
        ScriptableObject.putProperty(scope, "mmcr", new KubeJSApi(), context);
        for (String argument : List.of("'40'", "true", "false", "null", "undefined", "0.5", "-1", "NaN",
                "Infinity", "-Infinity", "9223372036854775808", "1e30")) {
            for (String expression : List.of("mmcr.airInput(" + argument + ", 4)",
                    "mmcr.airInput(" + argument + ", 4, ['drive'])",
                    "mmcr.airOutput(" + argument + ")", "mmcr.airOutput(" + argument + ", ['generator'])")) {
                assertThatThrownBy(() -> context.evaluateString(scope, expression, "air-invalid-rate", 1, null))
                        .as(expression).hasMessageContaining("Air rate must be");
            }
        }
    }

    @Test
    void rhino_rejects_non_numeric_pressure_and_float_overflow_after_conversion() {
        var context = new ContextFactory().enter();
        var scope = context.initStandardObjects();
        ScriptableObject.putProperty(scope, "mmcr", new KubeJSApi(), context);
        for (String argument : List.of("'4'", "true", "null", "undefined", "-0.25", "NaN", "Infinity", "1e39")) {
            String expression = "mmcr.airInput(40, " + argument + ")";
            assertThatThrownBy(() -> context.evaluateString(scope, expression, "air-invalid-pressure", 1, null))
                    .as(expression).hasMessageContaining("Air pressure must be");
        }
    }

    @Test
    void object_boundary_preserves_exact_java_long_and_big_numbers_without_double_rounding() {
        var api = new KubeJSApi();
        for (Object rate : List.of(Long.MAX_VALUE, BigInteger.valueOf(Long.MAX_VALUE), new BigDecimal(Long.MAX_VALUE), new AtomicLong(Long.MAX_VALUE))) {
            assertThat(RecipeIoValidation.decodeRequirement(api.airOutput(rate))).isEqualTo(AirRequirement.output(Long.MAX_VALUE));
        }
        for (Object rate : List.of(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE),
                new BigDecimal("9223372036854775807.5"), new BigDecimal("0.5"))) {
            assertThatThrownBy(() -> api.airOutput(rate)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
