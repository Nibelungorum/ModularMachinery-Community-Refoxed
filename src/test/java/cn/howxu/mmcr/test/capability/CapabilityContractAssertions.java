package cn.howxu.mmcr.test.capability;

import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.assertj.core.api.Assertions;

/**
 * Reusable public-API assertions for external capability adapters.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class CapabilityContractAssertions {
    private CapabilityContractAssertions() {
    }

    public static CapabilityRequest request(long parallelism) {
        return request(IOType.INPUT, parallelism);
    }

    public static CapabilityRequest request(IOType ioType, long parallelism) {
        return new CapabilityRequest() {
            @Override
            public CapabilityType type() {
                return new CapabilityType(ResourceLocation.fromNamespaceAndPath("mmcr_test", "contract"));
            }

            @Override
            public IOType ioType() {
                return ioType;
            }

            @Override
            public long parallelism() {
                return parallelism;
            }
        };
    }

    public static void assertCommitted(CapabilityResult result) {
        Assertions.assertThat(result.success()).isTrue();
    }

    public static void assertRollsBack(TestScalarFacet facet) {
        try (Transaction transaction = Transaction.openRoot()) {
            IOType direction = facet.directions().values().iterator().next();
            assertCommitted(facet.prepareScalar(request(direction, 1L)).commit(transaction));
        }
        Assertions.assertThat(facet.amount()).isZero();
    }
}
