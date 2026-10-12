package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.internal.runtime.RecipeCommitLedger.Phase;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises native payment conservation and persistence without inventing rollback.
 *
 * @author howxu <dev@howxu.cn>
 */
class RecipeCommitLedgerTest {
    @Test
    void simulated_resource_payment_does_not_mutate_storage_or_ledger() {
        LongValueStorage storage = chargedStorage(null);
        RecipeCommitLedger ledger = new RecipeCommitLedger();
        RecipeCommitLedger.State original = ledger.state();
        assertThat(storage.extract(7L, true)).isEqualTo(7L);
        assertThat(storage.amount()).isEqualTo(40L);
        assertThat(ledger.state()).isEqualTo(original);
    }

    @Test
    void committed_resource_payment_settles_without_another_charge() {
        LongValueStorage storage = chargedStorage(null);
        RecipeCommitLedger ledger = new RecipeCommitLedger();
        storage.extract(7L, false);
        ledger.markResourcePaid(0, false);
        assertThat(storage.amount()).isEqualTo(33L);
        assertThat(ledger.state()).isEqualTo(new RecipeCommitLedger.State(
                Phase.RESOURCE_PAID, 1L, 1L, 0, false, false));
        ledger.settleTick(1, false);
        assertThat(ledger.state()).isEqualTo(new RecipeCommitLedger.State(
                Phase.READY, 1L, 1L, 1, false, false));
        assertThat(storage.amount()).isEqualTo(33L);
    }

    @Test
    void later_native_failure_keeps_successful_payment_and_prevents_replay() {
        LongValueStorage storage = chargedStorage(null);
        RecipeCommitLedger ledger = new RecipeCommitLedger();
        assertThat(storage.extract(7L, false)).isEqualTo(7L);
        ledger.markResourcePaid(0, true);
        LongValueStorage unavailable = new LongValueStorage(100L, 100L, null);
        assertThat(unavailable.extract(7L, false)).isZero();
        ledger.pauseAfterCommitFailure();
        ledger.settleTick(1, true);
        assertThat(storage.amount()).isEqualTo(33L);
        assertThat(ledger.state().phase()).isEqualTo(Phase.ERROR_PAUSED);
        assertThat(ledger.state().finalTickPaid()).isTrue();
        RecipeCommitLedger restored = new RecipeCommitLedger();
        assertThat(load(restored, save(ledger), 0, false)).isTrue();
        restored.settleTick(1, true);
        assertThat(restored.state().phase()).isEqualTo(Phase.ERROR_PAUSED);
        assertThat(restored.state().paidAttempts()).isEqualTo(1L);
        assertThat(storage.amount()).isEqualTo(33L);
    }

    @Test
    void native_notification_failure_keeps_mutation_without_inventing_confirmed_payment() {
        AtomicBoolean fail = new AtomicBoolean();
        LongValueStorage storage = chargedStorage(() -> {
            if (fail.get()) throw new IllegalStateException("notification failed");
        });
        RecipeCommitLedger ledger = new RecipeCommitLedger();
        fail.set(true);
        // Native callbacks run inside mutation: fence payment before entering that window.
        ledger.beginNativeExecution();
        assertThatThrownBy(() -> storage.extract(7L, false)).isInstanceOf(RuntimeException.class);
        assertThat(storage.amount()).isEqualTo(33L);
        assertThat(ledger.state().phase()).isEqualTo(Phase.UNCERTAIN);
        ledger.pauseAfterCommitFailure();
        ledger.settleTick(3, true);
        assertThat(ledger.state()).isEqualTo(new RecipeCommitLedger.State(
                Phase.ERROR_PAUSED, 1L, 0L, 0, false, false));
        RecipeCommitLedger restored = new RecipeCommitLedger();
        assertThat(load(restored, save(ledger), 0, false)).isTrue();
        assertThat(restored.state()).isEqualTo(ledger.state());
    }

    @Test
    void native_notification_save_restores_uncertainty_as_paused_without_inventing_attempts() {
        RecipeCommitLedger ledger = new RecipeCommitLedger();
        AtomicBoolean saving = new AtomicBoolean();
        AtomicReference<CompoundTag> saved = new AtomicReference<>();
        LongValueStorage storage = chargedStorage(() -> {
            if (saving.get()) saved.set(save(ledger));
        });
        saving.set(true);
        ledger.beginNativeExecution();
        storage.extract(7L, false);
        RecipeCommitLedger restored = new RecipeCommitLedger();
        assertThat(load(restored, saved.get(), 2, false)).isTrue();
        assertThat(restored.state()).isEqualTo(new RecipeCommitLedger.State(
                Phase.ERROR_PAUSED, 1L, 0L, 2, false, false));
        assertThat(storage.amount()).isEqualTo(33L);
    }

