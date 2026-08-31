package cn.howxu.mmcr.api.capability;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.type.CapabilityDefinition;
import cn.howxu.mmcr.api.capability.type.CapabilityFactory;
import cn.howxu.mmcr.api.capability.type.CapabilityRegistry;
import cn.howxu.mmcr.api.publicapi.ApiRuntime;
import cn.howxu.mmcr.internal.api.PublicApiBootstrap;
import cn.howxu.mmcr.test.TestBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the public capability definition and registry contract.
 *
 * @author howxu <dev@howxu.cn>
 */
class CapabilityRegistryTest {
    private static final CapabilityFactory FACTORY = context -> null;

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void beginCapabilityRegistration() {
        PublicApiBootstrap.clearForTesting();
        PublicApiBootstrap.begin();
    }

    @AfterEach
    void resetCapabilityRegistration() {
        PublicApiBootstrap.clearForTesting();
    }

    @Test
    void registers_definition_before_freeze_and_finds_it_by_equal_type() {
        CapabilityDefinition definition = definition("test");

        CapabilityRegistry.register(definition);

        assertThat(CapabilityRegistry.get(new CapabilityType(MMCR.id("test")))).isSameAs(definition);
    }

    @Test
    void bootstrap_registers_the_builtin_capability_definitions() {
        assertThat(CapabilityRegistry.values())
                .extracting(definition -> definition.type().id())
                .containsExactly(MMCR.id("item"), MMCR.id("fluid"), MMCR.id("energy"));
    }

    @Test
    void exposes_only_registry_operations_as_public_methods() {
        assertThat(Arrays.stream(CapabilityRegistry.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName))
                .containsExactlyInAnyOrder("register", "get", "values", "freeze");
    }

    @Test
    void capability_registration_checks_the_hook_without_holding_registry_monitors() {
        ApiRuntime.install(new ApiRuntime.Hook() {
            @Override
            public boolean isRegistrationOpen() {
                assertThat(Thread.holdsLock(CapabilityRegistry.class)).isFalse();
                assertThat(Thread.holdsLock(ApiRuntime.class)).isFalse();
                return true;
            }
        });

        CapabilityRegistry.register(definition("lock_boundary"));
    }

    @Test
    void rejects_duplicate_ids_even_when_capability_types_are_distinct_values() {
        CapabilityDefinition first = definition("duplicate");
        CapabilityDefinition second = definition("duplicate");
        CapabilityRegistry.register(first);

        assertThatThrownBy(() -> CapabilityRegistry.register(second))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    void values_are_immutable() {
        CapabilityRegistry.register(definition("immutable"));

        assertThatThrownBy(() -> CapabilityRegistry.values().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejects_null_definitions_immediately() {
        assertThatThrownBy(() -> CapabilityRegistry.register(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejects_registration_after_freeze() {
        CapabilityRegistry.freeze();

        assertThatThrownBy(() -> CapabilityRegistry.register(definition("late")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("frozen");
    }

    private static CapabilityDefinition definition(String path) {
        return new CapabilityDefinition(new CapabilityType(MMCR.id(path)), Set.<Class<? extends CapabilityFacet>>of(), FACTORY);
    }
}
