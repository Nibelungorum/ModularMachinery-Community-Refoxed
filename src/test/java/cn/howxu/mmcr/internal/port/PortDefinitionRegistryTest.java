package cn.howxu.mmcr.internal.port;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.type.CapabilityFactory;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding.ExternalExposure;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.api.port.PortDefinitionRegistry;
import cn.howxu.mmcr.api.port.PortTierPolicy;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies ordered capability binding resolution and port definition lifecycle rules.
 *
 * @author howxu <dev@howxu.cn>
 */
class PortDefinitionRegistryTest {
    private static final CapabilityFactory FACTORY = context -> null;

    @BeforeEach
    void openRegistry() {
        PortDefinitionRegistry.clearForTesting();
    }

    @AfterEach
    void closeRegistry() {
        PortDefinitionRegistry.clearForTesting();
        PortKinds.all().forEach(kind -> PortDefinitionRegistry.register(kind.definition()));
        PortDefinitionRegistry.freeze();
    }

    @Test
    void resolves_a_single_binding_for_its_direction_and_tier() {
        CapabilityBinding binding = binding("item", IOType.INPUT, tierAtLeast(2));
        PortDefinition definition = PortDefinition.of(id("single"), List.of(binding));

        PortDefinitionRegistry.register(definition);

        assertThat(PortDefinitionRegistry.resolve(id("single"), IOType.INPUT, 2))
                .containsExactly(binding);
        assertThat(PortDefinitionRegistry.resolve(id("single"), IOType.INPUT, 1)).isEmpty();
        assertThat(PortDefinitionRegistry.resolve(id("single"), IOType.OUTPUT, 2)).isEmpty();
    }

    @Test
    void preserves_combined_binding_order_while_filtering_direction_and_tier() {
        CapabilityBinding item = binding("item", IOType.INPUT, tierAtLeast(1));
        CapabilityBinding fluid = binding("fluid", IOType.INPUT, tierAtLeast(3));
        CapabilityBinding energy = binding("energy", IOType.OUTPUT, tierAtLeast(1));
        CapabilityBinding gas = binding("gas", IOType.INPUT, tierAtLeast(4));
        PortDefinitionRegistry.register(PortDefinition.of(id("combined"), List.of(item, fluid, energy, gas)));

        assertThat(PortDefinitionRegistry.resolve(id("combined"), IOType.INPUT, 3))
                .containsExactly(item, fluid);
        assertThat(PortDefinitionRegistry.resolve(id("combined"), IOType.OUTPUT, 1))
                .containsExactly(energy);
    }

    @Test
    void resolves_two_bindings_in_the_declared_order() {
        CapabilityBinding item = binding("two_item", IOType.INPUT, PortTierPolicy.always());
        CapabilityBinding fluid = binding("two_fluid", IOType.INPUT, PortTierPolicy.always());
        PortDefinitionRegistry.register(PortDefinition.of(id("two"), List.of(item, fluid)));

        assertThat(PortDefinitionRegistry.resolve(id("two"), IOType.INPUT, 0))
                .containsExactly(item, fluid);
    }

    @Test
    void retains_a_custom_binding_factory_and_typed_external_exposure() {
        AtomicBoolean factoryCalled = new AtomicBoolean();
        CapabilityFactory factory = context -> {
            factoryCalled.set(true);
            return null;
        };
        ExternalExposure<String> exposure = new ExternalExposure<>(id("native"), String.class,
                (host, ioType, side) -> "exposed");
        CapabilityBinding binding = new CapabilityBinding(
                new CapabilityType(MMCR.id("custom")), IOType.INPUT, factory, PortTierPolicy.always(), exposure);
        PortDefinitionRegistry.register(PortDefinition.of(id("custom"), binding));

        CapabilityBinding resolved = PortDefinitionRegistry.resolve(id("custom"), IOType.INPUT, 0).getFirst();
        assertThat(resolved.externalExposure()).hasValue(exposure);
        resolved.factory().create(null);
        assertThat(factoryCalled).isTrue();
    }

    @Test
    void generic_combined_kind_accepts_a_third_definition_binding() {
        CapabilityBinding item = binding("generic_item", IOType.INPUT, PortTierPolicy.always());
        CapabilityBinding fluid = binding("generic_fluid", IOType.INPUT, PortTierPolicy.always());
        CapabilityBinding gas = binding("generic_gas", IOType.INPUT, PortTierPolicy.always());
        PortDefinition definition = PortDefinition.of(id("generic_combined"), List.of(item, fluid, gas));
        IOPortKind kind = new PortKinds.CombinedKind("generic_combined", IOType.INPUT,
                List.of(
                        new PortFamilyDescriptor(MMCR.id("generic_item"), IOType.INPUT, 0, List.of("item")),
                        new PortFamilyDescriptor(MMCR.id("generic_fluid"), IOType.INPUT, 0, List.of("fluid")),
                        new PortFamilyDescriptor(MMCR.id("generic_gas"), IOType.INPUT, 0, List.of("gas"))),
                PortKinds.ITEM_INPUT.entityFactory(), definition);

        assertThat(kind.definition()).isSameAs(definition);
        assertThat(kind.capabilityTypes()).containsExactly(item.type(), fluid.type(), gas.type());
    }

    @Test
    void rejects_duplicate_bindings_for_the_same_capability_type() {
        CapabilityBinding first = binding("item", IOType.INPUT, PortTierPolicy.always());
        CapabilityBinding duplicate = binding("item", IOType.OUTPUT, PortTierPolicy.always());

        assertThatThrownBy(() -> PortDefinition.of(id("duplicate"), List.of(first, duplicate)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    void definitions_and_registry_views_are_immutable() {
        List<CapabilityBinding> source = new ArrayList<>();
        CapabilityBinding binding = binding("item", IOType.INPUT, PortTierPolicy.always());
        source.add(binding);
        PortDefinition definition = PortDefinition.of(id("immutable"), source);
        source.clear();
        PortDefinitionRegistry.register(definition);

        assertThat(definition.bindings()).containsExactly(binding);
        assertThatThrownBy(() -> definition.bindings().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> PortDefinitionRegistry.values().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejects_registration_after_freeze() {
        PortDefinitionRegistry.freeze();

        assertThatThrownBy(() -> PortDefinitionRegistry.register(
                PortDefinition.of(id("late"), List.of(binding("item", IOType.INPUT, PortTierPolicy.always())))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("frozen");
    }

    private static CapabilityBinding binding(String path, IOType ioType, PortTierPolicy tierPolicy) {
        return new CapabilityBinding(new CapabilityType(MMCR.id(path)), ioType, FACTORY, tierPolicy);
    }

    private static PortTierPolicy tierAtLeast(int minimum) {
        return (binding, tier) -> tier >= minimum;
    }

    private static Identifier id(String path) {
        return MMCR.id("test_port_" + path);
    }
}
