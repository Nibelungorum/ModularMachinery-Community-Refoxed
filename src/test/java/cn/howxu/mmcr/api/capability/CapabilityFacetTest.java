package cn.howxu.mmcr.api.capability;

import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.facet.ExchangeFacet;
import cn.howxu.mmcr.api.capability.facet.NetworkParticipantFacet;
import cn.howxu.mmcr.api.capability.facet.ResourceFacet;
import cn.howxu.mmcr.api.capability.facet.ScalarFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.internal.storage.LongResourceStorage;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Verifies typed capability facet contracts and snapshot preservation.
 *
 * @author howxu <dev@howxu.cn>
 */
class CapabilityFacetTest {
    @Test
    void incompatible_default_facet_does_not_query_the_view() {
        ProbeCapability capability = new ProbeCapability();
        assertThat(capability.facet(ResourceFacet.class)).isEmpty();
        assertThat(capability.viewReads).isZero();
    }

    @Test
    void compatible_default_facet_keeps_declarations_live() {
        ProbeCapability capability = new ProbeCapability();
        assertThat(capability.facet(ProbeFacet.class)).isEmpty();
        capability.declared = true;
        assertThat(capability.facet(ProbeFacet.class)).contains(capability);
        capability.declared = false;
        assertThat(capability.facet(ProbeFacet.class)).isEmpty();
        assertThat(capability.viewReads).isEqualTo(3);
    }

    @Test
    void null_default_facet_is_rejected_before_querying_the_view() {
        ProbeCapability capability = new ProbeCapability();
        assertThatNullPointerException().isThrownBy(() -> capability.facet(null));
        assertThat(capability.viewReads).isZero();
    }

    @Test
    void a_custom_override_can_return_a_separate_facet_instance() {
        ResourceFacet<String> adapter = new TestCapability();
        MachineCapability capability = new ProbeCapability() {
            @Override
            public <F extends CapabilityFacet> Optional<F> facet(Class<F> facetType) {
                if (facetType == ResourceFacet.class) return Optional.of(facetType.cast(adapter));
                return super.facet(facetType);
            }
        };
        assertThat(capability).isNotInstanceOf(ResourceFacet.class);
        assertThat(capability.facet(ResourceFacet.class)).contains(adapter);
    }

    @Test
    void lookup_returns_only_facets_declared_by_the_capability_view() {
        TestCapability capability = new TestCapability();

        assertThat(capability.facet(ResourceFacet.class)).contains(capability);
        assertThat(capability.facet(ScalarFacet.class)).contains(capability);
        assertThat(capability.facet(ExchangeFacet.class)).isEmpty();
        assertThat(capability.facet(NetworkParticipantFacet.class)).isEmpty();
        assertThat(capability.facet(CapabilityFacet.class)).isEmpty();
    }

    @Test
    void resource_and_scalar_facets_keep_their_distinct_contracts() {
        TestCapability capability = new TestCapability();
        ResourceFacet<String> resource = capability.facet(ResourceFacet.class).orElseThrow();
        ScalarFacet scalar = capability.facet(ScalarFacet.class).orElseThrow();

        assertThat(resource.resourceType()).isEqualTo(String.class);
        assertThat(resource.storage()).isSameAs(capability.resourceStorage);
        CapabilityOperation operation = scalar.prepareScalar(new TestRequest(capability.type(), IOType.INPUT, 1L));
        assertThat(operation.commit(null)).isEqualTo(CapabilityResult.successful());
    }

    @Test
    void snapshot_preserves_capability_identity_and_order() {
        TestCapability first = new TestCapability();
        TestCapability second = new TestCapability();

        CapabilitySnapshot snapshot = new CapabilitySnapshot(List.of(first, second));

        assertThat(snapshot.capabilities()).containsExactly(first, second);
        assertThat(snapshot.capabilities().get(0)).isSameAs(first);
        assertThat(snapshot.capabilities().get(1)).isSameAs(second);
    }

    private record TestRequest(CapabilityType type, IOType ioType, long parallelism) implements CapabilityRequest {
    }

    private static final class TestCapability implements MachineCapability, ResourceFacet<String>, ScalarFacet {
        private final ResourceStorage<String> resourceStorage = new LongResourceStorage<>(
                String.class, 1, 10L, String::isEmpty, () -> {});
        private final CapabilityView view = new CapabilityView() {
            @Override
            public CapabilityType type() {
                return TestCapability.this.type();
            }

            @Override
            public CapabilityDirections directions() {
                return TestCapability.this.directions();
            }

            @Override
            public Set<Class<? extends CapabilityFacet>> facets() {
                return Set.of(ResourceFacet.class, ScalarFacet.class);
            }
        };

        @Override
        public CapabilityType type() {
            return new CapabilityType(Identifier.fromNamespaceAndPath("mmcr_test", "facets"));
        }

        @Override
        public CapabilityDirections directions() {
            return CapabilityDirections.input();
        }

        @Override
        public CapabilityView view() {
            return view;
        }

        @Override
        public Class<String> resourceType() {
            return String.class;
        }

        @Override
        public ResourceStorage<String> storage() {
            return resourceStorage;
        }

        @Override
        public CapabilityOperation prepare(CapabilityRequest request) {
            return ignored -> CapabilityResult.successful();
        }

        @Override
        public CapabilityOperation prepareScalar(CapabilityRequest request) {
            return prepare(request);
        }
    }

    /** @author howxu <dev@howxu.cn> */
    private interface ProbeFacet extends CapabilityFacet { }

    /** @author howxu <dev@howxu.cn> */
    private static class ProbeCapability implements MachineCapability, ProbeFacet {
        private boolean declared;
        private int viewReads;

        @Override
        public CapabilityType type() {
            throw new AssertionError("Unexpected capability type query");
        }

        @Override
        public CapabilityView view() {
            viewReads++;
            return new CapabilityView() {
                @Override
                public CapabilityType type() {
                    return ProbeCapability.this.type();
                }

                @Override
                public Set<Class<? extends CapabilityFacet>> facets() {
                    return declared ? Set.of(ProbeFacet.class) : Set.of();
                }
            };
        }

        @Override
        public CapabilityOperation prepare(CapabilityRequest request) {
            throw new AssertionError("Unexpected resource planning");
        }
    }
}