    @Test
    void final_payment_survives_settlement_and_waiting_output_reload() {
        RecipeCommitLedger ledger = paidFinalTick();
        ledger.settleTick(2, true);
        RecipeCommitLedger restored = new RecipeCommitLedger();
        assertThat(load(restored, save(ledger), 2, true)).isTrue();
        assertThat(restored.state()).isEqualTo(ledger.state());
        assertThat(restored.state().phase()).isEqualTo(Phase.WAITING_FINISH);
        assertThat(restored.state().finalTickPaid()).isTrue();
    }

    @Test
    void legacy_waiting_output_derives_final_payment_without_inventing_attempts() {
        RecipeCommitLedger ledger = new RecipeCommitLedger();
        assertThat(load(ledger, new CompoundTag(), 4, true)).isTrue();
        assertThat(ledger.state()).isEqualTo(new RecipeCommitLedger.State(
                Phase.WAITING_FINISH, 0L, 0L, 4, true, false));
        RecipeCommitLedger restored = new RecipeCommitLedger();
        assertThat(load(restored, save(ledger), 4, true)).isTrue();
        assertThat(restored.state()).isEqualTo(ledger.state());
    }

    @Test
    void legacy_running_recipe_remains_ready_at_its_saved_tick() {
        RecipeCommitLedger ledger = new RecipeCommitLedger();
        assertThat(load(ledger, new CompoundTag(), 4, false)).isTrue();
        assertThat(ledger.state()).isEqualTo(new RecipeCommitLedger.State(
                Phase.READY, 0L, 0L, 4, false, false));
    }

    @Test
    void simulated_output_leaves_waiting_state_and_output_storage_unchanged() {
        RecipeCommitLedger ledger = paidFinalTick();
        ledger.settleTick(2, true);
        RecipeCommitLedger.State waiting = ledger.state();
        LongValueStorage output = new LongValueStorage(100L, 100L, null);
        assertThat(output.insert(9L, true)).isEqualTo(9L);
        assertThat(output.amount()).isZero();
        assertThat(ledger.state()).isEqualTo(waiting);
    }

    @Test
    void committed_output_is_terminal_even_when_notification_throws_and_saves_reentrantly() {
        RecipeCommitLedger ledger = paidFinalTick();
        AtomicReference<CompoundTag> saved = new AtomicReference<>();
        LongValueStorage output = new LongValueStorage(100L, 100L, null);
        output.insert(9L, false);
        ledger.markOutputPaid();
        assertThatThrownBy(() -> {
            saved.set(save(ledger));
            throw new IllegalStateException("confirmed output notification failed");
        }).isInstanceOf(RuntimeException.class);
        assertThat(output.amount()).isEqualTo(9L);
        assertThat(ledger.state()).isEqualTo(new RecipeCommitLedger.State(
                Phase.TERMINAL, 2L, 1L, 2, true, true));
        RecipeCommitLedger restored = new RecipeCommitLedger();
        assertThat(load(restored, saved.get(), 2, true)).isTrue();
        assertThat(restored.state()).isEqualTo(ledger.state());
        ledger.pauseAfterCommitFailure();
        assertThat(ledger.state().outputPaid()).isTrue();
        assertThat(load(restored, save(ledger), 2, true)).isTrue();
        assertThat(restored.state().phase()).isEqualTo(Phase.TERMINAL);
        assertThat(restored.state().outputPaid()).isTrue();
    }

