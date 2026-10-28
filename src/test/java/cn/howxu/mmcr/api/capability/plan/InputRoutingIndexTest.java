package cn.howxu.mmcr.api.capability.plan;

import cn.howxu.mmcr.api.capability.facet.InputRoutingFacet;
import cn.howxu.mmcr.api.recipe.InputScope;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.IntPredicate;

import static org.assertj.core.api.Assertions.assertThat;

/** @author howxu <dev@howxu.cn> */
class InputRoutingIndexTest {
    @Test
    void pinned_scope_does_not_admit_new_ordinary_sources() {
        BlockPos first = new BlockPos(1, 0, 0);
        BlockPos second = new BlockPos(2, 0, 0);
        InputRoutingFacet routing = new InputRoutingFacet(7,
                List.of(new InputRoutingFacet.Source(1, first), new InputRoutingFacet.Source(4, second)),
                List.of(new InputRoutingFacet.Port(first, false), new InputRoutingFacet.Port(second, false)));
        IntPredicate allowed = InputRoutingIndex.capture(routing)
                .allowed(new InputScope(InputScope.Kind.ORDINARY, List.of(first)));
        assertThat(allowed.test(1)).isTrue();
        assertThat(allowed.test(4)).isFalse();
        assertThat(allowed.test(0)).isFalse();
        assertThat(allowed.test(-1)).isFalse();
    }

    @Test
    void compiled_membership_matches_routing_with_sparse_and_duplicate_occurrences() {
        BlockPos first = new BlockPos(1, 0, 0);
        BlockPos second = new BlockPos(2, 0, 0);
        InputRoutingFacet routing = new InputRoutingFacet(9, List.of(
                new InputRoutingFacet.Source(2, first), new InputRoutingFacet.Source(2, first),
                new InputRoutingFacet.Source(8, first), new InputRoutingFacet.Source(8, second),
                new InputRoutingFacet.Source(11, second)), List.of(
                new InputRoutingFacet.Port(first, true), new InputRoutingFacet.Port(second, false)));
        InputRoutingIndex index = InputRoutingIndex.capture(routing);
        for (InputScope scope : List.of(InputScope.legacy(), routing.ordinaryScope(),
                new InputScope(InputScope.Kind.ISOLATED, List.of(first)),
                new InputScope(InputScope.Kind.ORDINARY, List.of()))) {
            IntPredicate allowed = index.allowed(scope);
            for (int occurrence = 0; occurrence < 14; occurrence++) {
                assertThat(allowed.test(occurrence)).isEqualTo(routing.allows(scope, occurrence));
            }
        }
    }
}
