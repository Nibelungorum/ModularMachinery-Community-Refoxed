package cn.howxu.mmcr.compat.emi;

import cn.howxu.mmcr.test.TestBootstrap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author howxu <dev@howxu.cn>
 */
class SourceEmiStackTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void roundTripKeepsQuantityButQuantityIsNotResourceIdentity() {
        var serializer = new SourceEmiStackSerializer();
        var original = new SourceEmiStack(5_000_000_000L);
        original.setChance(0.5F);

        var decoded = serializer.deserialize(serializer.serialize(original));

        assertThat(decoded.getAmount()).isEqualTo(original.getAmount());
        assertThat(decoded.getChance()).isEqualTo(original.getChance());
        assertThat(decoded.getEmiStacks().getFirst().isEqual(new SourceEmiStack(1))).isTrue();
        assertThat(decoded.copy().setAmount(7).getAmount()).isEqualTo(7);
        assertThat(decoded.getAmount()).isEqualTo(5_000_000_000L);
        assertThat(decoded.getEmiStacks().getFirst().getKey()).isEqualTo(original.getId());
    }

    @Test
    void copyKeepsComparisonChanceAndIndependentRemainder() {
        Comparison comparison = Comparison.compareData(EmiStack::getAmount);
        EmiStack original = new SourceEmiStack(17).setChance(0.25F).comparison(comparison);
        original.setRemainder(new SourceEmiStack(17).setChance(0.5F));

        EmiStack copy = original.copy();

        assertThat(copy.getAmount()).isEqualTo(17);
        assertThat(copy.getChance()).isEqualTo(0.25F);
        assertThat(copy.isEqual(new SourceEmiStack(1))).isFalse();
        assertThat(copy.isEqual(original)).isTrue();
        assertThat(copy.getRemainder()).isNotSameAs(original.getRemainder());
        assertThat(copy.getRemainder().getChance()).isEqualTo(0.5F);
        copy.getRemainder().setAmount(3);
        copy.setAmount(4);
        assertThat(original.getRemainder().getAmount()).isEqualTo(17);
        assertThat(original.getAmount()).isEqualTo(17);
    }

    @Test
    void omittedQuantityAndChanceUseLegacyDefaults() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "mmcr:source");

        var decoded = new SourceEmiStackSerializer().deserialize(json);

        assertThat(decoded).isInstanceOf(SourceEmiStack.class);
        assertThat(decoded.getAmount()).isEqualTo(1);
        assertThat(decoded.getChance()).isEqualTo(1F);
        assertThat(decoded.getEmiStacks().getFirst().getComponentChanges().isEmpty()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "null", "[]", "1", "{}", "{\"type\":\"other:source\"}",
            "{\"type\":\"mmcr:source\",\"amount\":-1}",
            "{\"type\":\"mmcr:source\",\"amount\":0}",
            "{\"type\":\"mmcr:source\",\"amount\":1.5}",
            "{\"type\":\"mmcr:source\",\"amount\":9223372036854775808}",
            "{\"type\":\"mmcr:source\",\"amount\":\"2\"}",
            "{\"type\":\"mmcr:source\",\"chance\":null}",
            "{\"type\":\"mmcr:source\",\"chance\":-0.1}",
            "{\"type\":\"mmcr:source\",\"chance\":1.1}",
            "{\"type\":\"mmcr:source\",\"chance\":1.000000001}",
            "{\"type\":\"mmcr:source\",\"chance\":1e1000}",
            "{\"type\":\"mmcr:source\",\"chance\":\"NaN\"}"
    })
    void invalidSavedDataReturnsEmpty(String json) {
        assertThat(new SourceEmiStackSerializer().deserialize(JsonParser.parseString(json)))
                .isSameAs(EmiStack.EMPTY);
    }
}
