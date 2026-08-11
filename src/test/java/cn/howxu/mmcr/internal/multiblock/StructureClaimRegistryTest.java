package cn.howxu.mmcr.internal.multiblock;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StructureClaimRegistryTest {

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
}
