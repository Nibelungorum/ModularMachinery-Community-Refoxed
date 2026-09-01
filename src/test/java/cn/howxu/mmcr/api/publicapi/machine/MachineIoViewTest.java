package cn.howxu.mmcr.api.publicapi.machine;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.ExchangeFacet;
import cn.howxu.mmcr.api.capability.facet.PresentationFacet;
import cn.howxu.mmcr.api.capability.facet.ResourceFacet;
import cn.howxu.mmcr.api.capability.facet.ScalarFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.presentation.CapabilityDisplay;
import cn.howxu.mmcr.api.capability.presentation.CapabilityDisplayRegistry;
import cn.howxu.mmcr.api.capability.storage.FloatValueStorage;
import cn.howxu.mmcr.internal.capability.SmartInterfaceCapability;
import cn.howxu.mmcr.util.IOType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies named smart-interface values exposed through the machine IO view.
 *
 * @author howxu <dev@howxu.cn>
 */
class MachineIoViewTest {
    @Test
    void smart_interface_values_are_read_once_from_shared_input_and_output_capabilities() {
        FloatValueStorage storage = new FloatValueStorage();
        storage.set("mode", 2.5F);
        storage.set("enabled", 1F);
        MachineCapability input = new SmartInterfaceCapability(storage, IOType.INPUT);
        MachineCapability output = new SmartInterfaceCapability(storage, IOType.OUTPUT);
        MachineIoView view = new MachineIoView(new CapabilitySnapshot(List.of(input, output)));

        assertThat(view.smartInterfaceValue("mode")).contains(2.5F);
        assertThat(view.smartInterfaceValue("missing")).isEmpty();
        assertThat(view.smartInterfaceValue(null)).isEmpty();
        assertThat(view.smartInterfaceValues())
                .containsExactlyInAnyOrderEntriesOf(Map.of("mode", 2.5F, "enabled", 1F));
        assertThatThrownBy(() -> view.smartInterfaceValues().put("new", 3F))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void custom_resource_scalar_and_exchange_capabilities_use_presentation_entries() {
        CapabilityDisplayRegistry registry = new CapabilityDisplayRegistry();
        MachineCapability resource = new TestCapability("test_resource", ResourceFacet.class);
        MachineCapability scalar = new TestCapability("test_scalar", ScalarFacet.class);
        MachineCapability exchange = new TestCapability("test_exchange", ExchangeFacet.class);
        registry.register(resource.type(), ignored -> List.of(new CapabilityDisplay("resource", "4", "units", Optional.empty())));
        registry.register(scalar.type(), ignored -> List.of(new CapabilityDisplay("scalar", "2", "V", Optional.empty())));
        registry.register(exchange.type(), ignored -> List.of(new CapabilityDisplay("exchange", "1", "A", Optional.empty())));

        List<CapabilityDisplay> displays = List.of(resource, scalar, exchange).stream()
                .flatMap(capability -> registry.displays(capability).stream())
                .toList();
        assertThat(displays)
                .extracting(CapabilityDisplay::label)
                .containsExactly("resource", "scalar", "exchange");
    }

    @Test
    void missing_presentation_uses_a_single_bounded_fallback_entry() {
        CapabilityDisplayRegistry registry = new CapabilityDisplayRegistry();

        assertThat(registry.displays(new TestCapability("missing", PresentationFacet.class)))
                .containsExactly(new CapabilityDisplay("mmcr:missing", "Unavailable", "", Optional.empty()));
    }

    /**
     * @author howxu <dev@howxu.cn>
     */
    private record TestCapability(CapabilityType type, CapabilityView view) implements MachineCapability {
        TestCapability(String id, Class<? extends cn.howxu.mmcr.api.capability.facet.CapabilityFacet> facet) {
            this(new CapabilityType(cn.howxu.mmcr.MMCR.id(id)), new CapabilityView() {
                @Override public CapabilityType type() { return new CapabilityType(cn.howxu.mmcr.MMCR.id(id)); }
                @Override public IOType ioType() { return IOType.INPUT; }
                @Override public Set<Class<? extends cn.howxu.mmcr.api.capability.facet.CapabilityFacet>> facets() {
                    return Set.of(facet);
                }
            });
        }

        @Override public IOType ioType() { return view.ioType(); }
        @Override public CapabilityOperation prepare(CapabilityRequest request) { throw new UnsupportedOperationException(); }
    }
}
