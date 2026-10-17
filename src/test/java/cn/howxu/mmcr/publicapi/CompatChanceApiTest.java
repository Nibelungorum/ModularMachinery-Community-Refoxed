package cn.howxu.mmcr.publicapi;

import cn.howxu.mmcr.api.recipe.CustomRecipeIo;
import cn.howxu.mmcr.api.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.RecipeIoValidation;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.compat.ars_nouveau.ArsNouveauRecipeTypes;
import cn.howxu.mmcr.compat.ars_nouveau.SourceOutput;
import cn.howxu.mmcr.compat.ars_nouveau.SourceRequirement;
import cn.howxu.mmcr.compat.botania.BotaniaRecipeTypes;
import cn.howxu.mmcr.compat.botania.ManaOutput;
import cn.howxu.mmcr.compat.botania.ManaRequirement;
import cn.howxu.mmcr.compat.pneumaticcraft.AirRequirement;
import cn.howxu.mmcr.compat.pneumaticcraft.PneumaticRecipeTypes;
import cn.howxu.mmcr.internal.api.facade.recipe.ModifierAdapters;
import cn.howxu.mmcr.internal.api.facade.recipe.RecipeAdapters;
import cn.howxu.mmcr.internal.api.facade.recipe.RequirementAdapters;
import cn.howxu.mmcr.internal.registration.MachineRecipeConverter;
import cn.howxu.mmcr.publicapi.recipe.ArsNouveauIo;
import cn.howxu.mmcr.publicapi.recipe.BotaniaIo;
import cn.howxu.mmcr.publicapi.recipe.CustomIoSpec;
import cn.howxu.mmcr.publicapi.recipe.IoValues;
import cn.howxu.mmcr.publicapi.recipe.PneumaticCraftIo;
import cn.howxu.mmcr.publicapi.recipe.requirement.AirRequirementSpec;
import cn.howxu.mmcr.publicapi.recipe.requirement.ManaRequirementSpec;
import cn.howxu.mmcr.publicapi.recipe.requirement.Requirements;
import cn.howxu.mmcr.publicapi.recipe.requirement.SourceRequirementSpec;
import cn.howxu.mmcr.test.TestBootstrap;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Probability declarations survive public factories and canonical recipe construction.
 * @author howxu <dev@howxu.cn>
 */
