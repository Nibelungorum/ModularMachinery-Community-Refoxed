package cn.howxu.mmcr.compat.create;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises real codecs and immutable declaration validation.
 * @author howxu <dev@howxu.cn>
 */
class StressRequirementCodecTest {
    @Test
    void directional_declarations_round_trip_without_unrelated_rotation_fields() {
        for (var requirement : List.of(StressRequirement.input(8, 32, List.of("drive")),
                StressRequirement.output(16, -64, List.of("generator")))) {
            JsonElement encoded = StressRequirement.CODEC.codec().encodeStart(JsonOps.INSTANCE, requirement).getOrThrow();
            assertThat(StressRequirement.CODEC.codec().parse(JsonOps.INSTANCE, encoded).getOrThrow()).isEqualTo(requirement);
            assertThat(encoded.getAsJsonObject().has(requirement.rpm() == 0 ? "rpm" : "min_rpm")).isFalse();
        }
        assertThat(parse("{\"type\":\"create:stress\",\"io\":\"input\",\"stress\":8,\"min_rpm\":32,\"tags\":[\"drive\"]}"))
                .isEqualTo(StressRequirement.input(8, 32, List.of("drive")));
    }

    @Test
    void codec_rejects_missing_output_rpm_and_wrong_direction_fields() {
        for (String fields : List.of("\"io\":\"output\"", "\"io\":\"output\",\"rpm\":0",
                "\"io\":\"output\",\"rpm\":64,\"min_rpm\":0", "\"io\":\"input\",\"rpm\":0",
                "\"io\":\"input\",\"min_rpm\":-1", "\"io\":\"unknown\"")) {
            assertThat(StressRequirement.CODEC.codec().parse(JsonOps.INSTANCE,
                    JsonParser.parseString("{\"type\":\"create:stress\",\"stress\":8," + fields + "}")).error()).isPresent();
        }
    }

    @Test
    void codec_enforces_required_fields_and_rejects_malformed_optional_fields() {
        for (String json : List.of(
                "{\"io\":\"input\",\"stress\":8}",
                "{\"type\":\"create:stress\",\"stress\":8}",
                "{\"type\":\"create:stress\",\"io\":\"input\"}",
                "{\"type\":\"other:stress\",\"io\":\"input\",\"stress\":8}",
                "{\"type\":\"create:stress\",\"io\":\"input\",\"stress\":8,\"min_rpm\":\"bad\"}",
                "{\"type\":\"create:stress\",\"io\":\"output\",\"stress\":8,\"rpm\":\"bad\"}",
                "{\"type\":\"create:stress\",\"io\":\"input\",\"stress\":8,\"tags\":42}")) {
            assertThat(StressRequirement.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error()).isPresent();
        }
    }

    @Test
    void codec_and_factories_reject_invalid_numbers_and_isolate_tags() {
        for (double value : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThatThrownBy(() -> StressRequirement.input(value, 0)).isInstanceOf(IllegalArgumentException.class);
            var json = JsonParser.parseString("{\"type\":\"create:stress\",\"io\":\"input\",\"stress\":1}").getAsJsonObject();
            json.addProperty("stress", value);
            assertThat(StressRequirement.CODEC.codec().parse(JsonOps.INSTANCE, json).error()).isPresent();
        }
        for (double value : new double[]{0, Double.NaN, Double.NEGATIVE_INFINITY}) {
            assertThatThrownBy(() -> StressRequirement.output(8, value)).isInstanceOf(IllegalArgumentException.class);
            var json = JsonParser.parseString("{\"type\":\"create:stress\",\"io\":\"output\",\"stress\":8}").getAsJsonObject();
            json.addProperty("rpm", value);
            assertThat(StressRequirement.CODEC.codec().parse(JsonOps.INSTANCE, json).error()).isPresent();
        }
        for (double value : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThatThrownBy(() -> StressRequirement.input(8, value)).isInstanceOf(IllegalArgumentException.class);
            var json = JsonParser.parseString("{\"type\":\"create:stress\",\"io\":\"input\",\"stress\":8}").getAsJsonObject();
            json.addProperty("min_rpm", value);
            assertThat(StressRequirement.CODEC.codec().parse(JsonOps.INSTANCE, json).error()).isPresent();
        }
        var tags = new ArrayList<>(List.of("drive"));
        var requirement = StressRequirement.input(8, 0, tags);
        tags.add("changed");
        assertThat(requirement.tags()).containsExactly("drive");
        assertThatThrownBy(() -> requirement.tags().add("changed")).isInstanceOf(UnsupportedOperationException.class);
    }

    private static StressRequirement parse(String json) {
        return StressRequirement.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }
}
