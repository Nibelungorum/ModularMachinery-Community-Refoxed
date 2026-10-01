package cn.howxu.mmcr.internal.sync;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.CustomOutput;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.OutputType;
import cn.howxu.mmcr.api.recipe.RecipeSyncCodec;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.LevelRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.serialization.MapCodec;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.function.Consumer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import io.netty.handler.codec.DecoderException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author howxu <dev@howxu.cn>
 */
class MachineRecipeSyncCodecTest {
    private static final ResourceLocation SYNC_LEVEL_TYPE = MMCR.id("sync_level_type");
    private static final ResourceLocation SYNC_LEVEL = MMCR.id("sync_level");

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void registerSyncLevel() {
        TestBootstrap.beginRegistration();
        TestBootstrap.registerType(new LevelType(SYNC_LEVEL_TYPE, Component.literal("Sync Level")));
        TestBootstrap.registerLevel(new MachineLevel(SYNC_LEVEL, SYNC_LEVEL_TYPE, 1,
                new BlockPredicate.OfBlockState(Blocks.COPPER_BLOCK.defaultBlockState()),
                ItemStack.EMPTY, ModifierDefinition.EMPTY));
        TestBootstrap.freezeRegistration();
    }

    @Test
    void roundTripsVersionThreeRegisteredCustomAndLevelRequirements() {
        try (RequirementHandlerRegistry.TestScope requirementScope = RequirementHandlerRegistry.openTestScope();
             OutputRegistry.TestScope outputs = OutputRegistry.openTestScope()) {
            RequirementHandlerRegistry.register(ScalarRequirement.TYPE);
            OutputRegistry.register(ScalarOutput.TYPE);
            List<MachineRequirement> requirements = List.of(
                    new ScalarRequirement(RecipeModifier.IOType.INPUT, 12, List.of("input")),
                    LevelRequirement.input(SYNC_LEVEL_TYPE, SYNC_LEVEL));
            MachineRecipe original = MachineRecipe.fromCanonical(MMCR.id("custom_sync"), MMCR.id("machine"), 20,
                    requirements,
                    List.of(new ScalarOutput(34, 0.75F)), List.of(), 2, 3, true, true,
                    true, Set.of(MMCR.id("host")));
            RegistryFriendlyByteBuf buffer = buffer();

            MachineRecipeSyncCodec.encode(buffer, original);
            assertThat(buffer.readVarInt()).isEqualTo(-1);
            assertThat(buffer.readVarInt()).isEqualTo(3);
            buffer.readerIndex(0);
            MachineRecipe decoded = MachineRecipeSyncCodec.decode(buffer);

            assertThat(decoded.id()).isEqualTo(original.id());
            assertThat(decoded.recipePoolId()).isEqualTo(original.recipePoolId());
            assertThat(decoded.requirements()).containsExactlyElementsOf(requirements);
            assertThat(decoded.levelRequirements()).singleElement()
                    .isEqualTo(LevelRequirement.input(SYNC_LEVEL_TYPE, SYNC_LEVEL));
            assertThat(decoded.machineOutputs()).containsExactly(new ScalarOutput(34, 0.75F));
            assertThat(decoded.isParallelized()).isTrue();
            assertThat(decoded.requiredHostIds()).containsExactly(MMCR.id("host"));
        }
    }

    @Test
    void rejectsLegacyRequirementWireFormats() {
        RegistryFriendlyByteBuf buffer = buffer();
        ResourceLocation.STREAM_CODEC.encode(buffer, MMCR.id("legacy"));

        assertThatThrownBy(() -> MachineRecipeSyncCodec.decode(buffer))
                .isInstanceOf(DecoderException.class)
                .hasMessageContaining("Unsupported machine recipe sync format");
    }

    @Test
    void rejectsUnsupportedVersionsUnknownOversizedAndResidualNewRequirementPayloads() {
        RegistryFriendlyByteBuf versionTwo = buffer();
        versionTwo.writeVarInt(-1);
        versionTwo.writeVarInt(2);
        assertThatThrownBy(() -> MachineRecipeSyncCodec.decode(versionTwo))
                .isInstanceOf(DecoderException.class)
                .hasMessage("Unsupported machine recipe sync version: 2");
        RegistryFriendlyByteBuf versionFour = buffer();
        versionFour.writeVarInt(-1);
        versionFour.writeVarInt(4);
        assertThatThrownBy(() -> MachineRecipeSyncCodec.decode(versionFour))
                .isInstanceOf(DecoderException.class)
                .hasMessage("Unsupported machine recipe sync version: 4");
        assertThatThrownBy(() -> MachineRecipeSyncCodec.decode(newRequirementBuffer(MMCR.id("unknown"), 0, buffer -> {
        }))).isInstanceOf(DecoderException.class);
        assertThatThrownBy(() -> MachineRecipeSyncCodec.decode(newRequirementBuffer(EnergyRequirement.TYPE.id(),
                RecipeSyncCodec.DEFAULT_MAX_PAYLOAD_SIZE + 1, buffer -> {
                }))).isInstanceOf(DecoderException.class);
        assertThatThrownBy(() -> MachineRecipeSyncCodec.decode(newRequirementBuffer(EnergyRequirement.TYPE.id(), -1, buffer -> {
            buffer.writeUtf("{\"type\":\"neoforge:energy\",\"io\":\"input\",\"fe_per_tick\":40}");
            buffer.writeByte(0);
        }))).isInstanceOf(DecoderException.class).hasMessageContaining("Invalid requirement payload");
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    private static RegistryFriendlyByteBuf newRequirementBuffer(ResourceLocation type, int size,
                                                                  Consumer<RegistryFriendlyByteBuf> writer) {
        RegistryFriendlyByteBuf buffer = buffer();
        RegistryFriendlyByteBuf payload = buffer();
        writer.accept(payload);
        buffer.writeVarInt(-1);
        buffer.writeVarInt(3);
        ResourceLocation.STREAM_CODEC.encode(buffer, MMCR.id("new"));
        ResourceLocation.STREAM_CODEC.encode(buffer, MMCR.id("recipe_pool"));
        buffer.writeVarInt(20);
        buffer.writeVarInt(1);
        ResourceLocation.STREAM_CODEC.encode(buffer, type);
        int payloadSize = size < 0 ? payload.writerIndex() : size;
        buffer.writeVarInt(payloadSize);
        if (payloadSize <= payload.writerIndex()) buffer.writeBytes(payload, 0, payloadSize);
        return buffer;
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
                            ArrayList<String> tags = new ArrayList<>(count);
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
