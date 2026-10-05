package cn.howxu.mmcr.compat.pneumaticcraft;

import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.internal.sync.MachineRecipeSyncCodec;
import cn.howxu.mmcr.test.TestBootstrap;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real codec, immutable declaration and complete recipe wire tests.
 * @author howxu <dev@howxu.cn>
 */
class AirRequirementCodecTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void pressureOnlyInputRoundTripsAndOutputRejectsPressureField() {
        for (var requirement : List.of(AirRequirement.input(0, 4F, List.of("drive")), AirRequirement.output(0),
                AirRequirement.input(Long.MAX_VALUE, 0F), AirRequirement.output(80, List.of("out")))) {
            var encoded = AirRequirement.CODEC.codec().encodeStart(JsonOps.INSTANCE, requirement).getOrThrow();
            assertThat(AirRequirement.CODEC.codec().parse(JsonOps.INSTANCE, encoded).getOrThrow()).isEqualTo(requirement);
        }
        assertThat(parse("{\"type\":\"pneumaticcraft:air\",\"io\":\"input\",\"air_per_tick\":0,"
                + "\"min_pressure\":4,\"tags\":[\"drive\"]}")).isEqualTo(AirRequirement.input(0, 4F, List.of("drive")));
        assertInvalid("{\"type\":\"pneumaticcraft:air\",\"io\":\"output\",\"air_per_tick\":80,\"min_pressure\":0}");
    }

    @Test
    void codecRejectsInvalidFieldsAndNumbers() {
        for (String fields : List.of("\"io\":\"unknown\",\"air_per_tick\":1", "\"io\":\"input\",\"air_per_tick\":-1",
                "\"io\":\"input\"", "\"air_per_tick\":1", "\"io\":\"input\",\"air_per_tick\":1,\"min_pressure\":\"bad\"",
                "\"io\":\"input\",\"air_per_tick\":1,\"tags\":42")) {
            assertInvalid("{\"type\":\"pneumaticcraft:air\"," + fields + "}");
        }
        assertInvalid("{\"type\":\"other:air\",\"io\":\"input\",\"air_per_tick\":1}");
        assertInvalid("{\"io\":\"input\",\"air_per_tick\":1}");
        for (float pressure : new float[]{-1F, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            assertThatThrownBy(() -> AirRequirement.input(1, pressure)).isInstanceOf(IllegalArgumentException.class);
            var json = JsonParser.parseString("{\"type\":\"pneumaticcraft:air\",\"io\":\"input\",\"air_per_tick\":1}")
                    .getAsJsonObject();
            json.addProperty("min_pressure", pressure);
            assertThat(AirRequirement.CODEC.codec().parse(JsonOps.INSTANCE, json).error()).isPresent();
        }
        assertThatThrownBy(() -> AirRequirement.output(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tagsAreCopiedAndBoundedAndCanonicalCopyPreservesType() {
        var tags = new ArrayList<>(List.of("drive"));
        var requirement = AirRequirement.input(40, 4F, tags);
        tags.add("changed");
        assertThat(requirement.tags()).containsExactly("drive");
        assertThatThrownBy(() -> requirement.tags().add("changed")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> AirRequirement.input(0, 0F, Collections.nCopies(1025, "tag")))
                .isInstanceOf(IllegalArgumentException.class);
        try (var scope = RequirementHandlerRegistry.openTestScope()) {
            PneumaticRecipeTypes.register();
            assertThat(MachineRequirement.copyOf(requirement)).isEqualTo(requirement);
            assertThat(MachineRequirement.copyOf(requirement).type()).isSameAs(AirRequirement.TYPE);
        }
    }

    @Test
    void completeMachineRecipeSyncRoundTripsAirRequirements() {
        try (var scope = RequirementHandlerRegistry.openTestScope()) {
            PneumaticRecipeTypes.register();
            List<MachineRequirement> requirements = List.of(AirRequirement.input(0, 4F, List.of("drive")),
                    AirRequirement.output(Long.MAX_VALUE, List.of("exhaust")));
            var recipe = MachineRecipe.fromCanonical(ResourceLocation.parse("test:air_sync"),
                    ResourceLocation.parse("test:machine"), 20, requirements, List.of(), List.of(),
                    2, 3, true, true, true, Set.of());
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                MachineRecipeSyncCodec.encode(buffer, recipe);
                var decoded = MachineRecipeSyncCodec.decode(buffer);
                assertThat(decoded.requirements()).containsExactlyElementsOf(requirements);
                assertThat(decoded.requirements()).allMatch(value -> value.type() == AirRequirement.TYPE);
                assertThat(buffer.isReadable()).isFalse();
            } finally {
                buffer.release();
            }
        }
    }

    private static AirRequirement parse(String json) {
        return AirRequirement.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    private static void assertInvalid(String json) {
        assertThat(AirRequirement.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error()).isPresent();
    }
}
