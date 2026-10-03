package cn.howxu.mmcr.compat.fluxnetworks.loaded;

import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import sonar.fluxnetworks.api.FluxConstants;
import sonar.fluxnetworks.common.data.FluxDeviceConfigComponent;
import sonar.fluxnetworks.common.device.FluxPointHandler;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Demand-driven Point whose native buffer is the sole local energy inventory.
 * All inventory and reservation operations run on the server thread.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FluxNetworkPointHandler extends FluxPointHandler {
    private final BooleanSupplier active;
    private final LongSupplier gameTime;
    private final Runnable onChange;
    private final LongValueStorage mirror = new LongValueStorage(Long.MAX_VALUE, Long.MAX_VALUE, null);
    private long reserved;
    private long tentative;
    private long requestTick = Long.MIN_VALUE;
    private long target;
    private long cycleTick = Long.MIN_VALUE;
    private long cycleReceived;
    private long cycleRemaining;
    private long consumed;
    private final Map<Object, RecipeOwner> recipeOwners = new IdentityHashMap<>();

    public FluxNetworkPointHandler(BooleanSupplier active, LongSupplier gameTime, Runnable onChange) {
        this.active = active;
        this.gameTime = gameTime;
        this.onChange = onChange;
    }

    /** Returns a planning projection, never a second writable energy inventory. */
    public LongValueStorage storage() {
        mirror.setAmount(mBuffer);
        return mirror;
    }

    public long reserved() { return reserved; }
    public long availableForPrefetch() { return mBuffer - reserved - tentative; }

    public void updateRecipeOwner(Object owner, long remaining, Runnable cancelOwner) {
        clearDemand();
        if (remaining > 0L) recipeOwners.put(owner, new RecipeOwner(remaining, cancelOwner));
        else recipeOwners.remove(owner);
    }

    /** Called only after all controller runtime restoration has actually completed. */
    public void reconcileRecipeOwners() {
        long owned = 0L;
        for (RecipeOwner owner : recipeOwners.values()) owned = Math.addExact(owned, owner.remaining());
        releaseReserved(Math.max(0L, reserved - owned));
    }

    public void cancelRecipeOwners() {
        for (RecipeOwner owner : List.copyOf(recipeOwners.values())) owner.cancel().run();
        recipeOwners.clear();
        clearDemand();
    }

    private record RecipeOwner(long remaining, Runnable cancel) { }

    public void requestWarmup(long amount) {
        if (amount <= 0L || !active.getAsBoolean()) return;
        long now = gameTime.getAsLong();
        if (requestTick != now) {
            requestTick = now;
            target = 0L;
        }
        long held = reserved + tentative;
        target = Math.max(target, amount > Long.MAX_VALUE - held ? Long.MAX_VALUE : held + amount);
    }

    public void clearDemand() {
        requestTick = Long.MIN_VALUE;
        target = 0L;
        cycleRemaining = 0L;
    }

    @Override
    public void onCycleStart() {
        long now = gameTime.getAsLong();
        if (cycleTick != now) {
            cycleTick = now;
            cycleReceived = 0L;
        }
        long wanted = active.getAsBoolean() && requestTick == now ? target : 0L;
        long missing = wanted > mBuffer ? wanted - mBuffer : 0L;
        // Consumption must not reopen network allocations already used this tick.
        long remainingLimit = Math.max(0L, getLimit() - cycleReceived);
        cycleRemaining = Math.min(missing, remainingLimit);
    }

    @Override
    public long getRequest() {
        long now = gameTime.getAsLong();
        return active.getAsBoolean() && requestTick == now && cycleTick == now
                ? Math.min(cycleRemaining, Long.MAX_VALUE - mBuffer) : 0L;
    }

    @Override
    public void addToBuffer(long amount) {
        if (amount < 0L || amount > getRequest()) throw new IllegalArgumentException("Invalid Flux allocation");
        if (amount == 0L) return;
        mBuffer += amount;
        cycleRemaining -= amount;
        cycleReceived += amount;
        changed();
    }

    @Override
    public void onCycleEnd() {
        // Native Point statistics report local consumption, not network receipts.
        mChange = -consumed;
        consumed = 0L;
    }

    public boolean reserveCandidate(long amount) {
        if (amount <= 0L || availableForPrefetch() < amount) return false;
        tentative += amount;
        return true;
    }

    public boolean commitCandidate(long amount) {
        if (amount <= 0L || tentative < amount || mBuffer - reserved < amount) return false;
        tentative -= amount;
        reserved += amount;
        changed();
        return true;
    }

    /** Cancels a tentative plan, or validates already-loaded reservation metadata. */
    public void restoreCandidateOrSavedReservation(long amount) {
        if (amount <= 0L) return;
        if (tentative > 0L) {
            if (amount > tentative) throw new IllegalStateException("Missing tentative Flux reservation");
            tentative -= amount;
        } else if (amount > reserved) {
            throw new IllegalStateException("Missing saved Flux reservation");
        }
    }

    public long releaseReserved(long amount) {
        long released = Math.min(Math.max(0L, amount), reserved);
        if (released > 0L) {
            reserved -= released;
            changed();
        }
        return released;
    }

    public boolean consumeLocal(long amount) {
        if (amount <= 0L || amount > mBuffer - tentative) return false;
        mBuffer -= amount;
        reserved -= Math.min(reserved, amount);
        consumed = amount > Long.MAX_VALUE - consumed ? Long.MAX_VALUE : consumed + amount;
        changed();
        return true;
    }

    public boolean consumeReserved(long amount) {
        return amount > 0L && amount <= reserved && consumeLocal(amount);
    }

    public boolean consumeUnreserved(long amount) {
        if (amount <= 0L || amount > availableForPrefetch()) return false;
        mBuffer -= amount;
        consumed = amount > Long.MAX_VALUE - consumed ? Long.MAX_VALUE : consumed + amount;
        changed();
        return true;
    }

    @Override
    public void writeCustomTag(CompoundTag tag, byte type) {
        super.writeCustomTag(tag, type);
        if (type == FluxConstants.NBT_SAVE_ALL) {
            CompoundTag metadata = new CompoundTag();
            metadata.putLong("reserved", reserved);
            tag.put("mmcr_prefetch", metadata);
        }
    }

    @Override
    public void readCustomTag(CompoundTag tag, byte type) {
        super.readCustomTag(tag, type);
        if (type == FluxConstants.NBT_SAVE_ALL) {
            reserved = Math.min(mBuffer, Math.max(0L, tag.getCompound("mmcr_prefetch").getLong("reserved")));
            recipeOwners.clear();
            resetTransientState();
            cycleTick = Long.MIN_VALUE;
            cycleReceived = 0L;
        }
        mirror.setAmount(mBuffer);
    }

    @Override
    public void applyConfiguration(FluxDeviceConfigComponent config, long energy) {
        boolean inventoryChanged = energy != mBuffer;
        if (inventoryChanged) {
            // The callbacks release controller allocations before the native inventory is replaced.
            cancelRecipeOwners();
        }
        super.applyConfiguration(config, energy);
        if (inventoryChanged) {
            reserved = 0L;
            resetTransientState();
        } else {
            clearDemand();
        }
        mirror.setAmount(mBuffer);
    }

    @Override
    public void readPacketBuffer(FriendlyByteBuf buffer, byte type) {
        super.readPacketBuffer(buffer, type);
        mirror.setAmount(mBuffer);
    }

    @Override
    public void onNetworkChanged() {
        super.onNetworkChanged();
        clearDemand();
    }

    private void resetTransientState() {
        tentative = 0L;
        consumed = 0L;
        mChange = 0L;
        clearDemand();
    }

    private void changed() {
        mirror.setAmount(mBuffer);
        onChange.run();
    }
}
