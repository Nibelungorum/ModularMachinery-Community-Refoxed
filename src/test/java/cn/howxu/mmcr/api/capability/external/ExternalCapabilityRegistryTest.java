package cn.howxu.mmcr.api.capability.external;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.type.CapabilityBinding;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/** Verifies the lifecycle of optional external capability adapters.
 * @author howxu <dev@howxu.cn>
 */
class ExternalCapabilityRegistryTest {
    private static final CapabilityType TYPE = new CapabilityType(MMCR.id("external_adapter_test"));

    @Test
    void invokes_available_adapters_once_and_skips_unavailable_adapters() {
        ExternalCapabilityRegistry registry = new ExternalCapabilityRegistry();
        AtomicInteger availableCalls = new AtomicInteger();
        AtomicInteger unavailableCalls = new AtomicInteger();
        registry.register(adapter("available", true, availableCalls));
        registry.register(adapter("unavailable", false, unavailableCalls));

        registry.freeze(new ExternalCapabilityContext());
        registry.freeze(new ExternalCapabilityContext());

        assertThat(availableCalls).hasValue(1);
        assertThat(unavailableCalls).hasValue(0);
    }

    @Test
    void rejects_duplicate_adapter_ids_and_registration_after_freeze() {
        ExternalCapabilityRegistry registry = new ExternalCapabilityRegistry();
        registry.register(adapter("duplicate", true, new AtomicInteger()));

        assertThatIllegalArgumentException().isThrownBy(() ->
                registry.register(adapter("duplicate", true, new AtomicInteger())));

        registry.freeze(new ExternalCapabilityContext());

        assertThatIllegalStateException().isThrownBy(() ->
                registry.register(adapter("late", true, new AtomicInteger())));
    }

    @Test
    void context_retains_handlers_bound_by_an_adapter() {
        ExternalCapabilityRegistry registry = new ExternalCapabilityRegistry();
        registry.register(new ExternalCapabilityAdapter() {
            @Override
            public net.minecraft.resources.Identifier id() {
                return MMCR.id("handler");
            }

            @Override
            public Set<CapabilityType> capabilityTypes() {
                return Set.of(TYPE);
            }

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public void register(ExternalCapabilityContext context) {
                context.bind(TYPE, new CapabilityBinding.ExternalExposure<>(MMCR.id("handler_native"),
                        String.class, (host, ioType, side) -> "handler"));
            }
        });
        ExternalCapabilityContext context = new ExternalCapabilityContext();

        registry.freeze(context);

        assertThat(context.bindings(TYPE)).hasSize(1);
    }

    private static ExternalCapabilityAdapter adapter(String id, boolean available, AtomicInteger calls) {
        return new ExternalCapabilityAdapter() {
            @Override
            public net.minecraft.resources.Identifier id() {
                return MMCR.id(id);
            }

            @Override
            public Set<CapabilityType> capabilityTypes() {
                return Set.of(TYPE);
            }

            @Override
            public boolean isAvailable() {
                return available;
            }

            @Override
            public void register(ExternalCapabilityContext context) {
                calls.incrementAndGet();
            }
        };
    }
}
