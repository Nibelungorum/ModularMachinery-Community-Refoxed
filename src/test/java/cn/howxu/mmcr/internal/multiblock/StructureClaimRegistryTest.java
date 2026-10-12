package cn.howxu.mmcr.internal.multiblock;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructureClaimRegistryTest {

    @Test
    void dense_controller_positions_keep_live_domains_through_merge_and_release() {
        StructureClaimRegistry registry = new StructureClaimRegistry();
        Map<BlockPos, StructureClaimRegistry.ResourceDomain> original = new LinkedHashMap<>();
        for (int index = -32; index < 32; index++) {
            // These distinct world positions collide under Vec3i.hashCode().
            BlockPos position = new BlockPos(index * 961, -64, -index);
            assertThat(registry.claim(position, List.of()).accepted()).isTrue();
            original.put(position, registry.domainFor(position));
        }
        var query = new BlockPos.MutableBlockPos();
        original.forEach((position, domain) -> {
            assertThat(registry.domainFor(query.set(position))).isSameAs(domain);
            assertThat(domain.controllers()).containsExactly(position);
        });
        BlockPos first = new BlockPos(-961, -64, 1);
        BlockPos second = new BlockPos(961, -64, -1);
        var claim = new StructureClaimRegistry.Claim(BlockPos.ZERO, ComponentClaimPolicy.SHARED_SERIALIZED);
        registry.claim(first, List.of(claim));
        registry.claim(second, List.of(claim));
        var merged = registry.domainFor(first);
        assertThat(registry.domainFor(query.set(second))).isSameAs(merged);
        assertThat(merged.controllers()).containsExactlyInAnyOrder(first, second);
        registry.release(second);
        assertThat(registry.domainFor(query.set(second))).isNull();
        assertThat(registry.domainFor(query.set(first)).generation()).isGreaterThan(merged.generation());
        original.forEach((position, domain) -> {
            if (!position.equals(first) && !position.equals(second)) {
                assertThat(registry.domainFor(query.set(position))).isSameAs(domain);
            }
        });
        assertThat(merged.controllers()).containsExactlyInAnyOrder(first, second);
    }

    @Test
    void exclusiveConflictDoesNotLeavePartialClaims() {
        BlockPos first = new BlockPos(0, 64, 0);
        BlockPos second = new BlockPos(4, 64, 0);
        BlockPos freePort = new BlockPos(1, 64, 0);
        BlockPos occupiedScheduler = new BlockPos(2, 64, 0);
        StructureClaimRegistry registry = new StructureClaimRegistry();

        assertThat(registry.claim(first, List.of(
                new StructureClaimRegistry.Claim(freePort, ComponentClaimPolicy.SHARED_SERIALIZED),
                new StructureClaimRegistry.Claim(occupiedScheduler, ComponentClaimPolicy.EXCLUSIVE))))
                .isEqualTo(StructureClaimRegistry.ClaimResult.success());

        StructureClaimRegistry.ClaimResult result = registry.claim(second, List.of(
                new StructureClaimRegistry.Claim(freePort, ComponentClaimPolicy.SHARED_SERIALIZED),
                new StructureClaimRegistry.Claim(occupiedScheduler, ComponentClaimPolicy.EXCLUSIVE)));

        assertThat(result.accepted()).isFalse();
        assertThat(result.conflict().componentPos()).isEqualTo(occupiedScheduler);
        assertThat(registry.ownersOf(freePort)).containsExactly(first);
        assertThat(registry.ownersOf(occupiedScheduler)).containsExactly(first);
    }

    @Test
    void claimed_controller_snapshots_are_reused_and_remain_immutable_across_claim_changes() {
        StructureClaimRegistry registry = new StructureClaimRegistry();
        BlockPos first = new BlockPos(0, 64, 0);
        BlockPos second = new BlockPos(10, 64, 0);
        BlockPos third = new BlockPos(20, 64, 0);
        BlockPos port = new BlockPos(1, 64, 0);
        var claim = new StructureClaimRegistry.Claim(port, ComponentClaimPolicy.EXCLUSIVE);
        var empty = registry.claimedControllers();
        assertThat(registry.claim(first, List.of(claim)).accepted()).isTrue();
        var firstSnapshot = registry.claimedControllers();
        assertThat(firstSnapshot).containsExactly(first);
        assertThat(registry.claimedControllers()).isSameAs(firstSnapshot);
        assertThatThrownBy(firstSnapshot::clear).isInstanceOf(UnsupportedOperationException.class);

        assertThat(registry.claim(second, List.of(claim)).accepted()).isFalse();
        registry.release(third);
        assertThat(registry.claimedControllers()).isSameAs(firstSnapshot);
        assertThat(registry.claim(second, List.of()).accepted()).isTrue();
        var secondSnapshot = registry.claimedControllers();
        assertThat(secondSnapshot).containsExactlyInAnyOrder(first, second);
        assertThat(firstSnapshot).containsExactly(first);
        assertThat(empty).isEmpty();

        assertThat(registry.claim(first, List.of()).accepted()).isTrue();
        assertThat(registry.claimedControllers()).containsExactlyInAnyOrder(first, second);
        assertThat(registry.ownersOf(port)).isEmpty();
        registry.release(first);
        assertThat(registry.claimedControllers()).containsExactly(second);
        assertThat(secondSnapshot).containsExactlyInAnyOrder(first, second);
        registry.release(second);
        assertThat(registry.claimedControllers()).isEmpty();
    }

    @Test
    void existingExclusiveConflictWithSharedRequestDoesNotLeavePartialClaims() {
        BlockPos first = new BlockPos(0, 64, 0);
        BlockPos second = new BlockPos(4, 64, 0);
        BlockPos freePort = new BlockPos(1, 64, 0);
        BlockPos occupiedScheduler = new BlockPos(2, 64, 0);
        StructureClaimRegistry registry = new StructureClaimRegistry();

        assertThat(registry.claim(first, List.of(
                new StructureClaimRegistry.Claim(occupiedScheduler, ComponentClaimPolicy.EXCLUSIVE))))
                .isEqualTo(StructureClaimRegistry.ClaimResult.success());

        StructureClaimRegistry.ClaimResult result = registry.claim(second, List.of(
                new StructureClaimRegistry.Claim(freePort, ComponentClaimPolicy.SHARED_SERIALIZED),
                new StructureClaimRegistry.Claim(occupiedScheduler, ComponentClaimPolicy.SHARED_SERIALIZED)));

        assertThat(result.accepted()).isFalse();
        assertThat(result.conflict().componentPos()).isEqualTo(occupiedScheduler);
        assertThat(registry.ownersOf(freePort)).isEmpty();
        assertThat(registry.ownersOf(occupiedScheduler)).containsExactly(first);
    }

    @Test
    void sharedClaimsMergeTransitiveControllersIntoOneDomainAndReleaseSplitsIt() {
        BlockPos a = new BlockPos(0, 64, 0);
        BlockPos b = new BlockPos(10, 64, 0);
        BlockPos c = new BlockPos(20, 64, 0);
        BlockPos p = new BlockPos(5, 64, 0);
        BlockPos q = new BlockPos(15, 64, 0);
        StructureClaimRegistry registry = new StructureClaimRegistry();

        registry.claim(a, List.of(new StructureClaimRegistry.Claim(p, ComponentClaimPolicy.SHARED_SERIALIZED)));
        registry.claim(b, List.of(
                new StructureClaimRegistry.Claim(p, ComponentClaimPolicy.SHARED_SERIALIZED),
                new StructureClaimRegistry.Claim(q, ComponentClaimPolicy.SHARED_SERIALIZED)));
        registry.claim(c, List.of(new StructureClaimRegistry.Claim(q, ComponentClaimPolicy.SHARED_SERIALIZED)));

        assertThat(registry.domainFor(a)).isEqualTo(registry.domainFor(b));
        assertThat(registry.domainFor(b)).isEqualTo(registry.domainFor(c));
        long beforeRelease = registry.generationFor(a);

        registry.release(b);

        assertThat(registry.domainFor(a)).isNotEqualTo(registry.domainFor(c));
        assertThat(registry.generationFor(a)).isGreaterThan(beforeRelease);
    }

    @Test
    void unrelatedClaimsDoNotInvalidateAnExistingResourceDomain() {
        BlockPos first = new BlockPos(0, 64, 0);
        BlockPos second = new BlockPos(10, 64, 0);
        StructureClaimRegistry registry = new StructureClaimRegistry();

        registry.claim(first, List.of(new StructureClaimRegistry.Claim(new BlockPos(1, 64, 0), ComponentClaimPolicy.SHARED_SERIALIZED)));
        StructureClaimRegistry.ResourceDomain original = registry.domainFor(first);

        registry.claim(second, List.of(new StructureClaimRegistry.Claim(new BlockPos(11, 64, 0), ComponentClaimPolicy.SHARED_SERIALIZED)));

        assertThat(registry.domainFor(first)).isEqualTo(original);
    }

    @Test
    void shareableCapacityClaimsReleaseWithoutLeavingStaleOwners() {
        BlockPos first = new BlockPos(0, 64, 0);
        BlockPos second = new BlockPos(10, 64, 0);
        BlockPos capacity = new BlockPos(1, 64, 0);
        StructureClaimRegistry registry = new StructureClaimRegistry();
        StructureClaimRegistry.Claim claim = new StructureClaimRegistry.Claim(
                capacity, ComponentClaimPolicy.SHARED_CAPACITY);

        assertThat(registry.claim(first, List.of(claim)).accepted()).isTrue();
        assertThat(registry.claim(second, List.of(claim)).accepted()).isTrue();
        assertThat(registry.ownersOf(capacity)).containsExactlyInAnyOrder(first, second);

        registry.release(first);
        assertThat(registry.ownersOf(capacity)).containsExactly(second);

        registry.release(second);
        assertThat(registry.ownersOf(capacity)).isEmpty();
        assertThat(registry.claimedControllers()).isEmpty();
    }

    @Test
    void exclusive_conflicts_with_shared_capacity_in_both_directions() {
        BlockPos first = new BlockPos(0, 64, 0);
        BlockPos second = new BlockPos(10, 64, 0);
        BlockPos component = new BlockPos(1, 64, 0);
        StructureClaimRegistry registry = new StructureClaimRegistry();

        registry.claim(first, List.of(new StructureClaimRegistry.Claim(component, ComponentClaimPolicy.EXCLUSIVE)));
        assertThat(registry.claim(second, List.of(new StructureClaimRegistry.Claim(component,
                ComponentClaimPolicy.SHARED_CAPACITY))).accepted()).isFalse();

        registry.release(first);
        registry.claim(first, List.of(new StructureClaimRegistry.Claim(component, ComponentClaimPolicy.SHARED_CAPACITY)));
        assertThat(registry.claim(second, List.of(new StructureClaimRegistry.Claim(component,
                ComponentClaimPolicy.EXCLUSIVE))).accepted()).isFalse();
    }

    @Test
    void exclusive_still_conflicts_with_exclusive() {
        BlockPos first = new BlockPos(0, 64, 0);
        BlockPos second = new BlockPos(10, 64, 0);
        BlockPos component = new BlockPos(1, 64, 0);
        StructureClaimRegistry registry = new StructureClaimRegistry();

        registry.claim(first, List.of(new StructureClaimRegistry.Claim(component, ComponentClaimPolicy.EXCLUSIVE)));

        assertThat(registry.claim(second, List.of(new StructureClaimRegistry.Claim(component,
                ComponentClaimPolicy.EXCLUSIVE))).accepted()).isFalse();
    }

    @Test
    void duplicate_claims_report_conflicts_using_first_seen_order() {
        BlockPos first = new BlockPos(0, 64, 0);
        BlockPos second = new BlockPos(10, 64, 0);
        BlockPos firstComponent = new BlockPos(1, 64, 0);
        BlockPos secondComponent = new BlockPos(2, 64, 0);
        StructureClaimRegistry registry = new StructureClaimRegistry();

        registry.claim(first, List.of(
                new StructureClaimRegistry.Claim(firstComponent, ComponentClaimPolicy.EXCLUSIVE),
                new StructureClaimRegistry.Claim(secondComponent, ComponentClaimPolicy.EXCLUSIVE)));

        StructureClaimRegistry.ClaimResult result = registry.claim(second, List.of(
                new StructureClaimRegistry.Claim(secondComponent, ComponentClaimPolicy.EXCLUSIVE),
                new StructureClaimRegistry.Claim(firstComponent, ComponentClaimPolicy.EXCLUSIVE),
                new StructureClaimRegistry.Claim(secondComponent, ComponentClaimPolicy.EXCLUSIVE)));

        assertThat(result.conflict().componentPos()).isEqualTo(secondComponent);
    }
}
