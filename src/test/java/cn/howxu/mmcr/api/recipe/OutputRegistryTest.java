package cn.howxu.mmcr.api.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies output type registration and canonical identity rules.
 * @author howxu <dev@howxu.cn>
 */
class OutputRegistryTest {
    private static final Identifier TEST_ID = Identifier.fromNamespaceAndPath("mmcr_test", "registry_output");
    private OutputRegistry.TestScope scope;

    @BeforeEach
    void openRegistryScope() {
        scope = OutputRegistry.openTestScope();
    }

    @AfterEach
    void closeRegistryScope() {
        scope.close();
    }

    @Test
    void registers_types_by_stable_identifier() {
        OutputType<TestOutput> type = type(TEST_ID);

        OutputRegistry.register(type);

        assertThat(OutputRegistry.typeFor(TEST_ID)).isSameAs(type);
        assertThat(OutputRegistry.canonicalType(type)).isSameAs(type);
    }

    @Test
    void rejects_duplicate_identifiers_and_reserved_builtins() {
        OutputRegistry.register(type(TEST_ID));

        assertThatThrownBy(() -> OutputRegistry.register(type(TEST_ID)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate output type");
        assertThatThrownBy(() -> OutputRegistry.register(type(Identifier.fromNamespaceAndPath("mmcr", "item"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved");
    }

    @Test
    void equal_identifier_substitute_is_not_canonical() {
        OutputType<TestOutput> canonical = type(TEST_ID);
        OutputType<TestOutput> substitute = type(TEST_ID);
        OutputRegistry.register(canonical);

        assertThat(OutputRegistry.canonicalType(substitute)).isNull();
        assertThatThrownBy(() -> MachineOutput.copyOf(new TestOutput(substitute, 3, 1F)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not registered canonically");
    }

    @Test
    void custom_types_do_not_leak_between_test_scopes() {
        OutputType<TestOutput> type = type(TEST_ID);
        OutputRegistry.register(type);
        scope.close();

        assertThat(OutputRegistry.typeFor(TEST_ID)).isNull();
        scope = OutputRegistry.openTestScope();
        assertThat(OutputRegistry.typeFor(TEST_ID)).isNull();
    }

    private static OutputType<TestOutput> type(Identifier id) {
        MapCodec<TestOutput> codec = MapCodec.unit(() -> new TestOutput(null, 3, 1F));
        return new OutputType.Definition<>(id, codec,
                (output, chance) -> new TestOutput(output.outputType(), output.value(), chance),
                (output, modifiers) -> output,
                output -> new TestOutput(output.outputType(), output.value(), output.chance()));
    }

    private record TestOutput(OutputType<TestOutput> outputType, int value, float chance)
            implements CustomOutput {
        private TestOutput {
            chance = MachineOutput.clampChance(chance);
        }

        @Override
        public OutputType<TestOutput> outputType() {
            return outputType;
        }
    }
}
