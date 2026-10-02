package cn.howxu.mmcr.compat.ars_nouveau;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.capability.status.FailureReasonRegistry;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.OutputType;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.Operation;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import cn.howxu.mmcr.test.TestBootstrap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies source declaration behavior without an Ars loaded bridge or a game world.
 *
 * @author howxu <dev@howxu.cn>
 */
class SourceRecipeDeclarationTest {
    private RequirementHandlerRegistry.TestScope requirementScope;
    private OutputRegistry.TestScope outputScope;

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void openRegistryScopes() {
        requirementScope = RequirementHandlerRegistry.openTestScope();
        outputScope = OutputRegistry.openTestScope();
        FailureReasonRegistry.clearForTesting();
        SourceRequirement.installUnavailableHandler();
        SourceFailureReasons.register();
        ArsNouveauRecipeTypes.register();
    }

    @AfterEach
    void closeRegistryScopes() {
        SourceRequirement.installUnavailableHandler();
        outputScope.close();
        requirementScope.close();
        FailureReasonRegistry.clearForTesting();
    }

    @Test
    void neutralDeclarationCopiesAndRoundTripsWithoutArsRuntime() {
        SourceRequirement original = new SourceRequirement(IOType.INPUT, 3_000_000_000L, List.of("arcane"));
        JsonElement encoded = MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        MachineRequirement decoded = MachineRequirement.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertThat(decoded).isEqualTo(original);
        assertThat(MachineRequirement.copyOf(original)).isEqualTo(original).isNotSameAs(original);
        assertThat(decoded.type()).isSameAs(SourceRequirement.TYPE);
        SourceOutput output = new SourceOutput(700L);
        assertThat(MachineOutput.copyOf(output)).isEqualTo(output);
        MachineRequirement converted = OutputRegistry.toRequirement(output, List.of("arcane"));
        assertThat(converted).isEqualTo(new SourceRequirement(IOType.OUTPUT, 700L, List.of("arcane")));
        assertThat(OutputRegistry.fromRequirement(converted)).isEqualTo(output);
        assertThat(OutputRegistry.fromRequirement(original)).isNull();
        assertThat(OutputRegistry.matchesOutputRequirement(output, converted)).isTrue();
        assertThat(OutputRegistry.matchesOutputRequirement(original)).isFalse();
    }

