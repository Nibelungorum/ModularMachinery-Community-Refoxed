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
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import com.mojang.serialization.MapCodec;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import io.netty.handler.codec.DecoderException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void decodesHandWrittenLegacyBuiltInRequirementWireFormats() {
        assertThat(decodeLegacy(0, buffer -> {
            buffer.writeEnum(RecipeModifier.IOType.OUTPUT);
            writeTags(buffer, "item");
            buffer.writeUtf("{\"id\":\"minecraft:iron_ingot\",\"count\":2}");
            buffer.writeFloat(0.5F);
        }).requirements()).singleElement().isInstanceOfSatisfying(ItemRequirement.class, requirement -> {
            assertThat(requirement.io()).isEqualTo(RecipeModifier.IOType.OUTPUT);
            assertThat(requirement.stack().getCount()).isEqualTo(2);
            assertThat(requirement.tags()).containsExactly("item");
        });
        assertThat(decodeLegacy(1, buffer -> {
            buffer.writeEnum(RecipeModifier.IOType.OUTPUT);
            writeTags(buffer, "fluid");
            buffer.writeUtf("{\"id\":\"minecraft:water\",\"amount\":250}");
            buffer.writeFloat(0.25F);
        }).requirements()).singleElement().isInstanceOfSatisfying(FluidRequirement.class, requirement -> {
            assertThat(requirement.io()).isEqualTo(RecipeModifier.IOType.OUTPUT);
            assertThat(requirement.stack().getAmount()).isEqualTo(250);
            assertThat(requirement.tags()).containsExactly("fluid");
        });
        assertThat(decodeLegacy(2, buffer -> {
            buffer.writeEnum(RecipeModifier.IOType.INPUT);
            writeTags(buffer, "energy");
            buffer.writeVarInt(40);
        }).requirements()).containsExactly(new EnergyRequirement(RecipeModifier.IOType.INPUT, 40, List.of("energy")));
        assertThat(decodeLegacy(3, buffer -> {
            buffer.writeEnum(RecipeModifier.IOType.OUTPUT);
            buffer.writeUtf("scalar");
            buffer.writeFloat(1F);
            buffer.writeFloat(2F);
        }).requirements()).singleElement().satisfies(requirement -> {
            assertThat(requirement.type().id()).isEqualTo(MMCR.id("smart_interface"));
            assertThat(requirement.io()).isEqualTo(RecipeModifier.IOType.OUTPUT);
        });
    }

    @Test
    void rejectsUnknownOversizedAndResidualNewRequirementPayloads() {
        RegistryFriendlyByteBuf unsupportedVersion = buffer();
        unsupportedVersion.writeVarInt(-1);
        unsupportedVersion.writeVarInt(2);
        assertThatThrownBy(() -> MachineRecipeSyncCodec.decode(unsupportedVersion)).isInstanceOf(DecoderException.class)
                .hasMessageContaining("Unsupported machine recipe sync version");
        assertThatThrownBy(() -> MachineRecipeSyncCodec.decode(newRequirementBuffer(MMCR.id("unknown"), 0, buffer -> {
        }))).isInstanceOf(DecoderException.class);
        assertThatThrownBy(() -> MachineRecipeSyncCodec.decode(newRequirementBuffer(MMCR.id("energy"),
                RecipeSyncCodec.DEFAULT_MAX_PAYLOAD_SIZE + 1, buffer -> {
                }))).isInstanceOf(DecoderException.class);
        assertThatThrownBy(() -> MachineRecipeSyncCodec.decode(newRequirementBuffer(MMCR.id("energy"), -1, buffer -> {
            buffer.writeUtf("{\"type\":\"mmcr:energy\",\"io\":\"input\",\"fe_per_tick\":40}");
            buffer.writeByte(0);
        }))).isInstanceOf(DecoderException.class).hasMessageContaining("Invalid requirement payload");
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    private static MachineRecipe decodeLegacy(int kind, java.util.function.Consumer<RegistryFriendlyByteBuf> writer) {
        RegistryFriendlyByteBuf buffer = buffer();
        Identifier.STREAM_CODEC.encode(buffer, MMCR.id("legacy"));
        Identifier.STREAM_CODEC.encode(buffer, MMCR.id("machine"));
        buffer.writeVarInt(20);
        buffer.writeVarInt(1);
        buffer.writeVarInt(kind);
        writer.accept(buffer);
        writeLegacyTail(buffer);
        return MachineRecipeSyncCodec.decode(buffer);
    }

    private static RegistryFriendlyByteBuf newRequirementBuffer(Identifier type, int size,
                                                                  java.util.function.Consumer<RegistryFriendlyByteBuf> writer) {
        RegistryFriendlyByteBuf buffer = buffer();
        RegistryFriendlyByteBuf payload = buffer();
        writer.accept(payload);
        buffer.writeVarInt(-1);
        buffer.writeVarInt(1);
        Identifier.STREAM_CODEC.encode(buffer, MMCR.id("new"));
        Identifier.STREAM_CODEC.encode(buffer, MMCR.id("machine"));
        buffer.writeVarInt(20);
        buffer.writeVarInt(1);
        Identifier.STREAM_CODEC.encode(buffer, type);
        int payloadSize = size < 0 ? payload.writerIndex() : size;
        buffer.writeVarInt(payloadSize);
        if (payloadSize <= payload.writerIndex()) buffer.writeBytes(payload, 0, payloadSize);
        return buffer;
    }

    private static void writeLegacyTail(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(0);
        buffer.writeVarInt(0);
        buffer.writeVarInt(1);
        buffer.writeBoolean(false);
        buffer.writeBoolean(false);
        buffer.writeVarInt(0);
        buffer.writeBoolean(false);
        buffer.writeVarInt(0);
    }

    private static void writeTags(RegistryFriendlyByteBuf buffer, String tag) {
        buffer.writeVarInt(1);
        buffer.writeUtf(tag);
    }

    private record ScalarRequirement(RecipeModifier.IOType io, int value, List<String> tags) implements MachineRequirement {
        private static final RequirementType<ScalarRequirement> TYPE = new RequirementType.Definition<>(
                MMCR.id("scalar_requirement"), MapCodec.unit(() -> new ScalarRequirement(RecipeModifier.IOType.INPUT,
                0, List.of())), (requirement, capabilities, context) -> null,
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
                (output, modifiers) -> output, output -> output,
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
