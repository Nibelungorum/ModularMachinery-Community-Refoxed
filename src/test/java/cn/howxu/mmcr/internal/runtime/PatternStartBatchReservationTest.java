package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.runtime.PatternStartReservationTest.PaidPatternFixture;
import cn.howxu.mmcr.test.ServerThreadTestScope;
import cn.howxu.mmcr.test.TestBootstrap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Caller-owned batches preserve each prepared payment through root notifications.
 * @author howxu <dev@howxu.cn>
 */
class PatternStartBatchReservationTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        TestBootstrap.bootstrapCapabilities();
    }

    @AfterEach
    void cleanup() {
        RecipeRegistry.clearForTesting();
    }

    @Test
    void native_notification_throw_keeps_the_paid_prefix_and_does_not_pay_unvisited_lanes() throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            PaidPatternFixture first = new PaidPatternFixture("batch_paid_first");
            PaidPatternFixture second = new PaidPatternFixture("batch_paid_second");
            PatternStartBatchReservation batch = PatternStartBatchReservation.reserved(List.of(first.reserve(), second.reserve()));
            RecipeCommitLedger firstPayment = first.runtime.ledger();
            RecipeCommitLedger secondPayment = second.runtime.ledger();
            first.notification.set(() -> { throw new IllegalStateException("batch root notification"); });

            assertThat(batch.commit()).isTrue();
            assertThat(batch.status()).isEqualTo(PatternStartBatchReservation.Status.COMMITTED);
            assertThat(batch.reservations().getFirst().status()).isEqualTo(PatternStartReservation.Status.COMMITTED);
            assertThat(batch.reservations().getFirst().rolledBack()).isFalse();
            assertThat(batch.reservations().getLast().rolledBack()).isTrue();
            assertThat(first.afterCommit).hasValue(0);
            assertThat(second.afterCommit).hasValue(0);
            assertThat(batch.commit()).isFalse();
            batch.close();
            first.assertPausedPayment(firstPayment);
            assertThat(secondPayment.state().paidAttempts()).isZero();
            assertThat(second.inputs.amount(0)).isEqualTo(2L);
            assertThat(second.network.amount()).isEqualTo(100L);
            assertThat(second.buffer.amount()).isZero();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancelled_paid_lane_does_not_run_after_commit_or_clear_its_new_pending_slot(boolean notifyThrow) throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            PaidPatternFixture first = new PaidPatternFixture("batch_cancelled_first");
            PaidPatternFixture second = new PaidPatternFixture("batch_valid_second");
            PatternStartBatchReservation batch = PatternStartBatchReservation.reserved(List.of(first.reserve(), second.reserve()));
            AtomicReference<PatternStartReservation> replacement = new AtomicReference<>();
            AtomicReference<RecipeCommitLedger> replacementPayment = new AtomicReference<>();
            first.notification.set(() -> {
                first.runtime.invalidate();
                replacement.set(first.reserve());
                replacementPayment.set(first.runtime.ledger());
                if (notifyThrow) throw new IllegalStateException("cancelled batch root notification");
            });

            assertThat(batch.commit()).isTrue();
            assertThat(batch.status()).isEqualTo(PatternStartBatchReservation.Status.COMMITTED);
            batch.close();
            assertThat(batch.commit()).isFalse();
            assertThat(first.afterCommit).hasValue(0);
            assertThat(second.afterCommit).hasValue(0);
            assertThat(second.runtime.active()).isFalse();
            assertThat(second.buffer.reserved()).isZero();
            assertThat(second.inputs.amount(0)).isEqualTo(2L);
            assertThat(second.network.amount()).isEqualTo(100L);
            first.assertPendingReplacement(replacement.get(), replacementPayment.get());
            assertThat(replacement.get().commit()).isTrue();
            first.assertReplacementPaid();
        }
    }

    @Test
    void ownership_write_failure_retains_each_native_lane_payment_without_activating_or_repaying() throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            PaidPatternFixture first = new PaidPatternFixture("batch_unpaid_first");
            PaidPatternFixture second = new PaidPatternFixture("batch_unpaid_second");
            PatternStartBatchReservation batch = PatternStartBatchReservation.reserved(List.of(first.reserve(), second.reserve()));

            assertThat(batch.commit(() -> { throw new IllegalStateException("native ownership settlement"); })).isTrue();
            assertThat(batch.status()).isEqualTo(PatternStartBatchReservation.Status.COMMITTED);
            for (PaidPatternFixture fixture : List.of(first, second)) {
                assertThat(fixture.runtime.active()).isFalse();
                fixture.assertPausedPayment(fixture.runtime.ledger(), 1L);
                assertThat(fixture.afterCommit).hasValue(0);
            }
            assertThat(batch.commit()).isFalse();
        }
    }

    @Test
    void unavailable_child_leaves_the_unpaid_proposal_and_other_reservations_untouched() throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            PaidPatternFixture first = new PaidPatternFixture("batch_rejected_first");
            PaidPatternFixture second = new PaidPatternFixture("batch_rejected_second");
            PatternStartReservation firstReservation = first.reserve();
            PatternStartReservation secondReservation = second.reserve();
            PatternStartBatchReservation batch = PatternStartBatchReservation.reserved(List.of(firstReservation, secondReservation));
            secondReservation.rollback();

            assertThat(batch.commit()).isFalse();
            assertThat(batch.status()).isEqualTo(PatternStartBatchReservation.Status.RESERVED);
            assertThat(firstReservation.rolledBack()).isFalse();
            assertThat(first.runtime.pendingPatternRecipeId()).isEqualTo(first.recipe.id());
            assertThat(first.runtime.ledger().state().paidAttempts()).isZero();
            assertThat(first.inputs.amount(0)).isEqualTo(2L);
            assertThat(first.network.amount()).isEqualTo(100L);
            assertThat(first.buffer.amount()).isZero();
            assertThat(first.afterCommit).hasValue(0);
            assertThat(firstReservation.commit()).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void batch_and_children_reject_nested_commits_until_all_activation_callbacks_finish(boolean notifyThrow) throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            PaidPatternFixture first = new PaidPatternFixture("batch_guard_first");
            PaidPatternFixture second = new PaidPatternFixture("batch_guard_second");
            PatternStartReservation firstReservation = first.reserve();
            PatternStartReservation secondReservation = second.reserve();
            PatternStartBatchReservation batch = PatternStartBatchReservation.reserved(List.of(firstReservation, secondReservation));
            AtomicInteger nestedWrites = new AtomicInteger();
            AtomicInteger notifications = new AtomicInteger();
            Runnable nestedCommits = () -> {
                assertThat(batch.commit(() -> nestedWrites.incrementAndGet())).isFalse();
                assertThat(firstReservation.commit(() -> nestedWrites.incrementAndGet())).isFalse();
                assertThat(secondReservation.commit(() -> nestedWrites.incrementAndGet())).isFalse();
            };
            firstReservation.afterCommit(() -> {
                assertThat(secondReservation.commit(() -> nestedWrites.incrementAndGet())).isFalse();
                assertThat(batch.commit()).isFalse();
            });
            first.notification.set(() -> {
                notifications.incrementAndGet();
                nestedCommits.run();
                if (notifyThrow) throw new IllegalStateException("guarded batch root notification");
            });

            assertThat(batch.commit(nestedCommits)).isTrue();
            assertThat(notifications).hasValue(1);
            assertThat(nestedWrites).hasValue(0);
            assertThat(batch.status()).isEqualTo(PatternStartBatchReservation.Status.COMMITTED);
            if (notifyThrow) {
                first.assertPausedPayment(first.runtime.ledger());
                assertThat(second.networkCommits).hasValue(0);
                assertThat(second.inputs.amount(0)).isEqualTo(2L);
            } else {
                first.assertSinglePayment();
                second.assertSinglePayment();
            }
            batch.close();
            assertThat(batch.commit()).isFalse();
            first.runtime.invalidate();
            second.runtime.invalidate();
            PatternStartBatchReservation next = PatternStartBatchReservation.reserved(List.of(first.reserve(), second.reserve()));
            assertThat(next.commit()).isTrue();
            first.assertSinglePaymentAfterSecondStart(notifyThrow ? 1 : 2);
            if (notifyThrow) second.assertSinglePayment();
            else second.assertSinglePaymentAfterSecondStart(2);
        }
    }

    @Test
    void partial_child_lock_failure_releases_only_its_acquired_prefix() throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            PaidPatternFixture free = new PaidPatternFixture("batch_lock_free");
            PaidPatternFixture held = new PaidPatternFixture("batch_lock_held");
            PaidPatternFixture tail = new PaidPatternFixture("batch_lock_tail");
            PatternStartReservation freeReservation = free.reserve();
            PatternStartReservation heldReservation = held.reserve();
            PatternStartReservation tailReservation = tail.reserve();
            PatternStartBatchReservation owner = PatternStartBatchReservation.reserved(List.of(heldReservation, tailReservation));
            PatternStartBatchReservation contender = PatternStartBatchReservation.reserved(List.of(freeReservation, heldReservation));
            AtomicInteger rejectedWrites = new AtomicInteger();
            AtomicInteger notifications = new AtomicInteger();
            held.notification.set(() -> {
                notifications.incrementAndGet();
                assertThat(contender.commit(() -> rejectedWrites.incrementAndGet())).isFalse();
                assertThat(contender.status()).isEqualTo(PatternStartBatchReservation.Status.RESERVED);
                assertThat(freeReservation.status()).isEqualTo(PatternStartReservation.Status.RESERVED);
                assertThat(freeReservation.rolledBack()).isFalse();
                assertThat(free.runtime.ledger().state().paidAttempts()).isZero();
                assertThat(free.inputs.amount(0)).isEqualTo(2L);
                assertThat(free.network.amount()).isEqualTo(100L);
                assertThat(free.buffer.amount()).isZero();
                assertThat(heldReservation.rolledBack()).isFalse();
                assertThat(heldReservation.commit(() -> rejectedWrites.incrementAndGet())).isFalse();
                assertThat(owner.commit(() -> rejectedWrites.incrementAndGet())).isFalse();
                assertThat(freeReservation.commit()).isTrue();
            });

            assertThat(owner.commit()).isTrue();
            assertThat(notifications).hasValue(1);
            assertThat(rejectedWrites).hasValue(0);
            for (PaidPatternFixture fixture : List.of(free, held, tail)) fixture.assertSinglePayment();
        }
    }
}
