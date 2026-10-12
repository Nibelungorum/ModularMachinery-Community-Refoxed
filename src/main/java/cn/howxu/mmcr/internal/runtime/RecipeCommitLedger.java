package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongTag;

/**
 * Main-thread payment facts for native recipe I/O, including uncertain partial execution.
 * An in-flight native operation is not a confirmed payment and cannot be replayed after reload.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeCommitLedger {
    private static final String SAVE_KEY = "execution_commit";

    public enum Phase { READY, UNCERTAIN, RESOURCE_PAID, WAITING_FINISH, ERROR_PAUSED, TERMINAL }

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

    public void markResourcePaid(int tick, boolean finalTick) {
        state = new State(Phase.RESOURCE_PAID, state.sequence() + 1,
                state.paidAttempts() + 1, tick, finalTick, state.outputPaid());
    }

    /** Native I/O is about to run; a callback cannot save this as a safely replayable batch. */
    public void beginNativeExecution() {
        if (state.phase() == Phase.UNCERTAIN) return;
        state = new State(Phase.UNCERTAIN, state.sequence() + 1, state.paidAttempts(),
                state.tick(), state.finalTickPaid(), state.outputPaid());
    }

    /** Settles recipe progress only after all native resource operations have succeeded. */
    public void settleTick(int tick, boolean finishPending) {
        if (state.phase() == Phase.ERROR_PAUSED || state.outputPaid()) return;
        boolean finalTickPaid = state.finalTickPaid() || finishPending;
        state = new State(finalTickPaid ? Phase.WAITING_FINISH : Phase.READY,
                state.sequence(), state.paidAttempts(), tick, finalTickPaid, false);
    }

    public void markOutputPaid() {
        boolean alreadyPaid = state.outputPaid();
        state = new State(Phase.TERMINAL, state.sequence() + 1, state.paidAttempts(),
                state.tick(), state.finalTickPaid(), true);
        // Native outputs are confirmed before this call; publish the fact before waking Post.
        if (!alreadyPaid && installedProgram != null) SharedIoCoordinator.executionChanged(installedProgram);
    }

    /** A completed native subphase settles its uncertainty without inventing an ordinary tick payment. */
    void completePrefetchDebit(State previous) {
        if (state.phase() != Phase.UNCERTAIN) return;
        state = new State(previous.phase(), state.sequence(), previous.paidAttempts(), previous.tick(),
                previous.finalTickPaid(), previous.outputPaid());
    }

    public void pauseAfterCommitFailure() {
        state = new State(Phase.ERROR_PAUSED, state.sequence(), state.paidAttempts(),
                state.tick(), state.finalTickPaid(), state.outputPaid());
    }

    /** All start effects were owner-scoped reservations and have been released; no ordinary payment occurred. */
    void settleReleasedStart() {
        if (state.paidAttempts() != 0L || state.finalTickPaid() || state.outputPaid()) return;
        state = new State(Phase.READY, state.sequence(), 0L, 0, false, false);
    }

    /** Writes the optional child into the owning runtime/lane's output. */
    public void save(CompoundTag output) {
        State saved = state;
        CompoundTag child = new CompoundTag();
        output.put(SAVE_KEY, child);
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
    public boolean load(CompoundTag input, int recipeTick, boolean finishPending) {
        if (recipeTick < 0) return false;
        CompoundTag saved = input.get(SAVE_KEY) instanceof CompoundTag tag ? tag : null;
        if (saved == null) {
            if (input.getAllKeys().contains(SAVE_KEY)) return false;
            state = new State(finishPending ? Phase.WAITING_FINISH : Phase.READY,
                    0L, 0L, recipeTick, finishPending, false);
            return true;
        }
        if (!(saved.get("sequence") instanceof LongTag sequenceTag)
                || !(saved.get("paid_attempts") instanceof LongTag paidAttemptsTag)
                || !(saved.get("final_tick_paid") instanceof ByteTag finalTickTag)
                || finalTickTag.getAsByte() != 0 && finalTickTag.getAsByte() != 1) {
            return false;
        }
        Phase phase;
        try {
            phase = Phase.valueOf((saved.contains("phase") ? saved.getString("phase") : ""));
        } catch (IllegalArgumentException exception) {
            return false;
        }
        long sequence = sequenceTag.getAsLong();
        long paidAttempts = paidAttemptsTag.getAsLong();
        boolean finalTickPaid = finalTickTag.getAsByte() != 0;
        if (sequence < 0L || paidAttempts < 0L || sequence < paidAttempts
                || phase == Phase.READY && (finalTickPaid || finishPending)
                || phase == Phase.WAITING_FINISH && !finalTickPaid
                || phase == Phase.RESOURCE_PAID && paidAttempts == 0L
                || phase == Phase.TERMINAL && sequence == paidAttempts
                || finishPending && !finalTickPaid) {
            return false;
        }
        boolean outputPaid = phase == Phase.TERMINAL;
        state = new State(phase == Phase.RESOURCE_PAID || phase == Phase.UNCERTAIN ? Phase.ERROR_PAUSED : phase,
                sequence, paidAttempts, recipeTick, finalTickPaid, outputPaid);
        return true;
    }

}
