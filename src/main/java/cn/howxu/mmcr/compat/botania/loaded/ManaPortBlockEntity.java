package cn.howxu.mmcr.compat.botania.loaded;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.compat.botania.ManaStorage;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import vazkii.botania.api.block.Wandable;
import vazkii.botania.api.mana.ManaItem;
import vazkii.botania.api.mana.ManaPool;
import vazkii.botania.api.mana.spark.ManaSparkAttachable;
import vazkii.botania.common.block.block_entity.mana.ManaPoolBlockEntity;
import vazkii.botania.common.block.block_entity.mana.ThrottledPacket;
import vazkii.botania.common.helper.EntityHelper;

/** One real store with fixed native projections and throttled client sync. @author howxu <dev@howxu.cn> */
public final class ManaPortBlockEntity extends IOPortBlockEntity
        implements ManaPool, ManaSparkAttachable, Wandable, ThrottledPacket<ManaPortBlockEntity> {
    private final IOPortKind kind;
    private final ManaStorage storage;
    private CapabilitySnapshot capabilitySnapshot;
    private boolean markedForSync;
    private boolean chunkUnloaded;
    private int lastSynchronizedMana;

    public ManaPortBlockEntity(BlockPos pos, BlockState state, IOPortKind kind) {
        super(ModBlockEntities.BES.get(kind.id()).get(), pos, state);
        this.kind = kind;
        storage = new ManaStorage(this::markManaChanged);
    }

    public ManaStorage storage() { return storage; }
    public ManaPortBlockEntity externalHandler() { return this; }
    @Override public IOType ioType() { return kind.ioType(); }
    @Override public IOPortKind kind() { return kind; }

    @Override
    public CapabilitySnapshot capabilitySnapshot() {
        if (capabilitySnapshot == null) {
            capabilitySnapshot = new CapabilitySnapshot(kind.definition().bindings().stream()
                    .map(this::createCapability).toList());
        }
        return capabilitySnapshot;
    }

    private void markManaChanged() {
        notifyStorageChanged();
        notifyControllerOfInputChange();
        markForPotentialSync();
    }

    private boolean isCurrentHost() {
        return level != null && !isRemoved() && !chunkUnloaded && level.hasChunkAt(worldPosition)
                && level.getBlockEntity(worldPosition) == this;
    }

    @Override public int getCurrentMana() { return !isCurrentHost() || ioType() == IOType.INPUT ? 0 : storage.amount(); }
    @Override public int getMaxMana() { return storage.capacity(); }
    @Override public boolean isFull() { return !isCurrentHost() || ioType() == IOType.OUTPUT || storage.amount() >= storage.capacity(); }

    @Override
    public void receiveMana(int value) {
        if (!isCurrentHost() || level.isClientSide()) return;
        if (value > 0 && ioType() == IOType.INPUT) storage.move(value, true, false);
        else if (value < 0 && ioType() == IOType.OUTPUT) storage.move(-(long) value, false, false);
    }

    @Override public boolean canReceiveManaFromBursts() { return isCurrentHost() && ioType() == IOType.INPUT; }
    @Override public boolean isOutputtingPower() { return ioType() == IOType.OUTPUT; }
    @Override public int getAvailableSpaceForMana() { return isCurrentHost() && ioType() == IOType.INPUT ? storage.capacity() - storage.amount() : 0; }
    @Override public boolean areIncomingTransfersDone() { return !isCurrentHost() || ioType() == IOType.OUTPUT; }
    @Override public boolean canAttachSpark(ItemStack stack) { return isCurrentHost(); }
    @Override public boolean canHaveAugment(ItemStack augment) { return isCurrentHost(); }
    @Override public Level getManaReceiverLevel() { return getLevel(); }
    @Override public BlockPos getManaReceiverPos() { return getBlockPos(); }

    public void tickMana() {
        if (!isCurrentHost() || level.isClientSide()) return;
        boolean canTransfer = ioType() == IOType.INPUT ? storage.amount() < storage.capacity() : storage.amount() > 0;
        if (canTransfer) {
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(worldPosition))) transferManaItem(item);
        }
        maybeSyncNow();
    }

    public boolean transferManaItem(ItemEntity entity) {
        if (!isCurrentHost() || level.isClientSide() || entity.level() != level
                || !entity.isAlive() || entity.getItem().isEmpty()) return false;
        ManaItem item = ManaItem.LOOKUP.find(entity.getItem());
        if (item == null) return false;
        boolean insert = ioType() == IOType.INPUT;
        if (insert ? !item.canDrainManaToPool(this) : !item.canReceiveManaFromPool(this)) return false;
        long poolAvailable = insert ? (long) storage.capacity() - storage.amount() : storage.amount();
        long itemAvailable = insert ? item.getMana() : (long) item.getMaxMana() - item.getMana();
        int moved = (int) Math.min(ManaPoolBlockEntity.TRANSFER_BASE_RATE,
                Math.min(Math.max(0L, poolAvailable), Math.max(0L, itemAvailable)));
        if (moved == 0) return false;
        item.addMana(insert ? -moved : moved);
        storage.move(moved, insert, false);
        EntityHelper.syncItem(entity);
        return true;
    }

    @Override
    public boolean onUsedByWand(@Nullable Player player, ItemStack stack, Direction side) {
        if (isCurrentHost() && !level.isClientSide()) markForImmediateSync();
        return true;
    }

    @Override public ManaPortBlockEntity getSelf() { return this; }
    @Override public boolean isMarkedForSync() { return markedForSync; }
    @Override public void setMarkedForSync(boolean marked) { markedForSync = marked; }
    @Override public int getSyncInterval() { return 13; }

    @Override
    public void markForImmediateSync() {
        ThrottledPacket.super.markForImmediateSync();
        lastSynchronizedMana = storage.amount();
    }

    @Override
    public boolean mayBeRelevantForClients(Level level) {
        int mana = storage.amount();
        if ((lastSynchronizedMana == 0 ^ mana == 0)
                || (float) Math.abs(lastSynchronizedMana - mana) / storage.capacity() > 0.01F) {
            for (Player player : level.players()) {
                if (player.isAlive() && player.position().closerThan(worldPosition.getCenter(), 64)) return true;
            }
        }
        return ThrottledPacket.super.mayBeRelevantForClients(level);
    }

    @Override
    public void onLoad() {
        chunkUnloaded = false;
        super.onLoad();
        initializeAvailabilityBaseline();
    }

    @Override
    public void onChunkUnloaded() {
        chunkUnloaded = true;
        super.onChunkUnloaded();
    }

    @Override
    protected void saveAdditional(CompoundTag output, HolderLookup.Provider registries) {
        super.saveAdditional(output, registries);
        CompoundTag mana = new CompoundTag();
        mana.putInt("amount", storage.amount());
        output.put("mana", mana);
    }

    @Override
    protected void loadAdditional(CompoundTag input, HolderLookup.Provider registries) {
        beginLoadingAdditional();
        try {
            super.loadAdditional(input, registries);
            storage.setAmount(input.getCompound("mana").getLong("amount"));
            lastSynchronizedMana = storage.amount();
            markedForSync = false;
        } finally {
            endLoadingAdditional();
            initializeAvailabilityBaseline();
        }
    }
}
