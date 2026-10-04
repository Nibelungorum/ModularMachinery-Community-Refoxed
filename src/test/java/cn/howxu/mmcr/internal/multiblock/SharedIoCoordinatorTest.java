package cn.howxu.mmcr.internal.multiblock;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityPlanner;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityRequest;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilitySnapshot;
import cn.howxu.mmcr.internal.async.MachineAsyncCoordinator;
import cn.howxu.mmcr.internal.async.AsyncContinuation;
import cn.howxu.mmcr.internal.recipe.AsyncRequirementPlanner;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import java.lang.reflect.Field;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.AbstractSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class SharedIoCoordinatorTest {
    private static final BlockPos A = new BlockPos(0, 64, 0);
    private static final BlockPos B = new BlockPos(4, 64, 0);
    private static final BlockPos C = new BlockPos(8, 64, 0);

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void request_budget_round_robins_between_domains() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator(2, 8);
        StructureClaimRegistry.ResourceDomain first = domain(1L, A);
        StructureClaimRegistry.ResourceDomain second = domain(2L, B);
        List<String> committed = new ArrayList<>();
        coordinator.enqueue(tick(first, A, 1L, () -> { committed.add("A1"); return true; }, () -> true, () -> 1L));
        coordinator.enqueue(tick(first, A, 1L, () -> { committed.add("A2"); return true; }, () -> true, () -> 1L));
        coordinator.enqueue(tick(second, B, 1L, () -> { committed.add("B1"); return true; }, () -> true, () -> 1L));

        coordinator.beginLevelTick(10L);
        coordinator.resolveKnownDomainsForTesting(Map.of(first.id(), first, second.id(), second));

        assertThat(committed).containsExactly("A1", "B1");
    }

    @Test
    void domain_rotation_skips_empty_domains_and_rotates_shared_lanes() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator(1, 8);
        StructureClaimRegistry.ResourceDomain first = domain(1L, A);
        StructureClaimRegistry.ResourceDomain shared = domain(3L, B, C);
        StructureClaimRegistry.ResourceDomain last = domain(5L, A);
        Map<Long, StructureClaimRegistry.ResourceDomain> known = Map.of(1L, first, 3L, shared, 5L, last);
        List<String> committed = new ArrayList<>();
        coordinator.enqueue(tick(first, A, 1L, () -> { committed.add("first"); return true; }, () -> true, () -> 1L));
        coordinator.enqueue(tick(shared, B, 1L, () -> { committed.add("shared-B"); return true; }, () -> true, () -> 1L));
        coordinator.enqueue(tick(shared, C, 1L, () -> { committed.add("shared-C"); return true; }, () -> true, () -> 1L));
        coordinator.enqueue(tick(last, A, 1L, () -> { committed.add("last"); return true; }, () -> true, () -> 1L));

        for (long gameTime = 1L; gameTime <= 4L; gameTime++) {
            coordinator.beginLevelTick(gameTime);
            assertThat(coordinator.resolveKnownDomainsForTesting(known)).isEqualTo(1);
        }

        assertThat(committed).containsExactly("first", "shared-B", "last", "shared-C");
        assertThat(coordinator.pendingRequestCountForTesting()).isZero();
    }

    @Test
    void selection_uses_live_topology_but_defers_callback_created_domains() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator(8, 8);
        StructureClaimRegistry.ResourceDomain first = domain(1L, A);
        StructureClaimRegistry.ResourceDomain removed = domain(2L, B);
        StructureClaimRegistry.ResourceDomain third = domain(3L, C);
        StructureClaimRegistry.ResourceDomain added = domain(4L, B);
        Map<Long, StructureClaimRegistry.ResourceDomain> known = Map.of(1L, first, 2L, removed, 3L, third, 4L, added);
        List<String> committed = new ArrayList<>();
        coordinator.enqueue(tick(first, A, 1L, () -> {
            committed.add("first");
            coordinator.cancel(B);
            coordinator.enqueue(tick(added, B, 1L, () -> { committed.add("added"); return true; }, () -> true, () -> 1L));
            coordinator.enqueue(finish(first, A, 1L, () -> { committed.add("successor"); return true; }, () -> true, () -> 1L));
            return true;
        }, () -> true, () -> 1L));
        coordinator.enqueue(tick(removed, B, 1L, () -> { committed.add("removed"); return true; }, () -> true, () -> 1L));
        coordinator.enqueue(tick(third, C, 1L, () -> { committed.add("third"); return true; }, () -> true, () -> 1L));

        assertThat(coordinator.resolveKnownDomainsForTesting(known)).isEqualTo(3);
        assertThat(committed).containsExactly("first", "third", "successor");
        assertThat(coordinator.pendingRequestCountForTesting()).isEqualTo(1);
        assertThat(coordinator.resolveKnownDomainsForTesting(known)).isEqualTo(1);
        assertThat(committed).containsExactly("first", "third", "successor", "added");
    }

    @Test
    void reentrant_resolution_updates_cursor_and_shares_the_remaining_budget() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator(3, 8);
        StructureClaimRegistry.ResourceDomain first = domain(1L, A);
        StructureClaimRegistry.ResourceDomain second = domain(2L, B);
        StructureClaimRegistry.ResourceDomain third = domain(3L, C);
        Map<Long, StructureClaimRegistry.ResourceDomain> known = Map.of(1L, first, 2L, second, 3L, third);
        List<String> committed = new ArrayList<>();
        coordinator.enqueue(tick(first, A, 1L, () -> {
            committed.add("first");
            assertThat(coordinator.resolve(second)).isEqualTo(1);
            return true;
        }, () -> true, () -> 1L));
        coordinator.enqueue(tick(first, A, 1L, () -> { committed.add("first-again"); return true; }, () -> true, () -> 1L));
        coordinator.enqueue(tick(second, B, 1L, () -> { committed.add("second"); return true; }, () -> true, () -> 1L));
        coordinator.enqueue(tick(third, C, 1L, () -> { committed.add("third"); return true; }, () -> true, () -> 1L));

        assertThat(coordinator.resolveKnownDomainsForTesting(known)).isEqualTo(2);
        assertThat(committed).containsExactly("first", "second", "third");
        assertThat(coordinator.resolveKnownDomainsForTesting(known)).isZero();
        coordinator.beginLevelTick(1L);
        assertThat(coordinator.resolveKnownDomainsForTesting(known)).isEqualTo(1);
        assertThat(committed).containsExactly("first", "second", "third", "first-again");
    }

    @Test
    void deleted_cursor_wraps_and_failed_requests_retry_only_on_the_next_resolution() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator(8, 8);
        StructureClaimRegistry.ResourceDomain first = domain(1L, A);
        StructureClaimRegistry.ResourceDomain last = domain(3L, C);
        Map<Long, StructureClaimRegistry.ResourceDomain> known = Map.of(1L, first, 3L, last);
        List<String> attempts = new ArrayList<>();
        AtomicBoolean succeeds = new AtomicBoolean();
        coordinator.enqueue(tick(last, C, 1L, () -> { attempts.add("last"); return false; }, () -> true, () -> 1L));
        assertThat(coordinator.resolveKnownDomainsForTesting(known)).isZero();
        coordinator.cancel(C);
        coordinator.enqueue(tick(first, A, 1L, () -> { attempts.add("first"); return succeeds.get(); }, () -> true, () -> 1L));
        coordinator.beginLevelTick(1L);
        assertThat(coordinator.resolveKnownDomainsForTesting(known)).isZero();
        assertThat(attempts).containsExactly("last", "first");
        succeeds.set(true);
        assertThat(coordinator.resolveKnownDomainsForTesting(known)).isEqualTo(1);
        assertThat(attempts).containsExactly("last", "first", "first");
    }

    @Test
    void generation_and_unknown_domain_invalidation_preserve_cursor_order() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator(8, 8);
        StructureClaimRegistry.ResourceDomain old = domain(3L, C);
        StructureClaimRegistry.ResourceDomain replacement = new StructureClaimRegistry.ResourceDomain(3L, 2L, Set.of(C));
        StructureClaimRegistry.ResourceDomain unknown = domain(1L, A);
        AtomicInteger discards = new AtomicInteger();
        List<String> committed = new ArrayList<>();
        coordinator.enqueue(tick(old, C, 1L, () -> false,
                () -> { discards.incrementAndGet(); return true; }, () -> 1L));
        assertThat(coordinator.resolve(old)).isZero();
        discards.set(0);
        coordinator.enqueue(tick(unknown, A, 1L, () -> { committed.add("unknown"); return true; },
                () -> { discards.incrementAndGet(); return true; }, () -> 1L));
        coordinator.enqueue(tick(replacement, C, 1L, () -> { committed.add("replacement"); return true; },
                () -> true, () -> 1L));

        assertThat(coordinator.resolveKnownDomainsForTesting(Map.of(3L, replacement))).isEqualTo(1);
        assertThat(committed).containsExactly("replacement");
        assertThat(discards).hasValue(2);
        assertThat(coordinator.pendingRequestCountForTesting()).isZero();
        assertThat(coordinator.indexedControllerCountForTesting()).isZero();
    }

    @Test
    void resolving_many_requests_does_not_reenumerate_domain_keys_per_request() throws Exception {
        SharedIoCoordinator coordinator = new SharedIoCoordinator(512, 8);
        DomainTraversalMap<Object, Object> domains = new DomainTraversalMap<>();
        Field field = SharedIoCoordinator.class.getDeclaredField("domains");
        field.setAccessible(true);
        field.set(coordinator, domains);
        Map<Long, StructureClaimRegistry.ResourceDomain> known = new HashMap<>();
        AtomicInteger commits = new AtomicInteger();
        for (int index = 0; index < 64; index++) {
            BlockPos owner = new BlockPos(index, 64, 0);
            StructureClaimRegistry.ResourceDomain domain = domain(index + 1L, owner);
            known.put(domain.id(), domain);
            for (int request = 0; request < 8; request++) {
                coordinator.enqueue(tick(domain, owner, 1L,
                        () -> { commits.incrementAndGet(); return true; }, () -> true, () -> 1L));
            }
        }

        assertThat(coordinator.resolveKnownDomainsForTesting(known)).isEqualTo(512);
        assertThat(commits).hasValue(512);
        // Stale-domain filtering may enumerate the keys once, but request selection must not
        // repeat a full topology traversal/materialization for each of the 512 commits.
        assertThat(domains.keyVisits).isLessThanOrEqualTo(64);
        assertThat(coordinator.pendingRequestCountForTesting()).isZero();
    }

    /**
     * Counts full domain-key enumeration independently of request validation/commit work.
     *
     * @author howxu <dev@howxu.cn>
     */
    private static final class DomainTraversalMap<K, V> extends TreeMap<K, V> {
        private int keyVisits;

        @Override
        public Set<K> keySet() {
            Set<K> keys = super.keySet();
            return new AbstractSet<>() {
                @Override
                public Iterator<K> iterator() {
                    Iterator<K> iterator = keys.iterator();
                    return new Iterator<>() {
                        @Override
                        public boolean hasNext() {
                            return iterator.hasNext();
                        }

                        @Override
                        public K next() {
                            keyVisits++;
                            return iterator.next();
                        }
                    };
                }

                @Override
                public int size() {
                    return keys.size();
                }
            };
        }
    }

    @Test
    void resolving_one_domain_preserves_requests_for_other_domains() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator(8, 8);
        StructureClaimRegistry.ResourceDomain first = domain(1L, A);
        StructureClaimRegistry.ResourceDomain second = domain(2L, B);
        List<String> committed = new ArrayList<>();
        coordinator.enqueue(tick(first, A, 1L, () -> { committed.add("first"); return true; }, () -> true, () -> 1L));
        coordinator.enqueue(tick(second, B, 1L, () -> { committed.add("second"); return true; }, () -> true, () -> 1L));

        coordinator.resolve(first);
        assertThat(committed).containsExactly("first");
        assertThat(coordinator.pendingRequestCountForTesting()).isEqualTo(1);

        coordinator.resolve(second);
        assertThat(committed).containsExactly("first", "second");
    }

    @Test
    void request_budget_is_shared_within_one_game_time_and_resets_on_the_next() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator(1, 8);
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        List<String> committed = new ArrayList<>();
        coordinator.enqueue(tick(domain, A, 1L, () -> { committed.add("first"); return true; }, () -> true, () -> 1L));
        coordinator.enqueue(tick(domain, B, 1L, () -> { committed.add("second"); return true; }, () -> true, () -> 1L));

        coordinator.beginLevelTick(10L);
        coordinator.resolveKnownDomainsForTesting(Map.of(domain.id(), domain));
        coordinator.beginLevelTick(10L);
        coordinator.resolveKnownDomainsForTesting(Map.of(domain.id(), domain));
        assertThat(committed).containsExactly("first");

        coordinator.beginLevelTick(11L);
        coordinator.resolveKnownDomainsForTesting(Map.of(domain.id(), domain));
        assertThat(committed).containsExactly("first", "second");
    }

    @Test
    void small_budget_rotates_between_start_tick_and_finish_requests() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator(1, 8);
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        List<String> committed = new ArrayList<>();
        coordinator.enqueue(start(domain, A, 1L, 1L, ignored -> 1L,
                ignored -> committed.add("start"), () -> true, () -> 1L));
        coordinator.enqueue(tick(domain, A, 1L,
                () -> { committed.add("tick"); return true; }, () -> true, () -> 1L));
        coordinator.enqueue(finish(domain, A, 1L,
                () -> { committed.add("finish"); return true; }, () -> true, () -> 1L));

        for (long gameTime = 1L; gameTime <= 3L; gameTime++) {
            coordinator.beginLevelTick(gameTime);
            coordinator.resolveKnownDomainsForTesting(Map.of(domain.id(), domain));
        }

        assertThat(committed).containsExactly("start", "tick", "finish");
    }

    @Test
    void generation_change_discards_the_old_bucket_and_owner_index() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator(8, 8);
        StructureClaimRegistry.ResourceDomain oldDomain = new StructureClaimRegistry.ResourceDomain(1L, 1L, Set.of(A));
        StructureClaimRegistry.ResourceDomain newDomain = new StructureClaimRegistry.ResourceDomain(1L, 2L, Set.of(A));
        AtomicInteger validations = new AtomicInteger();
        coordinator.enqueue(tick(oldDomain, A, 1L, () -> true,
                () -> { validations.incrementAndGet(); return false; }, () -> 1L));

        coordinator.beginLevelTick(1L);
        coordinator.resolveKnownDomainsForTesting(Map.of(newDomain.id(), newDomain));

        assertThat(validations).hasValue(1);
        assertThat(coordinator.pendingRequestCountForTesting()).isZero();
        assertThat(coordinator.indexedControllerCountForTesting()).isZero();
    }

    @Test
    void cancelling_a_controller_only_removes_its_indexed_requests() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator(8, 8);
        StructureClaimRegistry.ResourceDomain domain = domain(A, B);
        List<String> committed = new ArrayList<>();
        coordinator.enqueue(tick(domain, A, 1L, () -> { committed.add("A"); return true; }, () -> true, () -> 1L));
        coordinator.enqueue(tick(domain, B, 1L, () -> { committed.add("B"); return true; }, () -> true, () -> 1L));

        coordinator.cancel(A);
        coordinator.beginLevelTick(1L);
        coordinator.resolveKnownDomainsForTesting(Map.of(domain.id(), domain));

        assertThat(committed).containsExactly("B");
        assertThat(coordinator.pendingRequestCountForTesting()).isZero();
    }

    @Test
    void domain_tick_workset_reserves_a_shared_snapshot_in_lane_order() {
        var capabilityId = MMCR.id("energy");
        AsyncCapabilitySnapshot snapshot = new AsyncCapabilitySnapshot.Scalar(capabilityId, 10L, 10L, 10L);
        AsyncRequirementPlanner.Capability capability = new AsyncRequirementPlanner.Capability(
                new AsyncCapabilityPlanner.Scalar(capabilityId), snapshot, Set.of(IOType.INPUT));
        AsyncRequirementPlanner.Requirement requirement = new AsyncRequirementPlanner.Requirement(0, 6L,
                IOType.INPUT, List.of(new AsyncCapabilityRequest.Scalar(capabilityId, 1L, 6L, false)));
        AsyncRequirementPlanner.PreparedPlan plan = new AsyncRequirementPlanner.PreparedPlan(
                List.of(requirement), List.of(capability), List.of());

        List<AsyncRequirementPlanner.PlanResult> results = SharedIoCoordinator.planTickWorksetForTesting(
                List.of(plan, plan));

        assertThat(results.get(0).operations()).hasSize(1);
        assertThat(results.get(0).mainThreadRequirements()).isEmpty();
        assertThat(results.get(1).operations()).isEmpty();
        assertThat(results.get(1).mainThreadRequirements()).containsExactly(0);
    }

    @Test
    void cancelling_a_controller_discards_its_pending_domain_tick_work() throws Exception {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A, B);
        ServerLevel level = allocate(ServerLevel.class);
        AtomicInteger discarded = new AtomicInteger();
        AsyncRequirementPlanner.PreparedPlan plan = new AsyncRequirementPlanner.PreparedPlan(
                List.of(), List.of(), List.of());

        coordinator.enqueueTickWork(level, domain,
                new MachineAsyncCoordinator.TaskKey(A, 1L), plan, ignored -> { }, discarded::incrementAndGet);
        coordinator.enqueueTickWork(level, domain,
                new MachineAsyncCoordinator.TaskKey(B, 1L), plan, ignored -> { }, () -> { });

        coordinator.cancel(A);

        assertThat(discarded).hasValue(1);
    }

    @Test
    void failed_domain_tick_workset_discards_every_entry_and_releases_the_submission() {
        SharedIoCoordinator sharedIo = new SharedIoCoordinator();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(Runnable::run);
        AtomicInteger discarded = new AtomicInteger();

        sharedIo.submitTickWorksetForTesting(coordinator, ignored -> {
            throw new IllegalStateException("planned failure");
        }, 2, discarded::incrementAndGet);
        coordinator.completeTick();

        assertThat(discarded).hasValue(2);
        assertThat(sharedIo.submittedTickWorkCountForTesting()).isZero();
    }

    @Test
    void resolving_while_a_domain_workset_is_in_flight_keeps_that_workset_current() throws Exception {
        SharedIoCoordinator sharedIo = new SharedIoCoordinator();
        ArrayDeque<Runnable> workers = new ArrayDeque<>();
        MachineAsyncCoordinator async = MachineAsyncCoordinator.forTesting(workers::add);
        ServerLevel level = allocate(ServerLevel.class);
        StructureClaimRegistry registry = StructureClaimRegistry.get(level);
        assertThat(registry.claim(A, List.of()).accepted()).isTrue();
        StructureClaimRegistry.ResourceDomain domain = registry.domainFor(A);
        AtomicInteger committed = new AtomicInteger();
        AtomicInteger discarded = new AtomicInteger();

        sharedIo.enqueueTickWorkForTesting(domain, new MachineAsyncCoordinator.TaskKey(A, 1L),
                ignored -> committed.incrementAndGet(), discarded::incrementAndGet);
        sharedIo.submitPendingTickWorksetForTesting(domain, async);
        sharedIo.resolve(level);

        workers.removeFirst().run();
        async.completeTick();

        assertThat(committed).hasValue(1);
        assertThat(discarded).hasValue(0);
    }

    @Test
    void failed_entry_commit_is_discarded_without_blocking_later_entries() {
        SharedIoCoordinator sharedIo = new SharedIoCoordinator();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(Runnable::run);
        AtomicInteger firstDiscarded = new AtomicInteger();
        AtomicInteger secondCommitted = new AtomicInteger();

        sharedIo.submitTickWorksetForTesting(coordinator, List.of(
                ignored -> { throw new IllegalStateException("commit failure"); },
                ignored -> secondCommitted.incrementAndGet()),
                List.of(firstDiscarded::incrementAndGet, () -> { }));
        coordinator.completeUntilIdleForTesting(() -> 0);

        assertThat(firstDiscarded).hasValue(1);
        assertThat(secondCommitted).hasValue(1);
        assertThat(sharedIo.submittedTickWorkCountForTesting()).isZero();
    }

    @Test
    void domain_tick_worksets_are_batched_and_serialized() {
        SharedIoCoordinator sharedIo = new SharedIoCoordinator(8, 2);
        MachineAsyncCoordinator async = MachineAsyncCoordinator.forTesting(Runnable::run);
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        var key = new MachineAsyncCoordinator.TaskKey(A, 1L);
        for (int index = 0; index < 5; index++) {
            sharedIo.enqueueTickWorkForTesting(domain, key, ignored -> { }, () -> { });
        }

        sharedIo.submitPendingTickWorksetForTesting(domain, async);
        assertThat(sharedIo.submittedTickWorkEntryCountsForTesting()).containsExactly(2);
        assertThat(sharedIo.pendingTickWorkCountForTesting()).isEqualTo(3);
        sharedIo.submitPendingTickWorksetForTesting(domain, async);
        assertThat(sharedIo.submittedTickWorkEntryCountsForTesting()).containsExactly(2);

        async.completeUntilIdleForTesting(() -> 0);
        sharedIo.submitPendingTickWorksetForTesting(domain, async);
        assertThat(sharedIo.submittedTickWorkEntryCountsForTesting()).containsExactly(2);
        async.completeUntilIdleForTesting(() -> 0);
        sharedIo.submitPendingTickWorksetForTesting(domain, async);
        assertThat(sharedIo.submittedTickWorkEntryCountsForTesting()).containsExactly(1);
        async.completeUntilIdleForTesting(() -> 0);

        assertThat(sharedIo.pendingTickWorkCountForTesting()).isZero();
        assertThat(sharedIo.submittedTickWorkCountForTesting()).isZero();
    }

    @Test
    void failed_workset_releases_the_domain_for_the_next_batch() {
        SharedIoCoordinator sharedIo = new SharedIoCoordinator(8, 2);
        MachineAsyncCoordinator async = MachineAsyncCoordinator.forTesting(Runnable::run);
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        var key = new MachineAsyncCoordinator.TaskKey(A, 1L);
        AtomicInteger discarded = new AtomicInteger();
        AtomicInteger committed = new AtomicInteger();
        sharedIo.enqueueTickWorkForTesting(domain, key,
                ignored -> { throw new IllegalStateException("commit failure"); }, discarded::incrementAndGet);
        sharedIo.enqueueTickWorkForTesting(domain, key, ignored -> committed.incrementAndGet(), () -> { });
        sharedIo.enqueueTickWorkForTesting(domain, key, ignored -> committed.incrementAndGet(), () -> { });

        sharedIo.submitPendingTickWorksetForTesting(domain, async);
        async.completeUntilIdleForTesting(() -> 0);
        sharedIo.submitPendingTickWorksetForTesting(domain, async);
        async.completeUntilIdleForTesting(() -> 0);

        assertThat(discarded).hasValue(1);
        assertThat(committed).hasValue(2);
        assertThat(sharedIo.pendingTickWorkCountForTesting()).isZero();
    }

    @Test
    void later_batch_plans_from_the_snapshot_committed_by_the_previous_batch() {
        var capabilityId = MMCR.id("energy");
        AsyncCapabilitySnapshot snapshot = new AsyncCapabilitySnapshot.Scalar(capabilityId, 10L, 10L, 10L);
        AsyncRequirementPlanner.Capability capability = new AsyncRequirementPlanner.Capability(
                new AsyncCapabilityPlanner.Scalar(capabilityId), snapshot, Set.of(IOType.INPUT));
        AsyncRequirementPlanner.Requirement requirement = new AsyncRequirementPlanner.Requirement(0, 6L,
                IOType.INPUT, List.of(new AsyncCapabilityRequest.Scalar(capabilityId, 1L, 6L, false)));
        AsyncRequirementPlanner.PreparedPlan plan = new AsyncRequirementPlanner.PreparedPlan(
                List.of(requirement), List.of(capability), List.of());
        SharedIoCoordinator sharedIo = new SharedIoCoordinator(8, 1);
        MachineAsyncCoordinator async = MachineAsyncCoordinator.forTesting(Runnable::run);
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        var key = new MachineAsyncCoordinator.TaskKey(A, 1L);
        List<AsyncRequirementPlanner.PlanResult> committed = new ArrayList<>();
        sharedIo.enqueueTickWorkForTesting(domain, key, plan, committed::add, () -> { });
        sharedIo.enqueueTickWorkForTesting(domain, key, plan, committed::add, () -> { });

        sharedIo.submitPendingTickWorksetForTesting(domain, async);
        async.completeUntilIdleForTesting(() -> 0);
        sharedIo.submitPendingTickWorksetForTesting(domain, async);
        async.completeUntilIdleForTesting(() -> 0);

        assertThat(committed.get(0).operations()).hasSize(1);
        assertThat(committed.get(1).operations()).isEmpty();
        assertThat(committed.get(1).mainThreadRequirements()).containsExactly(0);
    }

    @Test
    void startRequestsUseRotatingOrderAndMayReceivePartialParallelism() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A, B);
        List<String> committed = new ArrayList<>();

        coordinator.enqueue(start(domain, A, 17L, 8,
                maximum -> Math.min(maximum, 8), granted -> committed.add("A:" + granted), () -> true, () -> 17L));
        coordinator.enqueue(start(domain, B, 21L, 8,
                maximum -> Math.min(maximum, 2), granted -> committed.add("B:" + granted), () -> true, () -> 21L));

        coordinator.resolve(domain);

        assertThat(committed).containsExactly("A:8", "B:2");
        assertThat(coordinator.nextStartLane(domain.id()))
                .isEqualTo(new SharedIoCoordinator.LaneKey(B, "base"));
    }

    @Test
    void invalid_request_runs_its_validator_once_per_resolution() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        AtomicInteger validations = new AtomicInteger();

        coordinator.enqueue(tick(domain, A, 1L, () -> true,
                () -> {
                    validations.incrementAndGet();
                    return false;
                }, () -> 1L));

        coordinator.resolve(domain);

        assertThat(validations).hasValue(1);
    }

    @Test
    void finish_spawned_start_runs_its_validator_once_per_resolution() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        AtomicInteger validations = new AtomicInteger();
        coordinator.enqueue(finish(domain, A, 1L, () -> {
            coordinator.enqueue(start(domain, A, 1L, 1L, ignored -> 1L, ignored -> { },
                    () -> {
                        validations.incrementAndGet();
                        return true;
                    }, () -> 1L));
            return true;
        }, () -> true, () -> 1L));

        coordinator.resolve(domain);

        assertThat(validations).hasValue(1);
    }

    @Test
    void round_robin_moves_past_the_last_base_lane_of_the_same_controller() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        List<String> committed = new ArrayList<>();

        coordinator.enqueue(new SharedIoCoordinator.StartRequest(domain,
                new SharedIoCoordinator.LaneKey(A, "base"), 1L, 0L, 1,
                ignored -> 1, ignored -> committed.add("base"), () -> true, () -> 1L, () -> 0L));
        coordinator.resolve(domain);
        committed.clear();
        coordinator.enqueue(new SharedIoCoordinator.StartRequest(domain,
                new SharedIoCoordinator.LaneKey(A, "base"), 1L, 0L, 1,
                ignored -> 1, ignored -> committed.add("base"), () -> true, () -> 1L, () -> 0L));
        coordinator.enqueue(new SharedIoCoordinator.StartRequest(domain,
                new SharedIoCoordinator.LaneKey(A, "factory-0"), 1L, 0L, 1,
                ignored -> 1, ignored -> committed.add("factory-0"), () -> true, () -> 1L, () -> 0L));

        coordinator.resolve(domain);

        assertThat(committed).containsExactly("factory-0", "base");
    }

    @Test
    void round_robin_rotates_between_all_lanes_of_the_same_controller() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        List<String> committed = new ArrayList<>();

        coordinator.enqueue(new SharedIoCoordinator.StartRequest(domain,
                new SharedIoCoordinator.LaneKey(A, "factory-0"), 1L, 0L, 1,
                ignored -> 1, ignored -> committed.add("factory-0"), () -> true, () -> 1L, () -> 0L));
        coordinator.resolve(domain);
        committed.clear();
        for (String laneId : List.of("base", "factory-0", "factory-1")) {
            coordinator.enqueue(new SharedIoCoordinator.StartRequest(domain,
                    new SharedIoCoordinator.LaneKey(A, laneId), 1L, 0L, 1,
                    ignored -> 1, ignored -> committed.add(laneId), () -> true, () -> 1L, () -> 0L));
        }

        coordinator.resolve(domain);

        assertThat(committed).containsExactly("factory-1", "base", "factory-0");
    }

    @Test
    void start_tick_and_finish_requests_use_independent_cursors() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A, B, C);
        List<String> committed = new ArrayList<>();

        coordinator.enqueue(start(domain, A, 1L, 1,
                ignored -> 1, ignored -> committed.add("initial-start"), () -> true, () -> 1L));
        coordinator.enqueue(tick(domain, B, 1L,
                () -> { committed.add("initial-tick"); return true; }, () -> true, () -> 1L));
        coordinator.enqueue(finish(domain, C, 1L,
                () -> { committed.add("initial-finish"); return true; }, () -> true, () -> 1L));
        coordinator.resolve(domain);
        committed.clear();

        enqueueAllRequestTypes(coordinator, domain, committed);
        coordinator.resolve(domain);

        assertThat(committed).containsExactly(
                "start:B", "tick:C", "finish:A",
                "start:C", "tick:A", "finish:B",
                "start:A", "tick:B", "finish:C");
    }

    @Test
    void cancelling_a_controller_discards_its_start_tick_and_finish_requests() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A, B);
        List<String> committed = new ArrayList<>();

        coordinator.enqueue(start(domain, A, 1L, 1, ignored -> 1, ignored -> committed.add("start"), () -> true, () -> 1L));
        coordinator.enqueue(tick(domain, A, 1L, () -> {
            committed.add("tick");
            return true;
        }, () -> true, () -> 1L));
        coordinator.enqueue(finish(domain, A, 1L, () -> {
            committed.add("finish");
            return true;
        }, () -> true, () -> 1L));
        coordinator.enqueue(tick(domain, B, 1L, () -> {
            committed.add("other-controller");
            return true;
        }, () -> true, () -> 1L));

        coordinator.cancel(A);
        coordinator.resolve(domain);

        assertThat(committed).containsExactly("other-controller");
    }

    @Test
    void finite_shared_energy_rotates_to_the_lane_that_can_finish_the_next_tick() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A, B);
        AtomicInteger energy = new AtomicInteger(20);
        List<String> advanced = new ArrayList<>();

        enqueueEnergyTick(coordinator, domain, A, energy, advanced);
        enqueueEnergyTick(coordinator, domain, B, energy, advanced);
        coordinator.resolve(domain);
        energy.addAndGet(15);
        enqueueEnergyTick(coordinator, domain, A, energy, advanced);
        enqueueEnergyTick(coordinator, domain, B, energy, advanced);
        coordinator.resolve(domain);

        assertThat(advanced).containsExactly("A", "B");
    }

    @Test
    void finish_commit_can_install_a_replacement_start_without_running_its_tick() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        SharedIoCoordinator.LaneKey lane = new SharedIoCoordinator.LaneKey(A, "base");
        AtomicInteger starts = new AtomicInteger();
        AtomicInteger ticks = new AtomicInteger();

        coordinator.enqueue(new SharedIoCoordinator.FinishRequest(domain, lane, 1L, 0L, () -> {
            coordinator.enqueue(new SharedIoCoordinator.StartRequest(domain, lane, 1L, 0L, 1,
                    ignored -> 1, ignored -> starts.incrementAndGet(), () -> true, () -> 1L, () -> 0L));
            coordinator.enqueue(new SharedIoCoordinator.TickRequest(domain, lane, 1L, 0L,
                    () -> { ticks.incrementAndGet(); return true; }, () -> true, () -> 1L, () -> 0L));
            return true;
        }, () -> true, () -> 1L, () -> 0L));

        coordinator.resolve(domain);

        assertThat(starts).hasValue(1);
        assertThat(ticks).hasValue(0);
    }

    @Test
    void tick_commit_can_enqueue_finish_for_the_same_domain_pass() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        SharedIoCoordinator.LaneKey lane = new SharedIoCoordinator.LaneKey(A, "base");
        List<String> committed = new ArrayList<>();

        coordinator.enqueue(new SharedIoCoordinator.TickRequest(domain, lane, 1L, 0L, () -> {
            committed.add("tick");
            coordinator.enqueue(new SharedIoCoordinator.FinishRequest(domain, lane, 1L, 0L,
                    () -> { committed.add("finish"); return true; }, () -> true, () -> 1L, () -> 0L));
            return true;
        }, () -> true, () -> 1L, () -> 0L));

        coordinator.resolve(domain);

        assertThat(committed).containsExactly("tick", "finish");
    }

    @Test
    void staleStructureAndStateVersionsNeverInvokeTransactions() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        AtomicLong structureVersion = new AtomicLong(1L);
        AtomicLong stateVersion = new AtomicLong(1L);
        AtomicBoolean invoked = new AtomicBoolean();

        coordinator.enqueue(new SharedIoCoordinator.TickRequest(domain, lane(A), 1L, 1L,
                () -> {
                    invoked.set(true);
                    return true;
                }, () -> true, structureVersion::get, stateVersion::get));
        structureVersion.incrementAndGet();

        coordinator.resolve(domain);

        assertThat(invoked).isFalse();
    }

    @Test
    void valid_request_is_checked_once_immediately_before_commit() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        AtomicInteger validations = new AtomicInteger();

        coordinator.enqueue(new SharedIoCoordinator.TickRequest(domain, lane(A), 1L, 0L,
                () -> true, () -> {
                    validations.incrementAndGet();
                    return true;
                }, () -> 1L, () -> 0L));

        coordinator.resolve(domain);

        assertThat(validations).hasValue(1);
    }

    @Test
    void catalog_change_before_shared_start_commit_never_runs_runtime_or_resource_transaction() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        AtomicLong catalogVersion = new AtomicLong(1L);
        AtomicInteger runtimeStarts = new AtomicInteger();
        AtomicInteger extractedInputs = new AtomicInteger();

        coordinator.enqueue(new SharedIoCoordinator.StartRequest(domain, lane(A), 1L, 0L, 1,
                ignored -> {
                    runtimeStarts.incrementAndGet();
                    extractedInputs.incrementAndGet();
                    return 1;
                }, ignored -> { }, () -> true, () -> 1L, () -> 0L,
                1L, catalogVersion::get, () -> { }));
        catalogVersion.set(2L);

        coordinator.resolve(domain);

        assertThat(runtimeStarts.get()).isZero();
        assertThat(extractedInputs.get()).isZero();
    }

    @Test
    void catalog_change_before_shared_tick_or_finish_commit_never_runs_transactions() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        AtomicLong catalogVersion = new AtomicLong(1L);
        AtomicInteger committed = new AtomicInteger();

        coordinator.enqueue(new SharedIoCoordinator.TickRequest(domain, lane(A), 1L, 0L,
                () -> {
                    committed.incrementAndGet();
                    return true;
                }, () -> true, () -> 1L, () -> 0L, 1L, catalogVersion::get, () -> { }));
        coordinator.enqueue(new SharedIoCoordinator.FinishRequest(domain, lane(A), 1L, 0L,
                () -> {
                    committed.incrementAndGet();
                    return true;
                }, () -> true, () -> 1L, () -> 0L, 1L, catalogVersion::get, () -> { }));
        catalogVersion.incrementAndGet();

        coordinator.resolve(domain);

        assertThat(committed).hasValue(0);
    }

    @Test
    void stateVersionInvalidationAlsoDiscardsPendingRequests() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        AtomicLong stateVersion = new AtomicLong(1L);
        AtomicBoolean invoked = new AtomicBoolean();

        coordinator.enqueue(new SharedIoCoordinator.TickRequest(domain, lane(A), 1L, 1L,
                () -> {
                    invoked.set(true);
                    return true;
                }, () -> true, () -> 1L, stateVersion::get));
        stateVersion.incrementAndGet();

        coordinator.resolve(domain);

        assertThat(invoked).isFalse();
    }

    @Test
    void finishRequestCanInstallAReplacementStartInTheSameDomainPass() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        AtomicInteger starts = new AtomicInteger();

        coordinator.enqueue(finish(domain, A, 1L, () -> {
            coordinator.enqueue(start(domain, A, 1L, 1,
                    ignored -> 1, ignored -> starts.incrementAndGet(), () -> true, () -> 1L));
            return true;
        }, () -> true, () -> 1L));

        coordinator.resolve(domain);

        assertThat(starts).hasValue(1);
    }

    @Test
    void resolve_reports_completed_work_even_when_a_replacement_request_remains_pending() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        coordinator.enqueue(finish(domain, A, 1L, () -> {
            coordinator.enqueue(start(domain, A, 1L, 1, ignored -> 0, ignored -> { }, () -> true, () -> 1L));
            return true;
        }, () -> true, () -> 1L));

        assertThat(coordinator.resolve(domain)).isEqualTo(1);
    }

    @Test
    void invalidValidatorPreventsAStaleLaneFromCommittingAfterEarlierLaneChangesState() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A, B);
        AtomicBoolean valid = new AtomicBoolean(true);
        AtomicBoolean staleInvoked = new AtomicBoolean();
        List<String> committed = new ArrayList<>();

        coordinator.enqueue(tick(domain, A, 1L,
                () -> {
                    committed.add("A");
                    valid.set(false);
                    return true;
                }, valid::get, () -> 1L));
        coordinator.enqueue(tick(domain, B, 1L,
                () -> {
                    staleInvoked.set(true);
                    committed.add("B");
                    return true;
                }, valid::get, () -> 1L));

        coordinator.resolve(domain);

        assertThat(committed).containsExactly("A");
        assertThat(staleInvoked).isFalse();
    }

    @Test
    void resolver_discards_a_partitioned_request_after_its_lifecycle_epoch_changes() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A, B);
        AtomicLong lifecycleEpoch = new AtomicLong(1L);
        AtomicBoolean staleInvoked = new AtomicBoolean();

        coordinator.enqueue(tick(domain, A, 1L, () -> {
            lifecycleEpoch.incrementAndGet();
            return true;
        }, () -> true, () -> 1L));
        long requestEpoch = lifecycleEpoch.get();
        coordinator.enqueue(tick(domain, B, 1L, () -> {
            staleInvoked.set(true);
            return true;
        }, () -> lifecycleEpoch.get() == requestEpoch, () -> 1L));

        coordinator.resolve(domain);

        assertThat(staleInvoked).isFalse();
    }

    @Test
    void validity_filtering_preserves_stage_order_when_requests_are_partitioned() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        AtomicBoolean invalid = new AtomicBoolean(false);
        List<String> committed = new ArrayList<>();

        coordinator.enqueue(start(domain, A, 1L, 1,
                ignored -> 1, ignored -> committed.add("start"), () -> true, () -> 1L));
        coordinator.enqueue(new SharedIoCoordinator.TickRequest(domain, lane(A), 1L, 0L,
                () -> {
                    invalid.set(true);
                    return true;
                }, () -> false, () -> 1L, () -> 0L));
        coordinator.enqueue(finish(domain, A, 1L,
                () -> {
                    committed.add("finish");
                    return true;
                }, () -> true, () -> 1L));

        coordinator.resolve(domain);

        assertThat(committed).containsExactly("start", "finish");
        assertThat(invalid).isFalse();
    }

    private static StructureClaimRegistry.ResourceDomain domain(BlockPos... positions) {
        return domain(1L, positions);
    }

    private static StructureClaimRegistry.ResourceDomain domain(long id, BlockPos... positions) {
        return new StructureClaimRegistry.ResourceDomain(id, 1L, Set.of(positions));
    }

    private static SharedIoCoordinator.LaneKey lane(BlockPos position) {
        return new SharedIoCoordinator.LaneKey(position, "base");
    }

    private static void enqueueAllRequestTypes(SharedIoCoordinator coordinator,
                                                StructureClaimRegistry.ResourceDomain domain,
                                                List<String> committed) {
        for (BlockPos pos : List.of(A, B, C)) {
            String lane = pos.equals(A) ? "A" : pos.equals(B) ? "B" : "C";
            SharedIoCoordinator.LaneKey laneKey = lane(pos);
            coordinator.enqueue(start(domain, pos, 1L, 1,
                    ignored -> 1, ignored -> committed.add("start:" + lane), () -> true, () -> 1L));
            coordinator.enqueue(new SharedIoCoordinator.TickRequest(domain, laneKey, 1L, 0L,
                    () -> { committed.add("tick:" + lane); return true; }, () -> true, () -> 1L, () -> 0L));
            coordinator.enqueue(new SharedIoCoordinator.FinishRequest(domain, laneKey, 1L, 0L,
                    () -> { committed.add("finish:" + lane); return true; }, () -> true, () -> 1L, () -> 0L));
        }
    }

    private static void enqueueEnergyTick(SharedIoCoordinator coordinator,
                                          StructureClaimRegistry.ResourceDomain domain,
                                          BlockPos position, AtomicInteger energy, List<String> advanced) {
        String lane = position.equals(A) ? "A" : "B";
        coordinator.enqueue(new SharedIoCoordinator.TickRequest(domain, lane(position), 1L, 0L,
                () -> {
                    if (energy.get() < 15) return false;
                    energy.addAndGet(-15);
                    advanced.add(lane);
                    return true;
                }, () -> true, () -> 1L, () -> 0L));
    }

    private static SharedIoCoordinator.StartRequest start(StructureClaimRegistry.ResourceDomain domain,
                                                           BlockPos position, long structureVersion,
                                                            long maximumParallelism,
                                                            LongUnaryOperator transaction,
                                                            LongConsumer committer,
                                                           BooleanSupplier validator,
                                                           LongSupplier structureVersionSupplier) {
        return new SharedIoCoordinator.StartRequest(domain, lane(position), structureVersion, 0L,
                maximumParallelism, transaction, committer, validator, structureVersionSupplier, () -> 0L);
    }

    private static SharedIoCoordinator.TickRequest tick(StructureClaimRegistry.ResourceDomain domain,
                                                         BlockPos position, long structureVersion,
                                                         BooleanSupplier transaction,
                                                         BooleanSupplier validator,
                                                         LongSupplier structureVersionSupplier) {
        return new SharedIoCoordinator.TickRequest(domain, lane(position), structureVersion, 0L,
                transaction, validator, structureVersionSupplier, () -> 0L);
    }

    private static SharedIoCoordinator.FinishRequest finish(StructureClaimRegistry.ResourceDomain domain,
                                                             BlockPos position, long structureVersion,
                                                             BooleanSupplier transaction,
                                                             BooleanSupplier validator,
                                                             LongSupplier structureVersionSupplier) {
        return new SharedIoCoordinator.FinishRequest(domain, lane(position), structureVersion, 0L,
                transaction, validator, structureVersionSupplier, () -> 0L);
    }

    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (T) ((sun.misc.Unsafe) field.get(null)).allocateInstance(type);
    }
}
