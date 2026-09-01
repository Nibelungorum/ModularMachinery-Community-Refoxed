package cn.howxu.mmcr.test.capability;

import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.facet.ResourceFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.internal.storage.LongResourceStorage;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.resources.Identifier;

import java.util.Set;

/**
 * Transactional resource fixture with a single identity-bearing slot.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class TestResourceFacet implements MachineCapability, ResourceFacet<TestResource> {
    private final CapabilityType type = new CapabilityType(Identifier.fromNamespaceAndPath("mmcr_test", "resource"));
    private final ResourceStorage<TestResource> storage = new LongResourceStorage<>(
            TestResource.class, 1, 10L, resource -> false, null);
    private final CapabilityView view = new CapabilityView() {
        @Override
        public CapabilityType type() {
            return TestResourceFacet.this.type();
        }

        @Override
        public IOType ioType() {
            return TestResourceFacet.this.ioType();
        }

        @Override
        public Set<Class<? extends CapabilityFacet>> facets() {
            return Set.of(ResourceFacet.class);
        }
    };

    @Override
    public Class<TestResource> resourceType() {
        return TestResource.class;
    }

    @Override
    public ResourceStorage<TestResource> storage() {
        return storage;
    }

    @Override
    public CapabilityType type() {
        return type;
    }

    @Override
    public IOType ioType() {
        return IOType.INPUT;
    }

    @Override
    public CapabilityView view() {
        return view;
    }

    @Override
    public CapabilityOperation prepare(CapabilityRequest request) {
        throw new UnsupportedOperationException("resource fixture has no generic operation");
    }
}
