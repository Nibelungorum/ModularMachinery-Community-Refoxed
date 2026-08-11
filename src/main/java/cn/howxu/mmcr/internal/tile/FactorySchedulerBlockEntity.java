package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContextPool;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.internal.recipe.FactoryRecipeScheduler;
import cn.howxu.mmcr.internal.network.FactoryControllerSnapshot;
import cn.howxu.mmcr.internal.network.PktFactoryControllerStatePayload;
import cn.howxu.mmcr.internal.menu.FactoryControllerMenu;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;

/**
 * Single-slot storage for thread dispersers.
 *
 * @author howxu <dev@howxu.cn>
 */
public class FactorySchedulerBlockEntity extends BlockEntity {

    private static final int THREAD_LIMIT_SYNC_INTERVAL = 40;

    private final ItemStackHandler handler = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(ModItems.THREAD_DISPERSER.get());
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private int threadLimit = 1;
    private int threadLimitSyncTicks;
    private FactoryRecipeScheduler scheduler = new FactoryRecipeScheduler(threadLimit);
    private @Nullable FactoryControllerSnapshot lastSyncedSnapshot;

    public FactorySchedulerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BES.get("factory_controller").get(), pos, state);
    }

    public int activeLaneCount() {
        return scheduler.activeLaneCount();
    }

    public int activeThreadCount() {
        return scheduler.activeThreadCount();
    }

    public int usedParallelism() {
        return scheduler.usedParallelism();
    }

    public int availableParallelism() {
        return scheduler.availableParallelism();
    }

    public int threadLimit() {
        return threadLimit;
    }

    public boolean hasLaneCapacity() {
        return scheduler.hasCapacity();
    }

    public int laneCapacity() {
        return scheduler.laneCapacity();
    }

    public boolean startLane(FactoryRecipeScheduler.Lane lane) {
        boolean started = scheduler.startLane(lane);
        if (started) setChanged();
        return started;
    }

    public void tickScheduler() {
        tickScheduler(null);
    }

    public void tickScheduler(SyncListener syncListener) {
        tickThreadLimitSync(syncListener);
        int before = scheduler.activeLaneCount();
        scheduler.tick();
        if (scheduler.activeLaneCount() != before) setChanged();
    }

    public void tickScheduler(MachineControllerBlockEntity controller, List<MachineRecipe> candidates,
                              long structureVersion, int parallelLimit, RecipeCraftingContextPool contextPool) {
        tickThreadLimitSync(controller);
        int before = scheduler.activeThreadCount();
        scheduler.tickThreads(controller, candidates, structureVersion, parallelLimit, contextPool);
        if (scheduler.activeThreadCount() != before) setChanged();
        if (controller != null) syncOpenControllerMenus(controller);
    }

    public void setThreadLimit(int threadLimit) {
        this.threadLimit = Math.max(1, threadLimit);
        this.scheduler.setThreadLimit(this.threadLimit);
        lastSyncedSnapshot = null;
        setChanged();
    }

    private void syncThreadLimit(SyncListener syncListener) {
        int current = threadCount();
        if (current == threadLimit) return;
        setThreadLimit(current);
        if (syncListener != null) syncListener.syncFactoryScheduler();
    }

    private void tickThreadLimitSync(SyncListener syncListener) {
        if (++threadLimitSyncTicks < THREAD_LIMIT_SYNC_INTERVAL) return;
        threadLimitSyncTicks = 0;
        syncThreadLimit(syncListener);
    }

    public void stopAll() {
        scheduler.stopAll();
        lastSyncedSnapshot = null;
    }

    public void pause() {
        scheduler.pause();
        lastSyncedSnapshot = null;
        setChanged();
    }

    public void resume() {
        scheduler.resume();
        lastSyncedSnapshot = null;
    }

    public void ensureBaseThreadFor(MachineControllerBlockEntity controller) {
        scheduler.ensureBaseThread(controller, null);
    }

    public void syncCoreThreads(MachineControllerBlockEntity controller, Machine machine,
                                List<MachineRecipe> candidates, RecipeCraftingContextPool contextPool) {
        scheduler.syncCoreThreads(controller, machine, candidates, contextPool);
        lastSyncedSnapshot = null;
    }

    public List<FactoryRecipeScheduler.ThreadSnapshot> threadSnapshots(MachineControllerBlockEntity controller) {
        syncThreadLimit(null);
        ensureBaseThreadFor(controller);
        return scheduler.threadSnapshots();
    }

    public FactoryControllerSnapshot snapshot(MachineControllerBlockEntity controller) {
        syncThreadLimit(null);
        ensureBaseThreadFor(controller);
        return new FactoryControllerSnapshot(controller.getBlockPos(), controller.isFormed(), controller.isRedstonePaused(),
                activeThreadCount(), threadLimit(), usedParallelism(), controller.getMaxParallelism(),
                controller.getMachine() == null ? "" : controller.getMachine().localizedName(),
                controller.parallelControllerCount(), controller.getLastFailureUnloc(), scheduler.threadSnapshots());
    }

    public void sendSnapshot(ServerPlayer player, MachineControllerBlockEntity controller) {
        if (player != null) player.connection.send(new ClientboundCustomPayloadPacket(new PktFactoryControllerStatePayload(snapshot(controller))));
    }

    public void syncOpenControllerMenus(MachineControllerBlockEntity controller) {
        if (!(controller.getLevel() instanceof ServerLevel serverLevel)) return;
        FactoryControllerSnapshot next = snapshot(controller);
        if (next.equals(lastSyncedSnapshot)) return;
        lastSyncedSnapshot = next;
        for (ServerPlayer player : serverLevel.players()) {
            if (player.containerMenu instanceof FactoryControllerMenu menu && menu.controllerPos().equals(controller.getBlockPos())) {
                player.connection.send(new ClientboundCustomPayloadPacket(new PktFactoryControllerStatePayload(next)));
            }
        }
    }

    public int threadCount() {
        long count = 1L + handler.getStackInSlot(0).getCount();
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }

    public IItemHandler getItemHandler(Direction side) {
        return handler;
    }

    public ItemStackHandler getItemStackHandler(Direction side) {
        return handler;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        handler.serialize(output.child("inventory"));
        output.putInt("thread_limit", threadLimit);
        scheduler.save(output.child("scheduler"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        handler.deserialize(input.childOrEmpty("inventory"));
        threadLimit = Math.max(1, input.getIntOr("thread_limit", 1));
        threadLimitSyncTicks = 0;
        scheduler = new FactoryRecipeScheduler(threadLimit);
        scheduler.load(input.childOrEmpty("scheduler"), null, null);
        lastSyncedSnapshot = null;
    }

    @Override
    public void setRemoved() {
        stopAll();
        super.setRemoved();
    }

    @FunctionalInterface
    public interface SyncListener {
        void syncFactoryScheduler();
    }
}