    @Test
    void malformed_child_never_falls_back_to_legacy_ready() {
        RecipeCommitLedger ledger = paidFinalTick();
        RecipeCommitLedger.State original = ledger.state();
        CompoundTag wrongType = new CompoundTag();
        wrongType.putString("execution_commit", "not a compound");
        assertThat(load(ledger, wrongType, 2, false)).isFalse();
        CompoundTag emptyChild = new CompoundTag();
        emptyChild.put("execution_commit", new CompoundTag());
        assertThat(load(ledger, emptyChild, 2, false)).isFalse();
        for (String field : new String[] { "phase", "sequence", "paid_attempts", "final_tick_paid" }) {
            CompoundTag missing = save(ledger);
            missing.getCompound("execution_commit").remove(field);
            assertThat(load(ledger, missing, 2, false)).as("missing %s", field).isFalse();
            CompoundTag malformed = save(ledger);
            malformed.getCompound("execution_commit").putString(field, "invalid");
            assertThat(load(ledger, malformed, 2, false)).as("invalid %s", field).isFalse();
        }
        for (String field : new String[] { "sequence", "paid_attempts" }) {
            CompoundTag negative = save(ledger);
            negative.getCompound("execution_commit").putLong(field, -1L);
            assertThat(load(ledger, negative, 2, false)).as("negative %s", field).isFalse();
            CompoundTag fractional = save(ledger);
            fractional.getCompound("execution_commit").putDouble(field, -0.5);
            assertThat(load(ledger, fractional, 2, false)).as("fractional %s", field).isFalse();
        }
        CompoundTag invalidBoolean = save(ledger);
        invalidBoolean.getCompound("execution_commit").putByte("final_tick_paid", (byte) 2);
        assertThat(load(ledger, invalidBoolean, 2, false)).isFalse();
        assertThat(load(ledger, save(ledger), -1, false)).isFalse();
        assertThat(ledger.state()).isEqualTo(original);
    }

    @Test
    void inconsistent_payment_facts_are_rejected_without_replacing_state() {
        RecipeCommitLedger ledger = new RecipeCommitLedger();
        RecipeCommitLedger.State original = ledger.state();
        CompoundTag unpaidWaiting = save(ledger);
        unpaidWaiting.getCompound("execution_commit").putString("phase", "WAITING_FINISH");
        assertThat(load(ledger, unpaidWaiting, 2, true)).isFalse();
        CompoundTag unpaidTerminal = save(ledger);
        unpaidTerminal.getCompound("execution_commit").putString("phase", "TERMINAL");
        assertThat(load(ledger, unpaidTerminal, 2, false)).isFalse();
        CompoundTag impossibleCounter = save(paidFinalTick());
        impossibleCounter.getCompound("execution_commit").putLong("sequence", 0L);
        assertThat(load(ledger, impossibleCounter, 2, false)).isFalse();
        assertThat(load(ledger, save(ledger), 2, true)).isFalse();
        assertThat(ledger.state()).isEqualTo(original);
    }

    @Test
    void ordinary_unsettled_payment_reload_pauses_at_outer_recipe_tick() {
        RecipeCommitLedger ledger = new RecipeCommitLedger();
        LongValueStorage storage = chargedStorage(null);
        ledger.markResourcePaid(3, false);
        storage.extract(7L, false);
        RecipeCommitLedger restored = new RecipeCommitLedger();
        assertThat(load(restored, save(ledger), 3, false)).isTrue();
        assertThat(restored.state()).isEqualTo(new RecipeCommitLedger.State(
                Phase.ERROR_PAUSED, 1L, 1L, 3, false, false));
        assertThat(storage.amount()).isEqualTo(33L);
    }

    @Test
    void independent_lane_ledgers_restore_without_sharing_payment_facts() {
        RecipeCommitLedger waiting = paidFinalTick();
        waiting.settleTick(2, true);
        RecipeCommitLedger ready = new RecipeCommitLedger();
        CompoundTag input = new CompoundTag();
        input.put("lane_a", save(waiting));
        input.put("lane_b", save(ready));
        RecipeCommitLedger restoredWaiting = new RecipeCommitLedger();
        RecipeCommitLedger restoredReady = new RecipeCommitLedger();
        assertThat(restoredWaiting.load(input.getCompound("lane_a"), 2, true)).isTrue();
        assertThat(restoredReady.load(input.getCompound("lane_b"), 0, false)).isTrue();
        assertThat(restoredWaiting.state()).isEqualTo(waiting.state());
        assertThat(restoredReady.state()).isEqualTo(ready.state());
        restoredWaiting.pauseAfterCommitFailure();
        assertThat(restoredReady.state()).isEqualTo(ready.state());
    }

    private static LongValueStorage chargedStorage(Runnable onChange) {
        LongValueStorage storage = new LongValueStorage(100L, 100L, onChange);
        storage.setAmount(40L);
        return storage;
    }

    private static RecipeCommitLedger paidFinalTick() {
        RecipeCommitLedger ledger = new RecipeCommitLedger();
        ledger.markResourcePaid(2, true);
        return ledger;
    }

    private static CompoundTag save(RecipeCommitLedger ledger) {
        CompoundTag output = new CompoundTag();
        ledger.save(output);
        return output;
    }

    private static boolean load(RecipeCommitLedger ledger, CompoundTag tag, int tick, boolean finishPending) {
        return ledger.load(tag, tick, finishPending);
    }
}
