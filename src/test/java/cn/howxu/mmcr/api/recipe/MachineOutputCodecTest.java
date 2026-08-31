package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.test.TestBootstrap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies registry-backed machine output serialization and transformations.
 * @author howxu <dev@howxu.cn>
 */
class MachineOutputCodecTest {
    private static final Identifier TEST_ID = Identifier.fromNamespaceAndPath("mmcr_test", "custom_output");
    private static final MapCodec<TestOutput> TEST_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            com.mojang.serialization.Codec.STRING.fieldOf("type").forGetter(ignored -> TEST_ID.toString()),
            com.mojang.serialization.Codec.INT.fieldOf("value").forGetter(TestOutput::value),
            com.mojang.serialization.Codec.FLOAT.optionalFieldOf("chance", 1F).forGetter(TestOutput::chance)
    ).apply(instance, (ignored, value, chance) -> new TestOutput(value, chance)));
    private static final OutputType<TestOutput> TEST_TYPE = new OutputType.Definition<>(
            TEST_ID,
            TEST_CODEC,
             (output, chance) -> new TestOutput(output.value(), chance),
             (output, modifiers) -> new TestOutput(output.value() + modifierAmount(modifiers), output.chance()),
            output -> new TestOutput(output.value(), output.chance()), OutputType.Presentation.defaults(TEST_ID),
            TEST_ID.toString(),
            (output, tags) -> new EnergyRequirement(RecipeModifier.IOType.OUTPUT, output.value(), tags),
            requirement -> requirement instanceof EnergyRequirement energy
                    && energy.io() == RecipeModifier.IOType.OUTPUT);

    private OutputRegistry.TestScope scope;

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void openRegistryScope() {
        scope = OutputRegistry.openTestScope();
        OutputRegistry.register(TEST_TYPE);
    }

    @AfterEach
    void closeRegistryScope() {
        scope.close();
    }

    @Test
    void item_output_keeps_legacy_json_shape_and_chance_defaults() {
        ItemStack stack = new ItemStack(Items.IRON_NUGGET, 4);
        JsonObject json = new JsonObject();
        json.addProperty("type", "item");
        json.add("stack", ItemStack.CODEC.encodeStart(jsonOps(), stack).getOrThrow());

        MachineOutput decoded = MachineOutput.CODEC.parse(jsonOps(), json).getOrThrow();
        JsonObject encoded = MachineOutput.CODEC.encodeStart(jsonOps(), decoded).getOrThrow().getAsJsonObject();

        assertThat(decoded).isInstanceOfSatisfying(MachineOutput.ItemOutput.class, output -> {
            assertThat(output.stack()).isEqualTo(stack);
            assertThat(output.chance()).isEqualTo(1F);
        });
        assertThat(encoded.get("type").getAsString()).isEqualTo("item");
        assertThat(encoded.getAsJsonObject("stack").get("count").getAsInt()).isEqualTo(4);
        assertThat(encoded.has("chance")).isTrue();
    }

    @Test
    void fluid_output_keeps_legacy_json_shape_and_clamps_chance() {
        FluidStack stack = new FluidStack(Fluids.WATER, 500);
        JsonObject json = new JsonObject();
        json.addProperty("type", "fluid");
        json.add("stack", FluidStack.CODEC.encodeStart(jsonOps(), stack).getOrThrow());
        json.addProperty("chance", 2F);

        MachineOutput decoded = MachineOutput.CODEC.parse(jsonOps(), json).getOrThrow();
        JsonObject encoded = MachineOutput.CODEC.encodeStart(jsonOps(), decoded).getOrThrow().getAsJsonObject();

        assertThat(decoded).isInstanceOfSatisfying(MachineOutput.FluidOutput.class, output -> {
            assertThat(output.stack().getFluid()).isEqualTo(Fluids.WATER);
            assertThat(output.stack().getAmount()).isEqualTo(500);
            assertThat(output.chance()).isEqualTo(1F);
        });
        assertThat(encoded.get("type").getAsString()).isEqualTo("fluid");
        assertThat(encoded.getAsJsonObject("stack").get("amount").getAsInt()).isEqualTo(500);
    }

    @Test
    void custom_output_roundtrips_through_the_registered_codec() {
        TestOutput output = new TestOutput(7, 0.25F);

        JsonObject encoded = MachineOutput.CODEC.encodeStart(jsonOps(), output).getOrThrow().getAsJsonObject();
        MachineOutput decoded = MachineOutput.CODEC.parse(jsonOps(), encoded).getOrThrow();

        assertThat(encoded.get("type").getAsString()).isEqualTo(TEST_ID.toString());
        assertThat(decoded).isEqualTo(output);
    }

    @Test
    void output_type_owns_chance_copy_and_modifier_transformations() {
        TestOutput output = new TestOutput(7, 0.25F);
        RecipeModifier modifier = new RecipeModifier("test_output", RecipeModifier.IOType.OUTPUT,
                3F, RecipeModifier.Operation.ADD, false);

        assertThat(output.withChance(0.75F)).isEqualTo(new TestOutput(7, 0.75F));
        assertThat(output.applyModifiers(List.of(modifier))).isEqualTo(new TestOutput(10, 0.25F));
        assertThat(MachineOutput.copyOf(output)).isEqualTo(output).isNotSameAs(output);
    }

    @Test
    void unknown_output_type_is_rejected_with_a_stable_error() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "mmcr_test:missing_output");

        assertThatThrownBy(() -> MachineOutput.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow())
                .hasMessageContaining("Unknown output type: mmcr_test:missing_output");
    }

    private static int modifierAmount(List<RecipeModifier> modifiers) {
        return modifiers.stream().mapToInt(modifier -> Math.round(modifier.getModifier())).sum();
    }

    private static DynamicOps<JsonElement> jsonOps() {
        return RegistryOps.create(JsonOps.INSTANCE,
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    private record TestOutput(int value, float chance) implements CustomOutput {
        private TestOutput {
            chance = MachineOutput.clampChance(chance);
        }

        @Override
        public OutputType<TestOutput> outputType() {
            return TEST_TYPE;
        }
    }
}