class CompatChanceApiTest {
    private static final long AMOUNT = 3_000_000_000L;
    private static final ResourceLocation ID = ResourceLocation.parse("test:compat_chance_api");

    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void custom_io_factories_keep_defaults_boundaries_fractional_chances_and_tags() {
        try (var requirements = RequirementHandlerRegistry.openTestScope();
             var outputs = OutputRegistry.openTestScope()) {
            register();
            assertThat(decode(IoValues.sourceInput(AMOUNT))).isEqualTo(SourceRequirement.input(AMOUNT));
            assertThat(decode(IoValues.manaInput(AMOUNT))).isEqualTo(ManaRequirement.input(AMOUNT));
            assertThat(decode(IoValues.airInput(AMOUNT, 4F, List.of("drive"))))
                    .isEqualTo(AirRequirement.input(AMOUNT, 4F, List.of("drive")));
            for (float chance : new float[]{0F, 0.25F, 1F}) {
                assertThat(decode(IoValues.sourceInput(AMOUNT, chance))).isEqualTo(SourceRequirement.input(AMOUNT, chance));
                assertThat(decode(IoValues.manaInput(AMOUNT, chance))).isEqualTo(ManaRequirement.input(AMOUNT, chance));
                assertThat(decode(IoValues.airInput(AMOUNT, 4F, chance, List.of("drive"))))
                        .isEqualTo(AirRequirement.input(AMOUNT, 4F, chance, List.of("drive")));
                assertThat(decode(IoValues.airOutput(AMOUNT, chance, List.of("generator"))))
                        .isEqualTo(AirRequirement.output(AMOUNT, chance, List.of("generator")));
                assertThat(MachineRecipeConverter.toOutput(custom(IoValues.sourceOutput(AMOUNT, chance))))
                        .isEqualTo(new SourceOutput(AMOUNT).withChance(chance));
                assertThat(MachineRecipeConverter.toOutput(custom(IoValues.manaOutput(AMOUNT, chance))))
                        .isEqualTo(new ManaOutput(AMOUNT).withChance(chance));
                assertThat(ArsNouveauIo.sourceInput(AMOUNT, chance).payload()).isEqualTo(IoValues.sourceInput(AMOUNT, chance).payload());
                assertThat(BotaniaIo.manaOutput(AMOUNT, chance).payload()).isEqualTo(IoValues.manaOutput(AMOUNT, chance).payload());
                assertThat(PneumaticCraftIo.airInput(AMOUNT, 4F, chance).payload()).isEqualTo(IoValues.airInput(AMOUNT, 4F, chance).payload());
            }
            assertThat(MachineRecipeConverter.toOutput(custom(IoValues.sourceOutput(AMOUNT))))
                    .isEqualTo(new SourceOutput(AMOUNT));
            assertThat(MachineRecipeConverter.toOutput(custom(IoValues.manaOutput(AMOUNT))))
                    .isEqualTo(new ManaOutput(AMOUNT));
            for (float invalid : new float[]{-0.1F, 1.1F, Float.NaN, Float.POSITIVE_INFINITY}) {
                assertThatThrownBy(() -> IoValues.sourceInput(AMOUNT, invalid)).isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> IoValues.sourceOutput(AMOUNT, invalid)).isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> IoValues.manaInput(AMOUNT, invalid)).isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> IoValues.manaOutput(AMOUNT, invalid)).isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> IoValues.airInput(AMOUNT, 4F, invalid)).isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> IoValues.airOutput(AMOUNT, invalid)).isInstanceOf(IllegalArgumentException.class);
            }
        }
    }

    @Test
    void core_and_public_drafts_preserve_probability_in_canonical_requirements_and_outputs() {
        try (var requirements = RequirementHandlerRegistry.openTestScope();
             var outputs = OutputRegistry.openTestScope()) {
            register();
            for (float chance : new float[]{0F, 0.25F, 1F}) {
                var core = MachineRecipeBuilder.recipe(ID).recipePool(ID)
                        .inputSource(AMOUNT, chance).outputSource(AMOUNT, chance)
                        .inputMana(AMOUNT, chance).outputMana(AMOUNT, chance)
                        .inputAir(AMOUNT, 4F, chance, List.of("drive"))
                        .outputAir(AMOUNT, chance, List.of("generator")).build();
                var facade = RecipeAdapters.unwrap(RecipeAdapters.recipe(ID).recipePool(ID)
                        .inputSource(AMOUNT, chance).outputSource(AMOUNT, chance)
                        .inputMana(AMOUNT, chance).outputMana(AMOUNT, chance)
                        .inputAir(AMOUNT, 4F, chance, List.of("drive"))
                        .outputAir(AMOUNT, chance, List.of("generator")).build());
                assertThat(core.requirements()).containsExactly(SourceRequirement.input(AMOUNT, chance),
                        ManaRequirement.input(AMOUNT, chance), AirRequirement.input(AMOUNT, 4F, chance, List.of("drive")),
                        AirRequirement.output(AMOUNT, chance, List.of("generator")));
                assertThat(facade.requirements()).isEqualTo(core.requirements());
                assertThat(core.customOutputs().stream().map(MachineRecipeConverter::toOutput).toList())
                        .containsExactly(new SourceOutput(AMOUNT).withChance(chance), new ManaOutput(AMOUNT).withChance(chance));
                assertThat(facade.customOutputs()).extracting(CustomRecipeIo::payload)
                        .containsExactlyElementsOf(core.customOutputs().stream().map(CustomRecipeIo::payload).toList());
            }
        }
    }

    private static void register() {
        ArsNouveauRecipeTypes.register();
        BotaniaRecipeTypes.register();
        PneumaticRecipeTypes.register();
    }

    @Test
    void typed_requirements_copy_chances_and_tags_without_changing_the_other_direction_probability() {
        try (var requirements = RequirementHandlerRegistry.openTestScope();
             var outputs = OutputRegistry.openTestScope()) {
            register();
            var tags = List.of("drive");
            assertThat(Requirements.sourceInput(AMOUNT).consumeChance()).isEqualTo(1F);
            assertThat(Requirements.sourceOutput(AMOUNT).chance()).isEqualTo(1F);
            assertThat(Requirements.manaInput(AMOUNT).consumeChance()).isEqualTo(1F);
            assertThat(Requirements.manaOutput(AMOUNT).chance()).isEqualTo(1F);
            assertThat(Requirements.airInput(AMOUNT, 4F).consumeChance()).isEqualTo(1F);
            assertThat(Requirements.airOutput(AMOUNT).chance()).isEqualTo(1F);
            for (float chance : new float[]{0F, 0.25F, 1F}) {
                var sourceInput = (SourceRequirementSpec) Requirements.sourceInput(AMOUNT, chance, tags).copy();
                var sourceOutput = (SourceRequirementSpec) Requirements.sourceOutput(AMOUNT, chance, tags).copy();
                var manaInput = (ManaRequirementSpec) Requirements.manaInput(AMOUNT, chance, tags).copy();
                var manaOutput = (ManaRequirementSpec) Requirements.manaOutput(AMOUNT, chance, tags).copy();
                var airInput = (AirRequirementSpec) Requirements.airInput(AMOUNT, 4F, chance, tags).copy();
                var airOutput = (AirRequirementSpec) Requirements.airOutput(AMOUNT, chance, tags).copy();
                assertThat(sourceInput.consumeChance()).isEqualTo(chance);
                assertThat(sourceInput.chance()).isEqualTo(1F);
                assertThat(sourceOutput.chance()).isEqualTo(chance);
                assertThat(sourceOutput.consumeChance()).isEqualTo(1F);
                assertThat(manaInput.consumeChance()).isEqualTo(chance);
                assertThat(manaInput.chance()).isEqualTo(1F);
                assertThat(manaOutput.chance()).isEqualTo(chance);
                assertThat(manaOutput.consumeChance()).isEqualTo(1F);
                assertThat(airInput.consumeChance()).isEqualTo(chance);
                assertThat(airInput.chance()).isEqualTo(1F);
                assertThat(airOutput.chance()).isEqualTo(chance);
                assertThat(airOutput.consumeChance()).isEqualTo(1F);
                assertThat(sourceInput.amount()).isEqualTo(AMOUNT);
                assertThat(manaInput.amount()).isEqualTo(AMOUNT);
                assertThat(airInput.airPerTick()).isEqualTo(AMOUNT);
                assertThat(airInput.minPressure()).isEqualTo(4F);
                for (var value : List.of(sourceInput, sourceOutput, manaInput, manaOutput, airInput, airOutput)) {
                    assertThat(value.tags()).containsExactly("drive");
                    assertThat(RequirementAdapters.unwrap(value.copy())).isEqualTo(RequirementAdapters.unwrap(value));
                    assertThatThrownBy(() -> value.tags().clear()).isInstanceOf(UnsupportedOperationException.class);
                }
            }
            for (float invalid : new float[]{-0.1F, 1.1F, Float.NaN, Float.POSITIVE_INFINITY}) {
                assertThatThrownBy(() -> Requirements.sourceInput(AMOUNT, invalid, tags)).isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> Requirements.sourceOutput(AMOUNT, invalid, tags)).isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> Requirements.manaInput(AMOUNT, invalid, tags)).isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> Requirements.manaOutput(AMOUNT, invalid, tags)).isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> Requirements.airInput(AMOUNT, 4F, invalid, tags)).isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> Requirements.airOutput(AMOUNT, invalid, tags)).isInstanceOf(IllegalArgumentException.class);
            }
        }
    }

    private static CustomRecipeIo custom(CustomIoSpec value) {
        return new CustomRecipeIo(value.typeId(), ModifierAdapters.io(value.io()), value.payload());
    }

    private static Object decode(CustomIoSpec value) { return RecipeIoValidation.decodeRequirement(custom(value)); }
}
