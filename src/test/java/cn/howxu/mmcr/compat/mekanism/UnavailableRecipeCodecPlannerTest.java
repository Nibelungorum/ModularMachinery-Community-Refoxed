package cn.howxu.mmcr.compat.mekanism;

import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.machine.definition.MachineIoPlan;
import cn.howxu.mmcr.compat.kubejs.KubeJSApi;
import dev.latvian.mods.rhino.ContextFactory;
import dev.latvian.mods.rhino.ScriptableObject;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalIngredient;
import cn.howxu.mmcr.api.compat.mekanism.MekanismFailureReasons;
import cn.howxu.mmcr.api.recipe.CustomRecipeIo;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.registration.StructureRegistration;
import cn.howxu.mmcr.internal.recipe.RequirementPlanner;
import cn.howxu.mmcr.internal.registration.MachineRecipeConverter;
import cn.howxu.mmcr.test.TestBootstrap;
import com.google.gson.JsonArray;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies absent-integration codecs and planning in isolated registries.
 * @author howxu <dev@howxu.cn>
 */
class UnavailableRecipeCodecPlannerTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrapCapabilities();
    }

    @Test
    void rhino_tick_helpers_decode_unavailable_io_and_cannot_commit_it() {
        try (var requirements = RequirementHandlerRegistry.openTestScope();
             var outputs = OutputRegistry.openTestScope()) {
            MekanismRecipeDeclarations.registerUnavailable();
            var context = new ContextFactory().enter();
            var scope = context.initStandardObjects();
            ScriptableObject.putProperty(scope, "api", new KubeJSApi(), context);
            String[] scripts = {
                    "plan.addInput(api.chemicalInput('mekanism:oxygen', 1000, 0.25))",
                    "plan.addInput(api.heatTemperatureInput(450))",
                    "plan.addOutput(api.chemicalOutput('mekanism:hydrogen', 200, 0.5))",
                    "plan.add(api.heatOutput(120))",
                    "plan.add(api.chemicalInput('mekanism:oxygen', 1000, 0.25))"
            };
            var expectedTypes = List.of(MekanismRecipeTypes.CHEMICAL, MekanismRecipeTypes.HEAT_TEMPERATURE,
                    MekanismRecipeTypes.CHEMICAL, MekanismRecipeTypes.HEAT, MekanismRecipeTypes.CHEMICAL);
            for (int index = 0; index < scripts.length; index++) {
                var plan = new MachineIoPlan(new CapabilitySnapshot(List.of()));
                ScriptableObject.putProperty(scope, "plan", plan, context);
                context.evaluateString(scope, scripts[index], "unavailable-tick-helper", 1, null);
                var requirement = plan.requirements().getFirst();
                assertThat(requirement.type()).isSameAs(RequirementHandlerRegistry.typeFor(expectedTypes.get(index)));
                assertThat(requirement.io()).isEqualTo(index == 2 || index == 3 ? IOType.OUTPUT : IOType.INPUT);
                if (index == 0 || index == 4) {
                    var chemical = (MekanismRecipeDeclarations.UnavailableChemicalRequirement) requirement;
                    assertThat(chemical.ingredient()).isEqualTo(ChemicalIngredient.chemical(ResourceLocation.parse("mekanism:oxygen"), 1000));
                    assertThat(chemical.consumeChance()).isEqualTo(0.25F);
                }
                if (index == 2) {
                    var chemical = (MekanismRecipeDeclarations.UnavailableChemicalRequirement) requirement;
                    assertThat(chemical.ingredient()).isEqualTo(ChemicalIngredient.chemical(ResourceLocation.parse("mekanism:hydrogen"), 200));
                    assertThat(chemical.chance()).isEqualTo(0.5F);
                }
                if (index == 1 || index == 3) {
                    var heat = (MekanismRecipeDeclarations.UnavailableHeatRequirement) requirement;
                    assertThat(heat.heat().value()).isEqualTo(index == 1 ? 450D : 120D);
                }
                assertThat(plan.simulate().failure().reason()).isSameAs(MekanismFailureReasons.MEKANISM_UNAVAILABLE);
                var writes = new AtomicInteger();
                assertThat(plan.commit(transaction -> writes.incrementAndGet()).successful()).isFalse();
                assertThat(writes).hasValue(0);
            }
        }
    }

    @Test
    void unavailable_decoders_preserve_declarations_and_block_every_io_without_operations() {
        try (var requirements = RequirementHandlerRegistry.openTestScope();
             var outputs = OutputRegistry.openTestScope()) {
            MekanismRecipeDeclarations.registerUnavailable();
            assertThat(RequirementHandlerRegistry.typeFor(MekanismRecipeTypes.CHEMICAL))
                    .isSameAs(MekanismRecipeDeclarations.CHEMICAL_TYPE);
            assertThat(OutputRegistry.typeFor(MekanismRecipeTypes.CHEMICAL))
                    .isSameAs(MekanismRecipeDeclarations.CHEMICAL_OUTPUT_TYPE);
            var payload = MachineRecipeBuilder.chemicalInputPayload(
                    ChemicalIngredient.tag(ResourceLocation.parse("mekanism:fuels"), 1000), 0.25F);
            var tags = new JsonArray();
            tags.add("unavailable");
            payload.add("tags", tags);
            var declaration = MachineRecipeBuilder.recipe(ResourceLocation.parse("mmcr:unavailable_codec"))
                    .recipePool(ResourceLocation.parse("mmcr:unavailable_pool"))
                    .custom(new CustomRecipeIo(MekanismRecipeTypes.CHEMICAL, IOType.INPUT, payload))
                    .inputHeatTemperature(450)
                    .outputChemical(ResourceLocation.parse("mekanism:hydrogen"), 200, 0.5F)
                    .outputHeat(120)
                    .build();
            payload.addProperty("amount", 1);
            tags.add("mutated");
            var chemical = (MekanismRecipeDeclarations.UnavailableChemicalRequirement) declaration.requirements().getFirst();
            assertThat(chemical.ingredient()).isEqualTo(ChemicalIngredient.tag(ResourceLocation.parse("mekanism:fuels"), 1000));
            assertThat(chemical.consumeChance()).isEqualTo(0.25F);
            assertThat(chemical.tags()).containsExactly("unavailable");
            var recipe = MachineRecipeConverter.toRecipe(declaration,
                    new StructureRegistration.Snapshot(Map.of(), Map.of(), Map.of(), Map.of()));
            assertThat(recipe.requirements()).extracting(value -> value.type().id())
                    .containsExactly(MekanismRecipeTypes.CHEMICAL, MekanismRecipeTypes.HEAT_TEMPERATURE,
                            MekanismRecipeTypes.CHEMICAL, MekanismRecipeTypes.HEAT);
            assertThat(recipe.requirements()).extracting(MachineRequirement::io)
                    .containsExactly(IOType.INPUT, IOType.INPUT, IOType.OUTPUT, IOType.OUTPUT);
            assertThat(recipe.machineOutputs()).hasSize(2);
            for (int index = 0; index < recipe.requirements().size(); index++) {
                var original = recipe.requirements().get(index);
                var encoded = MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
                var decoded = MachineRequirement.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
                var copied = MachineRequirement.copyOf(decoded);
                assertThat(copied.type()).isSameAs(original.type());
                assertThat(copied.io()).isEqualTo(original.io());
                assertThat(MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE, copied).getOrThrow()).isEqualTo(encoded);
                var planned = new RequirementPlanner().plan(List.of(copied), List.of(), new PlanningContext(1, index));
                assertThat(planned.failure().reason()).isSameAs(MekanismFailureReasons.MEKANISM_UNAVAILABLE);
                assertThat(planned.plan()).isNull();
            }
            for (int index = 0; index < recipe.machineOutputs().size(); index++) {
                var output = recipe.machineOutputs().get(index);
                var expected = MachineRecipeConverter.toOutput(declaration.customOutputs().get(index));
                var encoded = MachineOutput.CODEC.encodeStart(JsonOps.INSTANCE, output).getOrThrow();
                assertThat(encoded).isEqualTo(MachineOutput.CODEC.encodeStart(JsonOps.INSTANCE, expected).getOrThrow());
                var decoded = MachineOutput.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
                assertThat(MachineOutput.CODEC.encodeStart(JsonOps.INSTANCE, MachineOutput.copyOf(decoded)).getOrThrow()).isEqualTo(encoded);
            }
            var temperature = (MekanismRecipeDeclarations.UnavailableHeatRequirement) recipe.requirements().get(1);
            assertThat(temperature.heat().value()).isEqualTo(450);
            var chemicalOutput = (MekanismRecipeDeclarations.UnavailableChemicalOutput) recipe.machineOutputs().getFirst();
            assertThat(chemicalOutput.id()).isEqualTo(ResourceLocation.parse("mekanism:hydrogen"));
            assertThat(chemicalOutput.amount()).isEqualTo(200);
            assertThat(chemicalOutput.chance()).isEqualTo(0.5F);
            assertThat(((MekanismRecipeDeclarations.UnavailableHeatOutput) recipe.machineOutputs().get(1)).heat()).isEqualTo(120);
        }
    }
}
