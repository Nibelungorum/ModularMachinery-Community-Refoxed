package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.compat.ars_nouveau.ArsNouveauRecipeTypes;
import cn.howxu.mmcr.compat.ars_nouveau.SourceOutput;
import cn.howxu.mmcr.compat.ars_nouveau.SourceRecipeDeclarations;
import cn.howxu.mmcr.compat.ars_nouveau.SourceRequirement;
import cn.howxu.mmcr.compat.botania.BotaniaRecipeTypes;
import cn.howxu.mmcr.compat.botania.ManaOutput;
import cn.howxu.mmcr.compat.botania.ManaRecipeDeclarations;
import cn.howxu.mmcr.compat.botania.ManaRequirement;
import cn.howxu.mmcr.compat.pneumaticcraft.AirRequirement;
import cn.howxu.mmcr.compat.pneumaticcraft.PneumaticRecipeTypes;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.serialization.JsonOps;
import dev.latvian.mods.rhino.ContextFactory;
import dev.latvian.mods.rhino.ScriptableObject;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/** Real Rhino routing for optional resource probabilities, including strict air numbers.
 * @author howxu <dev@howxu.cn>
 */
class CompatChanceJSTest {
    private static final long AMOUNT = 3_000_000_000L;
    private static final ResourceLocation ID = ResourceLocation.parse("test:compat_chance_js");

    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void rhino_api_direct_and_custom_builders_preserve_probability_amount_pressure_and_tags() {
        try (var requirements = RequirementHandlerRegistry.openTestScope();
             var outputs = OutputRegistry.openTestScope()) {
            register();
            for (float chance : new float[]{0F, 0.25F, 1F}) {
                var apiBuilder = builder();
                var directBuilder = builder();
                var genericBuilder = builder();
                var context = new ContextFactory().enter();
                var scope = context.initStandardObjects();
                ScriptableObject.putProperty(scope, "api", new KubeJSApi(), context);
                ScriptableObject.putProperty(scope, "apiBuilder", apiBuilder, context);
                ScriptableObject.putProperty(scope, "directBuilder", directBuilder, context);
                ScriptableObject.putProperty(scope, "genericBuilder", genericBuilder, context);
                ScriptableObject.putProperty(scope, "chance", chance, context);
                ScriptableObject.putProperty(scope, "input", IOType.INPUT, context);
                ScriptableObject.putProperty(scope, "output", IOType.OUTPUT, context);
                ScriptableObject.putProperty(scope, "sourceInput", SourceRecipeDeclarations.inputPayload(AMOUNT, chance), context);
                ScriptableObject.putProperty(scope, "sourceOutput", SourceRecipeDeclarations.outputPayload(AMOUNT, chance), context);
                ScriptableObject.putProperty(scope, "manaInput", ManaRecipeDeclarations.inputPayload(AMOUNT, chance), context);
                ScriptableObject.putProperty(scope, "manaOutput", ManaRecipeDeclarations.outputPayload(AMOUNT, chance), context);
                ScriptableObject.putProperty(scope, "airInput", MachineRecipeBuilder.airInputPayload(AMOUNT, 4F, chance, List.of("drive")), context);
                ScriptableObject.putProperty(scope, "airOutput", MachineRecipeBuilder.airOutputPayload(AMOUNT, chance, List.of("generator")), context);
                context.evaluateString(scope, """
                        apiBuilder.addRequirement(api.sourceInput(3000000000, chance));
                        apiBuilder.addRequirement(api.sourceOutput(3000000000, chance));
                        apiBuilder.addRequirement(api.manaInput(3000000000, chance));
                        apiBuilder.addRequirement(api.manaOutput(3000000000, chance));
                        apiBuilder.addRequirement(api.airInput(3000000000, 4, chance, ['drive']));
                        apiBuilder.addRequirement(api.airOutput(3000000000, chance, ['generator']));
                        directBuilder.inputSource(3000000000, chance).outputSource(3000000000, chance)
                            .inputMana(3000000000, chance).outputMana(3000000000, chance)
                            .inputAir(3000000000, 4, chance, ['drive']).outputAir(3000000000, chance, ['generator']);
                        genericBuilder.custom('ars_nouveau:source', input, sourceInput)
                            .custom('ars_nouveau:source', output, sourceOutput)
                            .custom('botania:mana', input, manaInput).custom('botania:mana', output, manaOutput)
                            .custom('pneumaticcraft:air', input, airInput).custom('pneumaticcraft:air', output, airOutput);
                        """, "compat-chance-routing", 1, null);
                var expected = apiBuilder.createObject();
                assertThat(expected.requirements()).contains(SourceRequirement.input(AMOUNT, chance), SourceRequirement.output(AMOUNT, chance),
                        ManaRequirement.input(AMOUNT, chance), ManaRequirement.output(AMOUNT, chance),
                        AirRequirement.input(AMOUNT, 4F, chance, List.of("drive")), AirRequirement.output(AMOUNT, chance, List.of("generator")));
                assertThat(expected.machineOutputs()).containsExactly(new SourceOutput(AMOUNT).withChance(chance), new ManaOutput(AMOUNT).withChance(chance));
                for (var candidate : List.of(directBuilder, genericBuilder)) {
                    var recipe = candidate.createObject();
                    assertThat(recipe.requirements()).isEqualTo(expected.requirements());
                    assertThat(recipe.machineOutputs()).isEqualTo(expected.machineOutputs());
                }
            }
        }
    }