    @Test
    void jsonFactoriesPreserveLongAmountsAndDefaultInputDirection() {
        long amount = Long.MAX_VALUE - 1L;
        JsonObject input = SourceRecipeDeclarations.inputPayload(amount);
        assertThat(MachineRequirement.CODEC.parse(JsonOps.INSTANCE, input).getOrThrow())
                .isEqualTo(SourceRequirement.input(amount));
        JsonObject output = SourceRecipeDeclarations.outputPayload(amount);
        MachineOutput decoded = MachineOutput.CODEC.parse(JsonOps.INSTANCE, output).getOrThrow();
        assertThat(decoded).isEqualTo(new SourceOutput(amount));
        assertThat(decoded.outputType()).isSameAs(SourceOutput.TYPE);
        assertThat(MachineOutput.scaledAmount(decoded)).isEqualTo(amount);
        assertThat(output.keySet()).containsExactlyInAnyOrder("type", "amount");
        JsonElement requirementOutput = MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE,
                SourceRequirement.output(amount)).getOrThrow();
        assertThat(MachineRequirement.CODEC.parse(JsonOps.INSTANCE, requirementOutput).getOrThrow())
                .isEqualTo(SourceRequirement.output(amount));
    }

    @Test
    void rejectsNonPositiveAmounts() {
        for (long amount : new long[]{0L, -1L, Long.MIN_VALUE}) {
            assertThatThrownBy(() -> SourceRequirement.input(amount)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> SourceRequirement.output(amount)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new SourceOutput(amount)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void tagsAreDefensivelyCopiedAndBounded() {
        List<String> tags = new ArrayList<>(List.of("arcane"));
        SourceRequirement value = new SourceRequirement(IOType.INPUT, 10L, tags);
        tags.add("changed");
        assertThat(value.tags()).containsExactly("arcane");
        assertThatThrownBy(() -> value.tags().add("changed")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new SourceRequirement(IOType.INPUT, 1L, Collections.nCopies(1025, "tag")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SourceRequirement(null, 1L, List.of())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SourceRequirement(IOType.INPUT, 1L, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void unavailableHandlerReportsArsUnavailable() {
        for (SourceRequirement value : List.of(SourceRequirement.input(10L), SourceRequirement.output(10L))) {
            RequirementPlan plan = SourceRequirement.TYPE.handler().plan(value, List.of(), new PlanningContext(4L, 2));
            assertThat(plan.successful()).isFalse();
            assertThat(plan.operations()).isEmpty();
            assertThat(plan.failure().reason()).isSameAs(SourceFailureReasons.ARS_UNAVAILABLE);
            assertThat(plan.requirementIndex()).isEqualTo(2);
            assertThat(RequirementHandlerRegistry.resourceWakeupsFor(value)).isEmpty();
        }
    }

    @Test
    void sourceModifiersDoNotMatchEnergyTarget() {
        SourceRequirement value = new SourceRequirement(IOType.INPUT, 700L, List.of("arcane"));
        List<RecipeModifier> energy = List.of(new RecipeModifier("energy", IOType.INPUT, 3F, Operation.MULTIPLY, false));
        assertThat(RequirementHandlerRegistry.applyModifiers(value, energy)).isEqualTo(value);

        List<RecipeModifier> modifiers = List.of(
                new RecipeModifier("source", IOType.INPUT, 2F, Operation.MULTIPLY, false),
                new RecipeModifier("source", IOType.OUTPUT, 5F, Operation.MULTIPLY, false),
                new RecipeModifier("source", IOType.INPUT, 99F, Operation.MULTIPLY, true));
        assertThat(RequirementHandlerRegistry.applyModifiers(value, modifiers))
                .isEqualTo(new SourceRequirement(IOType.INPUT, 1400L, List.of("arcane")));
        assertThat(new SourceOutput(700L).applyModifiers(modifiers)).isEqualTo(new SourceOutput(3500L));
    }

    @Test
    void levelModifiersAffectOnlySourceOutputAndKeepTags() {
        SourceRequirement input = SourceRequirement.input(700L);
        SourceRequirement output = new SourceRequirement(IOType.OUTPUT, 700L, List.of("arcane"));
        assertThat(RequirementHandlerRegistry.applyLevelModifiers(input, 9D, 3D)).isSameAs(input);
        assertThat(RequirementHandlerRegistry.applyLevelModifiers(output, 9D, 3D))
                .isEqualTo(new SourceRequirement(IOType.OUTPUT, 2100L, List.of("arcane")));
        assertThat(RequirementHandlerRegistry.applyLevelModifiers(output, 9D, 1D)).isEqualTo(output);
    }

    @Test
    void amountTransformsPreserveUnmodifiedLongsAndClampAdjustedValues() {
        long precise = Long.MAX_VALUE - 1L;
        assertThat(SourceRecipeDeclarations.applyAmount(precise, IOType.INPUT, List.of())).isEqualTo(precise);
        assertThat(SourceRecipeDeclarations.applyAmount(precise, IOType.INPUT, null)).isEqualTo(precise);
        assertThat(SourceRecipeDeclarations.applyOutputMultiplier(precise, 1D)).isEqualTo(precise);
        assertThat(SourceRecipeDeclarations.applyOutputMultiplier(3L, 1.5D)).isEqualTo(4L);
        for (double multiplier : new double[]{0D, -1D, Double.NaN}) {
            assertThat(SourceRecipeDeclarations.applyOutputMultiplier(700L, multiplier)).isEqualTo(1L);
        }
        assertThat(SourceRecipeDeclarations.applyOutputMultiplier(700L, Double.POSITIVE_INFINITY)).isEqualTo(Long.MAX_VALUE);
        assertThat(SourceRecipeDeclarations.applyAmount(700L, IOType.INPUT,
                List.of(new RecipeModifier("source", IOType.INPUT, Float.NaN, Operation.MULTIPLY, false))))
                .isEqualTo(1L);
        assertThat(SourceRecipeDeclarations.applyAmount(700L, IOType.INPUT,
                List.of(new RecipeModifier("source", IOType.INPUT, Float.POSITIVE_INFINITY, Operation.MULTIPLY, false))))
                .isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void outputRejectsProbability() {
        SourceOutput value = new SourceOutput(700L);
        assertThat(value.withChance(1F)).isSameAs(value);
        for (float chance : new float[]{0F, 0.5F, -1F, 2F, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertThatThrownBy(() -> SourceOutput.TYPE.withChance(value, chance))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void installingHandlerKeepsCanonicalIdentityAndNeutralTransforms() {
        SourceRequirement value = SourceRequirement.input(700L);
        RequirementHandler<SourceRequirement> canonicalHandler = SourceRequirement.TYPE.handler();
        PlanningContext context = new PlanningContext(4L, 2);
        RequirementPlan expectedPlan = new RequirementPlan(2, 4L, List.of(), null);
        List<RequirementHandler.ResourceWakeup> wakeups = List.of(new RequirementHandler.ResourceWakeup(
                Set.of(SourceFailureReasons.INPUT_MISSING.id()), RequirementHandler.WakeupReason.INPUT_AVAILABLE,
                ArsSourceIds.SOURCE::equals));
        SourceRequirement.installHandler(new RequirementHandler<>() {
            @Override
            public RequirementPlan plan(SourceRequirement requirement, List<MachineCapability> capabilities,
                                        PlanningContext planning) {
                assertThat(requirement).isSameAs(value);
                assertThat(planning).isSameAs(context);
                return expectedPlan;
            }

            @Override
            public List<ResourceWakeup> resourceWakeups(SourceRequirement requirement) {
                return wakeups;
            }
        });

        ArsNouveauRecipeTypes.register();
        assertThat(RequirementHandlerRegistry.typeFor(ArsSourceIds.SOURCE)).isSameAs(SourceRequirement.TYPE);
        assertThat(RequirementHandlerRegistry.handlerFor(SourceRequirement.TYPE)).isSameAs(canonicalHandler);
        assertThat(canonicalHandler.plan(value, List.of(), context)).isSameAs(expectedPlan);
        assertThat(RequirementHandlerRegistry.resourceWakeupsFor(value)).isSameAs(wakeups);
        assertThat(RequirementHandlerRegistry.overlaps(value, SourceRequirement.input(1L))).isTrue();
        assertThat(RequirementHandlerRegistry.overlaps(value, SourceRequirement.output(1L))).isFalse();
        assertThat(RequirementHandlerRegistry.applyModifiers(value,
                List.of(new RecipeModifier("source", IOType.INPUT, 2F, Operation.MULTIPLY, false))))
                .isEqualTo(SourceRequirement.input(1400L));
        assertThatThrownBy(() -> SourceRequirement.installHandler(null)).isInstanceOf(NullPointerException.class);
        assertThat(canonicalHandler.plan(value, List.of(), context)).isSameAs(expectedPlan);
        SourceRequirement.installUnavailableHandler();
        assertThat(canonicalHandler.plan(value, List.of(), context).failure().reason())
                .isSameAs(SourceFailureReasons.ARS_UNAVAILABLE);
        assertThat(RequirementHandlerRegistry.resourceWakeupsFor(value)).isEmpty();
    }

    @Test
    void canonicalRegistrationRejectsForeignRequirementWithSameId() {
        requirementScope.close();
        requirementScope = RequirementHandlerRegistry.openTestScope();
        RequirementType<SourceRequirement> foreign = new RequirementType.Definition<>(ArsSourceIds.SOURCE,
                SourceRequirement.CODEC, SourceRequirement.TYPE.handler());
        RequirementHandlerRegistry.register(foreign);
        assertThatThrownBy(ArsNouveauRecipeTypes::register).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate requirement handler type");
        assertThat(RequirementHandlerRegistry.typeFor(ArsSourceIds.SOURCE)).isSameAs(foreign);
    }

    @Test
    void canonicalRegistrationRejectsForeignOutputWithSameId() {
        outputScope.close();
        outputScope = OutputRegistry.openTestScope();
        OutputType<SourceOutput> foreign = new OutputType.Definition<>(ArsSourceIds.SOURCE, SourceOutput.CODEC,
                SourceOutput::withChance, (value, modifiers) -> value, value -> value);
        OutputRegistry.register(foreign);
        assertThatThrownBy(ArsNouveauRecipeTypes::register).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate output type");
        assertThat(OutputRegistry.typeFor(ArsSourceIds.SOURCE)).isSameAs(foreign);
    }

    @Test
    void failureRegistrationUsesStableKeysAndBuiltinPriorities() {
        for (FailureReason reason : List.of(SourceFailureReasons.ARS_UNAVAILABLE,
                SourceFailureReasons.INPUT_MISSING, SourceFailureReasons.OUTPUT_BLOCKED)) {
            assertThat(FailureReasonRegistry.find(reason.id())).isSameAs(reason);
            assertThat(reason.translationKey()).isEqualTo("gui.mmcr.failure." + reason.id().getPath());
        }
        assertThat(SourceFailureReasons.INPUT_MISSING.priority()).isEqualTo(BuiltinFailureReasons.MISSING_INPUT.priority());
        assertThat(SourceFailureReasons.OUTPUT_BLOCKED.priority()).isEqualTo(BuiltinFailureReasons.MISSING_OUTPUT.priority());
        assertThatThrownBy(SourceFailureReasons::register).isInstanceOf(IllegalArgumentException.class);
    }
}
