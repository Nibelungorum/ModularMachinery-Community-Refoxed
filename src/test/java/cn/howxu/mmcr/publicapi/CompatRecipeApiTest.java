package cn.howxu.mmcr.publicapi;

import cn.howxu.mmcr.api.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.compat.ars_nouveau.ArsNouveauRecipeTypes;
import cn.howxu.mmcr.compat.ars_nouveau.SourceRequirement;
import cn.howxu.mmcr.compat.botania.BotaniaRecipeTypes;
import cn.howxu.mmcr.compat.botania.ManaRequirement;
import cn.howxu.mmcr.compat.create.CreateRecipeTypes;
import cn.howxu.mmcr.compat.pneumaticcraft.PneumaticRecipeTypes;
import cn.howxu.mmcr.compat.kubejs.KubeJSApi;
import cn.howxu.mmcr.compat.kubejs.MachineRecipeBuilderJS;
import cn.howxu.mmcr.internal.api.facade.recipe.RecipeAdapters;
import cn.howxu.mmcr.internal.api.facade.recipe.RequirementAdapters;
import cn.howxu.mmcr.publicapi.recipe.CreateIo;
import cn.howxu.mmcr.publicapi.recipe.PneumaticCraftIo;
import cn.howxu.mmcr.publicapi.recipe.IoDirection;
import cn.howxu.mmcr.publicapi.recipe.requirement.ManaRequirementSpec;
import cn.howxu.mmcr.publicapi.recipe.requirement.Requirements;
import cn.howxu.mmcr.publicapi.recipe.requirement.SourceRequirementSpec;
import cn.howxu.mmcr.test.TestBootstrap;
import dev.latvian.mods.rhino.ContextFactory;
import dev.latvian.mods.rhino.ScriptableObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises neutral compatibility declarations through the real public recipe facade.
 * @author howxu <dev@howxu.cn>
 */
