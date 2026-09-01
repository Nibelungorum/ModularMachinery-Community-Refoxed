package cn.howxu.mmcr.internal.sync;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.CustomOutput;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.OutputType;
import cn.howxu.mmcr.api.recipe.RecipeSyncCodec;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import com.mojang.serialization.MapCodec;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author howxu <dev@howxu.cn>
 */
class MachineRecipeSyncCodecTest {
    @Test
    void roundTripsRegisteredCustomRequirementAndOutput() {
        try (RequirementHandlerRegistry.TestScope requirements = RequirementHandlerRegistry.openTestScope();
             OutputRegistry.TestScope outputs = OutputRegistry.openTestScope()) {
            RequirementHandlerRegistry.register(ScalarRequirement.TYPE);
            OutputRegistry.register(ScalarOutput.TYPE);
            MachineRecipe original = MachineRecipe.fromCanonical(MMCR.id("custom_sync"), MMCR.id("machine"), 20,
                    List.of(new ScalarRequirement(RecipeModifier.IOType.INPUT, 12, List.of("input"))),
                    List.of(new ScalarOutput(34, 0.75F)), List.of(), 2, 3, true, true,
                    List.of(), true, Set.of(MMCR.id("host")));
            RegistryFriendlyByteBuf buffer = buffer();

            MachineRecipeSyncCodec.encode(buffer, original);
            MachineRecipe decoded = MachineRecipeSyncCodec.decode(buffer);

            assertThat(decoded.id()).isEqualTo(original.id());
            assertThat(decoded.machineId()).isEqualTo(original.machineId());
            assertThat(decoded.requirements()).containsExactly(new ScalarRequirement(RecipeModifier.IOType.INPUT,
                    12, List.of("input")));
            assertThat(decoded.machineOutputs()).containsExactly(new ScalarOutput(34, 0.75F));
            assertThat(decoded.isParallelized()).isTrue();
            assertThat(decoded.requiredHostIds()).containsExactly(MMCR.id("host"));
        }
    }

    @Test
    void roundTripsLegacyBuiltInRequirementWireFormat() {
        MachineRecipe original = MachineRecipe.fromCanonical(MMCR.id("legacy_sync"), MMCR.id("machine"), 20,
                List.of(new EnergyRequirement(RecipeModifier.IOType.INPUT, 40, List.of("legacy"))),
                List.of(), List.of(), 0, 1, false, false, List.of(), false, Set.of());
        RegistryFriendlyByteBuf buffer = buffer();

        MachineRecipeSyncCodec.encode(buffer, original);
        MachineRecipe decoded = MachineRecipeSyncCodec.decode(buffer);

        assertThat(decoded.requirements()).containsExactly(new EnergyRequirement(RecipeModifier.IOType.INPUT, 40,
                List.of("legacy")));
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    private record ScalarRequirement(RecipeModifier.IOType io, int value, List<String> tags) implements MachineRequirement {
        private static final RequirementType<ScalarRequirement> TYPE = new RequirementType.Definition<>(
                MMCR.id("scalar_requirement"), MapCodec.unit(() -> new ScalarRequirement(RecipeModifier.IOType.INPUT,
                0, List.of())), requirement -> (capabilities, context) -> null,
                requirement -> new ScalarRequirement(requirement.io(), requirement.value(), requirement.tags()),
                RecipeSyncCodec.of(32,
                        (buffer, requirement) -> {
                            buffer.writeEnum(requirement.io());
                            buffer.writeVarInt(requirement.value());
                            buffer.writeVarInt(requirement.tags().size());
                            for (String tag : requirement.tags()) buffer.writeUtf(tag);
                        },
                        buffer -> {
                            RecipeModifier.IOType io = buffer.readEnum(RecipeModifier.IOType.class);
                            int value = buffer.readVarInt();
                            int count = buffer.readVarInt();
                            if (count < 0 || count > 4) throw new IllegalArgumentException("Invalid scalar tag count: " + count);
                            java.util.ArrayList<String> tags = new java.util.ArrayList<>(count);
                            for (int index = 0; index < count; index++) tags.add(buffer.readUtf());
                            return new ScalarRequirement(io, value, tags);
                        }, requirement -> {
                            if (requirement.value() < 0 || requirement.tags().size() > 4) {
                                throw new IllegalArgumentException("Invalid scalar requirement");
                            }
                        }));

        private ScalarRequirement {
            tags = List.copyOf(tags);
        }

        @Override
        public RequirementType<ScalarRequirement> type() {
            return TYPE;
        }
    }

    private record ScalarOutput(int value, float chance) implements CustomOutput {
        private static final OutputType<ScalarOutput> TYPE = new OutputType.Definition<>(MMCR.id("scalar_output"),
                MapCodec.unit(() -> new ScalarOutput(0, 1F)), (output, chance) -> new ScalarOutput(output.value(), chance),
                (output, modifiers) -> output, output -> output, OutputType.Presentation.defaults(MMCR.id("scalar_output")),
                MMCR.id("scalar_output").toString(),
                (output, tags) -> new ScalarRequirement(RecipeModifier.IOType.OUTPUT, output.value(), tags),
                requirement -> requirement instanceof ScalarRequirement scalar
                        && scalar.io() == RecipeModifier.IOType.OUTPUT,
                requirement -> requirement instanceof ScalarRequirement scalar
                        && scalar.io() == RecipeModifier.IOType.OUTPUT
                        ? new ScalarOutput(scalar.value(), 1F) : null,
                RecipeSyncCodec.of(8, (buffer, output) -> {
                    buffer.writeVarInt(output.value());
                    buffer.writeFloat(output.chance());
                }, buffer -> new ScalarOutput(buffer.readVarInt(), buffer.readFloat()), output -> {
                    if (output.value() < 0) throw new IllegalArgumentException("Invalid scalar output");
                }));

        @Override
        public OutputType<ScalarOutput> outputType() {
            return TYPE;
        }
    }
}
