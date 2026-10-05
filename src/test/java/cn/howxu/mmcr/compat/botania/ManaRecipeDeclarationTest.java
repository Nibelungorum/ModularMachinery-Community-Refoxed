package cn.howxu.mmcr.compat.botania;

import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.status.FailureReasonRegistry;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.OutputType;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.Operation;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies canonical mana declarations without a Botania bridge or game world.
 *
 * @author howxu <dev@howxu.cn>
 */
class ManaRecipeDeclarationTest {
    private RequirementHandlerRegistry.TestScope requirementScope;
    private OutputRegistry.TestScope outputScope;

    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @BeforeEach
    void openScopes() {
        requirementScope = RequirementHandlerRegistry.openTestScope();
        outputScope = OutputRegistry.openTestScope();
        FailureReasonRegistry.clearForTesting();
        ManaRequirement.installUnavailableHandler();
        ManaFailureReasons.register();
        BotaniaRecipeTypes.register();
    }

    @AfterEach
    void closeScopes() {
        ManaRequirement.installUnavailableHandler();
        outputScope.close();
        requirementScope.close();
        FailureReasonRegistry.clearForTesting();
    }

    @Test
    void longDeclarationsRoundTripThroughCanonicalJsonAndSync() {
        long amount = 3_000_000_000L;
        ManaRequirement input = new ManaRequirement(IOType.INPUT, amount, List.of("arcane"));
        assertThat(MachineRequirement.CODEC.parse(JsonOps.INSTANCE,
                MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE, input).getOrThrow()).getOrThrow())
                .isEqualTo(input);
        assertThat(MachineRequirement.copyOf(input)).isEqualTo(input).isNotSameAs(input);
        assertThat(MachineRequirement.CODEC.parse(JsonOps.INSTANCE,
                ManaRecipeDeclarations.inputPayload(amount)).getOrThrow()).isEqualTo(ManaRequirement.input(amount));
        ManaOutput output = new ManaOutput(amount);
        assertThat(MachineOutput.CODEC.parse(JsonOps.INSTANCE,
                ManaRecipeDeclarations.outputPayload(amount)).getOrThrow()).isEqualTo(output);
        assertThat(MachineOutput.scaledAmount(output)).isEqualTo(amount);
        MachineRequirement converted = OutputRegistry.toRequirement(output, List.of("arcane"));
        assertThat(converted).isEqualTo(new ManaRequirement(IOType.OUTPUT, amount, List.of("arcane")));
        assertThat(OutputRegistry.fromRequirement(converted)).isEqualTo(output);
        assertThat(OutputRegistry.fromRequirement(input)).isNull();
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            ManaRequirement.SYNC_CODEC.encode(buffer, input);
            ManaOutput.SYNC_CODEC.encode(buffer, output);
            assertThat(ManaRequirement.SYNC_CODEC.decode(buffer)).isEqualTo(input);
            assertThat(ManaOutput.SYNC_CODEC.decode(buffer)).isEqualTo(output);
            assertThat(buffer.readableBytes()).isZero();
        } finally {
            buffer.release();
        }
    }

    @Test
    void validatesPositiveAmountsImmutableTagsAndDeterministicOutputs() {
        for (long amount : new long[]{0L, -1L, Long.MIN_VALUE}) {
            assertThatThrownBy(() -> ManaRequirement.input(amount)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ManaRequirement.output(amount)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ManaOutput(amount)).isInstanceOf(IllegalArgumentException.class);
        }
        List<String> tags = new ArrayList<>(List.of("arcane"));
        ManaRequirement input = new ManaRequirement(IOType.INPUT, 700L, tags);
        tags.add("changed");
        assertThat(input.tags()).containsExactly("arcane");
        assertThatThrownBy(() -> input.tags().add("changed")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new ManaRequirement(IOType.INPUT, 1L, Collections.nCopies(1025, "tag")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ManaRequirement(null, 1L, List.of())).isInstanceOf(NullPointerException.class);
        ManaOutput output = new ManaOutput(700L);
        assertThat(output.withChance(1F)).isSameAs(output);
        for (float chance : new float[]{0F, 0.5F, -1F, 2F, Float.NaN}) {
            assertThatThrownBy(() -> output.withChance(chance)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void modifiersRespectManaTargetDirectionAndNonTickSemantics() {
        List<RecipeModifier> modifiers = List.of(
                new RecipeModifier("energy", IOType.INPUT, 3F, Operation.MULTIPLY, false),
                new RecipeModifier("mana", IOType.INPUT, 2F, Operation.MULTIPLY, false),
                new RecipeModifier("mana", IOType.OUTPUT, 5F, Operation.MULTIPLY, false),
                new RecipeModifier("mana", IOType.INPUT, 99F, Operation.MULTIPLY, true));
        assertThat(RequirementHandlerRegistry.applyModifiers(ManaRequirement.input(700L), modifiers))
                .isEqualTo(ManaRequirement.input(1400L));
        assertThat(new ManaOutput(700L).applyModifiers(modifiers)).isEqualTo(new ManaOutput(3500L));
        ManaRequirement input = ManaRequirement.input(700L);
        ManaRequirement output = new ManaRequirement(IOType.OUTPUT, 700L, List.of("arcane"));
        assertThat(RequirementHandlerRegistry.applyLevelModifiers(input, 9D, 3D)).isSameAs(input);
        assertThat(RequirementHandlerRegistry.applyLevelModifiers(output, 9D, 3D))
                .isEqualTo(new ManaRequirement(IOType.OUTPUT, 2100L, List.of("arcane")));
        long precise = Long.MAX_VALUE - 1L;
        assertThat(ManaRecipeDeclarations.applyAmount(precise, IOType.INPUT, List.of())).isEqualTo(precise);
        assertThat(ManaRecipeDeclarations.applyOutputMultiplier(precise, 1D)).isEqualTo(precise);
        assertThat(ManaRecipeDeclarations.applyOutputMultiplier(3L, 1.5D)).isEqualTo(4L);
        assertThat(ManaRecipeDeclarations.applyOutputMultiplier(700L, Double.NaN)).isEqualTo(1L);
        assertThat(ManaRecipeDeclarations.applyOutputMultiplier(700L, Double.POSITIVE_INFINITY))
                .isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void installingHandlerAndRepeatedRegistrationPreserveCanonicalIdentity() {
        var canonicalHandler = ManaRequirement.TYPE.handler();
        for (ManaRequirement value : List.of(ManaRequirement.input(10L), ManaRequirement.output(10L))) {
            var plan = canonicalHandler.plan(value, List.of(), new PlanningContext(4L, 2));
            assertThat(plan.successful()).isFalse();
            assertThat(plan.failure().reason()).isSameAs(ManaFailureReasons.BOTANIA_UNAVAILABLE);
            assertThat(plan.requirementIndex()).isEqualTo(2);
            assertThat(RequirementHandlerRegistry.resourceWakeupsFor(value)).isEmpty();
        }
        ManaRequirement.installHandler(new ManaRequirementHandler());
        BotaniaRecipeTypes.register();
        assertThat(RequirementHandlerRegistry.typeFor(BotaniaManaIds.MANA)).isSameAs(ManaRequirement.TYPE);
        assertThat(OutputRegistry.typeFor(BotaniaManaIds.MANA)).isSameAs(ManaOutput.TYPE);
        assertThat(ManaRequirement.TYPE.handler()).isSameAs(canonicalHandler);
        assertThat(canonicalHandler.plan(ManaRequirement.input(10L), List.of(), new PlanningContext(1L, 0))
                .failure().reason()).isSameAs(ManaFailureReasons.INPUT_MISSING);
        ManaRequirement.installUnavailableHandler();
        assertThat(canonicalHandler.plan(ManaRequirement.input(10L), List.of(), new PlanningContext(1L, 0))
                .failure().reason()).isSameAs(ManaFailureReasons.BOTANIA_UNAVAILABLE);
        assertThat(FailureReasonRegistry.find(ManaFailureReasons.BOTANIA_UNAVAILABLE.id()))
                .isSameAs(ManaFailureReasons.BOTANIA_UNAVAILABLE);
    }

    @Test
    void rejectsForeignCanonicalRequirement() {
        requirementScope.close();
        requirementScope = RequirementHandlerRegistry.openTestScope();
        RequirementType<ManaRequirement> foreign = new RequirementType.Definition<>(BotaniaManaIds.MANA,
                ManaRequirement.CODEC, ManaRequirement.TYPE.handler());
        RequirementHandlerRegistry.register(foreign);
        assertThatThrownBy(BotaniaRecipeTypes::register).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate requirement handler type");
        assertThat(RequirementHandlerRegistry.typeFor(BotaniaManaIds.MANA)).isSameAs(foreign);
    }

    @Test
    void rejectsForeignCanonicalOutput() {
        outputScope.close();
        outputScope = OutputRegistry.openTestScope();
        OutputType<ManaOutput> foreign = new OutputType.Definition<>(BotaniaManaIds.MANA, ManaOutput.CODEC,
                ManaOutput::withChance, (value, modifiers) -> value, value -> value);
        OutputRegistry.register(foreign);
        assertThatThrownBy(BotaniaRecipeTypes::register).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate output type");
        assertThat(OutputRegistry.typeFor(BotaniaManaIds.MANA)).isSameAs(foreign);
    }
}
