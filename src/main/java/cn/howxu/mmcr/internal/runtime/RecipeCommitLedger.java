package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Main-thread payment facts journaled in the same transaction as recipe I/O.
 * Root-commit notifications do not change these facts or invoke user callbacks.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeCommitLedger extends SnapshotJournal<RecipeCommitLedger.State> {
    private static final String SAVE_KEY = "execution_commit";

    public enum Phase { READY, RESOURCE_PAID, WAITING_FINISH, ERROR_PAUSED, TERMINAL }

    public record State(Phase phase, long sequence, long paidAttempts,
                        int tick, boolean finalTickPaid, boolean outputPaid) { }

    private State state = new State(Phase.READY, 0L, 0L, 0, false, false);
    private InstalledRecipeProgram installedProgram;

    void bindInstalledProgram(InstalledRecipeProgram program) { installedProgram = program; }

    void unbindInstalledProgram(InstalledRecipeProgram program) {
        if (installedProgram == program) installedProgram = null;
    }

    public RecipeCommitLedger() {
    }

    public State state() {
        return state;
    }

    public void markResourcePaid(int tick, boolean finalTick, TransactionContext transaction) {
        updateSnapshots(transaction);
        state = new State(Phase.RESOURCE_PAID, state.sequence() + 1,
                state.paidAttempts() + 1, tick, finalTick, state.outputPaid());
    }

    /** Settles recipe progress only after the resource transaction has committed. */
    public void settleTick(int tick, boolean finishPending) {
        if (state.phase() == Phase.ERROR_PAUSED || state.outputPaid()) return;
        boolean finalTickPaid = state.finalTickPaid() || finishPending;
        Phase phase = finalTickPaid ? Phase.WAITING_FINISH : Phase.READY;
        if (state.phase() == phase && state.tick() == tick
                && state.finalTickPaid() == finalTickPaid) return;
        state = new State(phase, state.sequence(), state.paidAttempts(),
                tick, finalTickPaid, false);
    }

    /** Equivalent to a committed empty resource transaction followed by a non-final grant. */
    void settleNoEffectsTick(int tick) {
        state = new State(Phase.READY, state.sequence() + 1L, state.paidAttempts() + 1L, tick, false, false);
    }

    public void markOutputPaid(TransactionContext transaction) {
        updateSnapshots(transaction);
        state = new State(Phase.TERMINAL, state.sequence() + 1, state.paidAttempts(),
                state.tick(), state.finalTickPaid(), true);
    }

    public void pauseAfterCommitFailure() {
        state = new State(Phase.ERROR_PAUSED, state.sequence(), state.paidAttempts(),
                state.tick(), state.finalTickPaid(), state.outputPaid());
    }

    /** Writes the optional child into the owning runtime/lane's output. */
    public void save(ValueOutput output) {
        State saved = state;
        ValueOutput child = output.child(SAVE_KEY);
        // Output payment is authoritative even when a later notification paused the runtime.
        child.putString("phase", (saved.outputPaid() ? Phase.TERMINAL : saved.phase()).name());
        child.putLong("sequence", saved.sequence());
        child.putLong("paid_attempts", saved.paidAttempts());
        child.putBoolean("final_tick_paid", saved.finalTickPaid());
    }

    /**
     * Restores from the owning runtime/lane, using the loaded recipe's progress.
     * Invalid data returns false without replacing the current state.
     */
    public boolean load(ValueInput input, int recipeTick, boolean finishPending) {
        if (recipeTick < 0) return false;
        CompoundTag saved = input.read(SAVE_KEY, CompoundTag.CODEC).orElse(null);
        if (saved == null) {
            if (input.keySet().contains(SAVE_KEY)) return false;
            state = new State(finishPending ? Phase.WAITING_FINISH : Phase.READY,
                    0L, 0L, recipeTick, finishPending, false);
            return true;
        }
        if (!(saved.get("sequence") instanceof LongTag sequenceTag)
                || !(saved.get("paid_attempts") instanceof LongTag paidAttemptsTag)
                || !(saved.get("final_tick_paid") instanceof ByteTag finalTickTag)
                || finalTickTag.byteValue() != 0 && finalTickTag.byteValue() != 1) {
            return false;
        }
        Phase phase;
        try {
            phase = Phase.valueOf(saved.getStringOr("phase", ""));
        } catch (IllegalArgumentException exception) {
            return false;
        }
        long sequence = sequenceTag.longValue();
        long paidAttempts = paidAttemptsTag.longValue();
        boolean finalTickPaid = finalTickTag.byteValue() != 0;
        if (sequence < 0L || paidAttempts < 0L || sequence < paidAttempts
                || phase == Phase.READY && (finalTickPaid || finishPending)
                || phase == Phase.WAITING_FINISH && !finalTickPaid
                || phase == Phase.RESOURCE_PAID && paidAttempts == 0L
                || phase == Phase.TERMINAL && sequence == paidAttempts
                || finishPending && !finalTickPaid) {
            return false;
        }
        boolean outputPaid = phase == Phase.TERMINAL;
        state = new State(phase == Phase.RESOURCE_PAID ? Phase.ERROR_PAUSED : phase,
                sequence, paidAttempts, recipeTick, finalTickPaid, outputPaid);
        return true;
    }

    @Override
    protected State createSnapshot() {
        return state;
    }

    @Override
    protected void revertToSnapshot(State snapshot) {
        state = snapshot;
    }

    @Override
    protected void onRootCommit(State originalState) {
        if (state.outputPaid() && !originalState.outputPaid() && installedProgram != null) {
            // Only a committed terminal fact wakes Post; an aborted output transaction does not.
            SharedIoCoordinator.executionChanged(installedProgram);
        }
    }
}