    @Test
    void rhino_typed_requirements_preserve_chance_and_tags_through_canonical_codecs() {
        try (var requirements = RequirementHandlerRegistry.openTestScope();
             var outputs = OutputRegistry.openTestScope()) {
            register();
            for (float chance : new float[]{0F, 0.25F, 1F}) {
                var builder = builder();
                var context = new ContextFactory().enter();
                var scope = context.initStandardObjects();
                ScriptableObject.putProperty(scope, "api", new KubeJSApi(), context);
                ScriptableObject.putProperty(scope, "builder", builder, context);
                ScriptableObject.putProperty(scope, "chance", chance, context);
                context.evaluateString(scope, """
                        builder.addRequirement(api.sourceInputRequirement(3000000000, chance, ['drive']));
                        builder.addRequirement(api.sourceOutputRequirement(3000000000, chance, ['drive']));
                        builder.addRequirement(api.manaInputRequirement(3000000000, chance, ['drive']));
                        builder.addRequirement(api.manaOutputRequirement(3000000000, chance, ['drive']));
                        builder.addRequirement(api.airInputRequirement(3000000000, 4, chance, ['drive']));
                        builder.addRequirement(api.airOutputRequirement(3000000000, chance, ['drive']));
                        """, "compat-chance-typed", 1, null);
                var tags = List.of("drive");
                assertThat(builder.requirements).containsExactly(new SourceRequirement(IOType.INPUT, AMOUNT, 1F, chance, tags),
                        new SourceRequirement(IOType.OUTPUT, AMOUNT, chance, 1F, tags), new ManaRequirement(IOType.INPUT, AMOUNT, 1F, chance, tags),
                        new ManaRequirement(IOType.OUTPUT, AMOUNT, chance, 1F, tags), AirRequirement.input(AMOUNT, 4F, chance, tags), AirRequirement.output(AMOUNT, chance, tags));
                for (var requirement : builder.requirements) {
                    var encoded = MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE, requirement).getOrThrow();
                    assertThat(MachineRequirement.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow()).isEqualTo(requirement);
                }
            }
        }
    }

    @Test
    void rhino_numeric_probability_literals_are_not_coerced_to_tag_lists() {
        try (var requirements = RequirementHandlerRegistry.openTestScope();
             var outputs = OutputRegistry.openTestScope()) {
            register();
            var builder = builder();
            var context = new ContextFactory().enter();
            var scope = context.initStandardObjects();
            ScriptableObject.putProperty(scope, "api", new KubeJSApi(), context);
            ScriptableObject.putProperty(scope, "builder", builder, context);
            context.evaluateString(scope, """
                    builder.addRequirement(api.sourceInputRequirement(10, .25));
                    builder.addRequirement(api.sourceOutputRequirement(10, .25));
                    builder.addRequirement(api.manaInputRequirement(10, .25));
                    builder.addRequirement(api.manaOutputRequirement(10, .25));
                    builder.addRequirement(api.airInputRequirement(10, 4, .25));
                    builder.addRequirement(api.airOutputRequirement(10, .25));
                    builder.addRequirement(api.airInput(10, 4, .25));
                    builder.addRequirement(api.airOutput(10, .25));
                    builder.inputAir(10, 4, .25).outputAir(10, .25);
                    """, "compat-chance-literals", 1, null);
            assertThat(builder.requirements).containsExactly(SourceRequirement.input(10L, .25F),
                    SourceRequirement.output(10L, .25F), ManaRequirement.input(10L, .25F),
                    ManaRequirement.output(10L, .25F), AirRequirement.input(10L, 4F, .25F),
                    AirRequirement.output(10L, .25F), AirRequirement.input(10L, 4F, .25F),
                    AirRequirement.output(10L, .25F), AirRequirement.input(10L, 4F, .25F),
                    AirRequirement.output(10L, .25F));
        }
    }

    @Test
    void old_rhino_air_overloads_keep_validation_and_new_probability_overloads_do_not_coerce_rate_or_pressure() {
        try (var requirements = RequirementHandlerRegistry.openTestScope();
             var outputs = OutputRegistry.openTestScope()) {
            register();
            var builder = builder();
            var context = new ContextFactory().enter();
            var scope = context.initStandardObjects();
            ScriptableObject.putProperty(scope, "api", new KubeJSApi(), context);
            ScriptableObject.putProperty(scope, "builder", builder, context);
            context.evaluateString(scope, """
                    builder.inputSource(3000000000).outputSource(3000000000).inputMana(3000000000).outputMana(3000000000);
                    builder.addRequirement(api.airInput(0, 4, ['drive']));
                    builder.addRequirement(api.airOutput(3000000000, ['generator']));
                    """, "compat-chance-defaults", 1, null);
            var recipe = builder.createObject();
            assertThat(recipe.requirements()).contains(SourceRequirement.input(AMOUNT), SourceRequirement.output(AMOUNT),
                    ManaRequirement.input(AMOUNT), ManaRequirement.output(AMOUNT), AirRequirement.input(0, 4F, List.of("drive")), AirRequirement.output(AMOUNT, List.of("generator")));
            assertThat(recipe.machineOutputs()).containsExactly(new SourceOutput(AMOUNT), new ManaOutput(AMOUNT));
            for (String invalid : List.of("api.airInput('10', 4)", "api.airInput(10, '4')", "api.airInput(1.5, 4, .25)",
                    "api.airInput('10', 4, .25)", "api.airInput(10, '4', .25)", "api.airOutput('10', .25)",
                    "builder.inputAir('10', 4, .25)", "builder.outputAir(1.5, .25)", "api.sourceInput(10, -.1)",
                    "api.sourceOutput(10, 1.1)", "api.manaInput(10, NaN)", "api.manaOutput(10, Infinity)",
                    "api.airInput(10, 4, -.1)", "api.airOutput(10, 1.1)", "builder.inputAir(10, 4, -.1)",
                    "builder.outputAir(10, 1.1)", "api.sourceInputRequirement(10, -.1)",
                    "api.manaOutputRequirement(10, 1.1)", "api.airInputRequirement(10, 4, -.1)")) {
                assertThat(catchThrowable(() -> context.evaluateString(scope, invalid, "invalid-compat-chance", 1, null)))
                        .as(invalid).isNotNull();
            }
        }
    }

    private static void register() {
        ArsNouveauRecipeTypes.register();
        BotaniaRecipeTypes.register();
        PneumaticRecipeTypes.register();
    }

    private static MachineRecipeBuilderJS builder() {
        var builder = new MachineRecipeBuilderJS(ID);
        builder.recipePoolId = ID;
        return builder;
    }
}
