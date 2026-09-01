package cn.howxu.mmcr.test.capability;

import cn.howxu.mmcr.api.capability.facet.ResourceFacet;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.internal.storage.LongResourceStorage;

/**
 * Transactional resource fixture with a single identity-bearing slot.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class TestResourceFacet implements ResourceFacet<TestResource> {
    private final ResourceStorage<TestResource> storage = new LongResourceStorage<>(
            TestResource.class, 1, 10L, resource -> false, null);

    @Override
    public Class<TestResource> resourceType() {
        return TestResource.class;
    }

    @Override
    public ResourceStorage<TestResource> storage() {
        return storage;
    }
}
