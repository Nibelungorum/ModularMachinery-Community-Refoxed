package cn.howxu.mmcr.internal.multiblock;

import cn.howxu.mmcr.api.capability.async.AsyncCapabilityPlanner;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityRequest;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilitySnapshot;
import cn.howxu.mmcr.api.capability.async.AsyncResourceAction;
import cn.howxu.mmcr.api.capability.async.AsyncResourceValue;
import cn.howxu.mmcr.api.capability.facet.InputRoutingFacet;
import cn.howxu.mmcr.api.recipe.InputScope;
import cn.howxu.mmcr.internal.async.MachineAsyncCoordinator;
import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import cn.howxu.mmcr.internal.recipe.AsyncRequirementPlanner;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.Test;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies routing metadata and shared snapshot identity through workset reconstruction.
 * @author howxu <dev@howxu.cn>
 */
class SharedIoInputRoutingTest {
    private static final ResourceLocation ITEM = BuiltinCapabilityDefinitions.ITEM_TYPE.id();
    private static final AsyncResourceValue IRON = new AsyncResourceValue(ResourceLocation.parse("minecraft:iron_ingot"), "{}");

    @Test
    void workset_reserves_only_the_pinned_source_and_retains_shared_storage_identity() {
        var prepared = prepared();
        var results = SharedIoCoordinator.planTickWorksetForTesting(List.of(prepared, prepared));
        assertThat(results.getFirst().mainThreadRequirements()).isEmpty();
        assertThat(results.getFirst().operations()).extracting(AsyncRequirementPlanner.PlannedOperation::capabilityIndex)
                .containsExactly(1);
        assertThat(results.get(1).operations()).isEmpty();
        assertThat(results.get(1).mainThreadRequirements()).containsExactly(7);
    }

    @Test
    void pending_entry_rebase_keeps_selected_scope_and_full_source_index() throws Exception {
        var prepared = prepared();
        Class<?> type = Class.forName(SharedIoCoordinator.class.getName() + "$TickWorkEntry");
        var constructor = type.getDeclaredConstructor(ServerLevel.class, MachineAsyncCoordinator.TaskKey.class,
                AsyncRequirementPlanner.PreparedPlan.class, Predicate.class, Runnable.class);
        constructor.setAccessible(true);
        Object entry = constructor.newInstance(null, null, prepared,
                (Predicate<AsyncRequirementPlanner.PlanResult>) ignored -> true, (Runnable) () -> { });
        IdentityHashMap<AsyncCapabilitySnapshot, AsyncCapabilitySnapshot> replacements = new IdentityHashMap<>();
        AsyncCapabilitySnapshot replacement = snapshot(1);
        replacements.put(prepared.capabilities().get(1).snapshot(), replacement);
        var rebase = type.getDeclaredMethod("rebaseSnapshots", IdentityHashMap.class);
        rebase.setAccessible(true);
        rebase.invoke(entry, replacements);
        var accessor = type.getDeclaredMethod("preparedPlan");
        accessor.setAccessible(true);
        var rebased = (AsyncRequirementPlanner.PreparedPlan) accessor.invoke(entry);
        assertThat(rebased.routing()).isSameAs(prepared.routing());
        assertThat(rebased.inputScope()).isSameAs(prepared.inputScope());
        assertThat(rebased.capabilities().get(1).snapshot()).isSameAs(replacement);
        assertThat(rebased.capabilities().get(1).sourceCapabilityIndex()).isEqualTo(9);
        assertThat(rebased.plan().mainThreadRequirements()).containsExactly(7);
        assertThat(rebased.plan().operations()).isEmpty();
    }

    private static AsyncRequirementPlanner.PreparedPlan prepared() {
        BlockPos first = new BlockPos(1, 0, 0);
        BlockPos second = new BlockPos(2, 0, 0);
        InputRoutingFacet routing = new InputRoutingFacet(3,
                List.of(new InputRoutingFacet.Source(5, first), new InputRoutingFacet.Source(9, second)),
                List.of(new InputRoutingFacet.Port(first, true), new InputRoutingFacet.Port(second, true)));
        var requirement = new AsyncRequirementPlanner.Requirement(7, 2, IOType.INPUT, List.of(
                new AsyncCapabilityRequest.Resource(ITEM, 1, List.of(new AsyncResourceAction(IRON, 2, false)))));
        return new AsyncRequirementPlanner.PreparedPlan(List.of(requirement), List.of(
                new AsyncRequirementPlanner.Capability(new AsyncCapabilityPlanner.Resource(ITEM), snapshot(10),
                        Set.of(IOType.INPUT), 5),
                new AsyncRequirementPlanner.Capability(new AsyncCapabilityPlanner.Resource(ITEM), snapshot(2),
                        Set.of(IOType.INPUT), 9)), List.of(), routing,
                new InputScope(InputScope.Kind.ISOLATED, List.of(second)));
    }

    private static AsyncCapabilitySnapshot snapshot(long amount) {
        return new AsyncCapabilitySnapshot.Resource(ITEM, List.of(
                new AsyncCapabilitySnapshot.ResourceSlot(Optional.of(IRON), amount, 64)));
    }
}