class CompatRecipeApiTest {
    private static final ResourceLocation ID = ResourceLocation.parse("test:compat_api");
    private static final long AMOUNT = 3_000_000_000L;

    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void create_and_air_io_keep_canonical_direction_tags_and_native_parameters() {
        try (var requirements = RequirementHandlerRegistry.openTestScope()) {
            CreateRecipeTypes.register();
            PneumaticRecipeTypes.register();
            var direct = MachineRecipeBuilder.recipe(ID).recipePool(ID)
                    .inputStress(8, 32, List.of("drive")).outputStress(16, -64, List.of("generator"))
                    .inputAir(AMOUNT, 4, List.of("air")).outputAir(80, List.of("exhaust")).build();
            var publicRecipe = Recipes.recipe(ID).recipePool(ID)
                    .custom(CreateIo.stressInput(8, 32, List.of("drive")))
                    .custom(CreateIo.stressOutput(16, -64, List.of("generator")))
                    .custom(PneumaticCraftIo.airInput(AMOUNT, 4, List.of("air")))
                    .custom(PneumaticCraftIo.airOutput(80, List.of("exhaust"))).build();
            assertThat(RecipeAdapters.unwrap(publicRecipe).requirements()).isEqualTo(direct.requirements());
            assertThat(RecipeAdapters.unwrap(publicRecipe).customOutputs()).isEmpty();
            assertThat(CreateIo.stressInput(8, 0).io()).isEqualTo(IoDirection.INPUT);
            assertThat(CreateIo.stressOutput(8, -64).io()).isEqualTo(IoDirection.OUTPUT);
            assertThat(PneumaticCraftIo.airInput(0, 8).payload().getAsJsonObject().get("min_pressure").getAsFloat()).isEqualTo(8);
            assertThat(PneumaticCraftIo.airOutput(0).io()).isEqualTo(IoDirection.OUTPUT);
            assertThatThrownBy(() -> CreateIo.stressOutput(8, 0)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> PneumaticCraftIo.airInput(-1, 4)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> PneumaticCraftIo.airInput(1, Float.NaN)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void source_and_mana_drafts_preserve_core_output_routing_and_typed_requirement_views() {
        try (var requirements = RequirementHandlerRegistry.openTestScope();
             var outputs = OutputRegistry.openTestScope()) {
            ArsNouveauRecipeTypes.register();
            BotaniaRecipeTypes.register();
            var direct = MachineRecipeBuilder.recipe(ID).recipePool(ID)
                    .inputSource(AMOUNT).outputSource(AMOUNT).inputMana(AMOUNT).outputMana(AMOUNT).build();
            var recipe = Recipes.recipe(ID).recipePool(ID)
                    .inputSource(AMOUNT).outputSource(AMOUNT).inputMana(AMOUNT).outputMana(AMOUNT).build();
            var core = RecipeAdapters.unwrap(recipe);
            assertThat(core.requirements()).isEqualTo(direct.requirements());
            assertThat(core.customOutputs()).hasSize(2);
            for (int index = 0; index < 2; index++) {
                assertThat(core.customOutputs().get(index).payload()).isEqualTo(direct.customOutputs().get(index).payload());
            }
            assertThat(recipe.requirements().get(0)).isInstanceOf(SourceRequirementSpec.class);
            assertThat(recipe.requirements().get(1)).isInstanceOf(ManaRequirementSpec.class);
            assertThat(((SourceRequirementSpec) recipe.requirements().get(0)).amount()).isEqualTo(AMOUNT);
            assertThat(((ManaRequirementSpec) recipe.requirements().get(1)).amount()).isEqualTo(AMOUNT);
            assertThatThrownBy(() -> Recipes.recipe(ID).inputSource(0)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Recipes.recipe(ID).outputMana(-1)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void tagged_total_requirements_and_copies_retain_types_and_protect_tags() {
        try (var requirements = RequirementHandlerRegistry.openTestScope();
             var outputs = OutputRegistry.openTestScope()) {
            ArsNouveauRecipeTypes.register();
            BotaniaRecipeTypes.register();
            var tags = new ArrayList<>(List.of("magic"));
            var sourceInput = Requirements.sourceInput(AMOUNT, tags);
            var sourceOutput = Requirements.sourceOutput(AMOUNT, tags);
            var manaInput = Requirements.manaInput(AMOUNT, tags);
            var manaOutput = Requirements.manaOutput(AMOUNT, tags);
            tags.clear();
            var recipe = Recipes.recipe(ID).recipePool(ID).requirement(sourceInput).requirement(sourceOutput)
                    .requirement(manaInput).requirement(manaOutput).build();
            assertThat(recipe.requirements()).allSatisfy(value -> {
                assertThat(value.tags()).containsExactly("magic");
                assertThat(RequirementAdapters.unwrap(value.copy())).isEqualTo(RequirementAdapters.unwrap(value));
                assertThat(value.copy()).isInstanceOf(value instanceof SourceRequirementSpec
                        ? SourceRequirementSpec.class : ManaRequirementSpec.class);
            });
            assertThat(RequirementAdapters.unwrap(sourceInput)).isEqualTo(new SourceRequirement(
                    IOType.INPUT, AMOUNT, List.of("magic")));
            assertThat(RequirementAdapters.unwrap(manaOutput)).isEqualTo(new ManaRequirement(
                    IOType.OUTPUT, AMOUNT, List.of("magic")));
            assertThat(Requirements.sourceInput(1).tags()).isEmpty();
            assertThat(Requirements.sourceOutput(1).io()).isEqualTo(IoDirection.OUTPUT);
            assertThat(Requirements.manaInput(1).io()).isEqualTo(IoDirection.INPUT);
            assertThat(Requirements.manaOutput(1).tags()).isEmpty();
            assertThatThrownBy(() -> manaInput.tags().clear()).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> Requirements.manaInput(0)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Requirements.sourceOutput(-1)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rhino_tagged_total_requirement_factories_route_through_real_recipe_builder() {
        try (var requirements = RequirementHandlerRegistry.openTestScope();
             var outputs = OutputRegistry.openTestScope()) {
            ArsNouveauRecipeTypes.register();
            BotaniaRecipeTypes.register();
            var builder = new MachineRecipeBuilderJS(ID);
            builder.recipePoolId = ID;
            var context = new ContextFactory().enter();
            var scope = context.initStandardObjects();
            ScriptableObject.putProperty(scope, "api", new KubeJSApi(), context);
            ScriptableObject.putProperty(scope, "builder", builder, context);
            context.evaluateString(scope, """
                    builder.addRequirement(api.sourceInputRequirement(3000000000, ['source_in']));
                    builder.addRequirement(api.sourceOutputRequirement(3000000000, ['source_out']));
                    builder.addRequirement(api.manaInputRequirement(3000000000, ['mana_in']));
                    builder.addRequirement(api.manaOutputRequirement(3000000000, ['mana_out']));
                    """, "tagged-total-requirements", 1, null);
            assertThat(builder.createObject().requirements()).containsExactly(
                    new SourceRequirement(IOType.INPUT, AMOUNT, List.of("source_in")),
                    new SourceRequirement(IOType.OUTPUT, AMOUNT, List.of("source_out")),
                    new ManaRequirement(IOType.INPUT, AMOUNT, List.of("mana_in")),
                    new ManaRequirement(IOType.OUTPUT, AMOUNT, List.of("mana_out")));
            var api = new KubeJSApi();
            assertThat(api.sourceInputRequirement(AMOUNT)).isEqualTo(SourceRequirement.input(AMOUNT));
            assertThat(api.sourceOutputRequirement(AMOUNT)).isEqualTo(SourceRequirement.output(AMOUNT));
            assertThat(api.manaInputRequirement(AMOUNT)).isEqualTo(ManaRequirement.input(AMOUNT));
            assertThat(api.manaOutputRequirement(AMOUNT)).isEqualTo(ManaRequirement.output(AMOUNT));
        }
    }
}
