package cn.howxu.mmcr.compat.fluxnetworks.loaded;

import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import sonar.fluxnetworks.api.FluxConstants;
import sonar.fluxnetworks.common.data.FluxDeviceConfigComponent;
import sonar.fluxnetworks.common.device.FluxPointHandler;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.IntSupplier;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

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
    private final IntSupplier networkId;
    private final LongValueStorage mirror = new LongValueStorage(Long.MAX_VALUE, Long.MAX_VALUE, null);
    private long reserved;
    private long tentative;
    private long requestTick = Long.MIN_VALUE;
    private long target;
    private long cycleTick = Long.MIN_VALUE;
    private long cycleReceived;
    private long cycleReturned;
    private long cycleRemaining;
    private long consumed;
    private final Map<Object, RecipeOwner> recipeOwners = new IdentityHashMap<>();
    private final Map<Object, Long> continuations = new IdentityHashMap<>();
    private boolean idleConfirmed;
    private final Map<Integer, Long> bufferSources = new LinkedHashMap<>();
    private Object searchOwner;
    private boolean searchCandidateEligible;
    private long searchWarmup;
    private long searchBaselineTentative;
    private long patternTarget;
    private long patternExpiresAt;
    // Pattern providers can retry without an admitted lane; retain one coalesced batch for their retry window.
    private static final long PATTERN_RETRY_WINDOW = 100L;

    public FluxNetworkPointHandler(BooleanSupplier active, LongSupplier gameTime, Runnable onChange) {
        this(active, gameTime, onChange, null);
    }

    public FluxNetworkPointHandler(BooleanSupplier active, LongSupplier gameTime, Runnable onChange, IntSupplier networkId) {
        this.active = active;
        this.gameTime = gameTime;
        this.onChange = onChange;
        this.networkId = networkId;
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
        if (remaining > 0L) {
            recipeOwners.put(owner, new RecipeOwner(remaining, cancelOwner));
            idleConfirmed = false;
        } else {
            recipeOwners.remove(owner);
            if (recipeOwners.isEmpty() && continuations.isEmpty()) idleConfirmed = true;
        }
    }

    public void updateContinuation(Object owner, long amount) {
        if (amount > 0L) {
            continuations.put(owner, amount);
            idleConfirmed = false;
        } else {
            continuations.remove(owner);
            idleConfirmed = continuations.isEmpty() && recipeOwners.isEmpty();
        }
    }

    public void beginSearch(Object owner) {
        searchOwner = owner;
        searchCandidateEligible = false;
        searchWarmup = 0L;
        searchBaselineTentative = tentative;
    }

    public void searchCandidate(Object owner, boolean eligible) {
        if (searchOwner == owner) searchCandidateEligible = eligible;
    }

    public void endSearch(Object owner, boolean found) {
        endSearch(owner, found, false);
    }

    public void endSearch(Object owner, boolean found, boolean patternRequest) {
        if (searchOwner != owner) return;
        long waiting = searchWarmup;
        searchOwner = null;
        searchCandidateEligible = false;
        searchWarmup = 0L;
        if (patternRequest) {
            if (found) clearPatternDemand();
            else if (waiting > 0L) {
                patternTarget = Math.max(patternTarget, waiting);
                patternExpiresAt = gameTime.getAsLong() + PATTERN_RETRY_WINDOW;
                idleConfirmed = false;
            }
        } else if (!found) updateContinuation(owner, waiting);
    }

    public void clearPatternDemand() {
        patternTarget = 0L;
        patternExpiresAt = 0L;
        if (continuations.isEmpty() && recipeOwners.isEmpty()) idleConfirmed = true;
    }

    private void expirePatternDemand() {
        if (patternTarget > 0L && gameTime.getAsLong() >= patternExpiresAt) clearPatternDemand();
    }

    public void stopPrefetch() {
        continuations.clear();
        clearPatternDemand();
        clearDemand();
        idleConfirmed = true;
    }

    public List<Integer> bufferNetworks() { return List.copyOf(bufferSources.keySet()); }

    public long refundable(int sourceNetwork) {
        expirePatternDemand();
        long now = gameTime.getAsLong();
        boolean returningToSource = networkId != null && sourceNetwork != networkId.getAsInt();
        boolean idle = idleConfirmed && continuations.isEmpty() && recipeOwners.isEmpty()
                && patternTarget == 0L && requestTick != now;
        return sourceNetwork >= 0 && (returningToSource || idle)
                ? Math.min(bufferSources.getOrDefault(sourceNetwork, 0L), availableForPrefetch()) : 0L;
    }

    public long returnToNetwork(int sourceNetwork, long requested) {
        prepareCycle();
        long amount = Math.min(Math.max(0L, requested), Math.min(refundable(sourceNetwork),
                Math.max(0L, getLimit() - cycleReceived - cycleReturned)));
        if (amount > 0L) {
            mBuffer -= amount;
            long remaining = bufferSources.get(sourceNetwork) - amount;
            if (remaining == 0L) bufferSources.remove(sourceNetwork);
            else bufferSources.put(sourceNetwork, remaining);
            cycleReturned += amount;
            changed();
        }
        return amount;
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
        stopPrefetch();
    }

    private record RecipeOwner(long remaining, Runnable cancel) { }

    public void requestWarmup(long amount) {
        if (amount <= 0L || !active.getAsBoolean()) return;
        if (searchOwner != null && searchCandidateEligible) {
            long staged = Math.max(0L, tentative - searchBaselineTentative);
            searchWarmup = Math.max(searchWarmup,
                    amount > Long.MAX_VALUE - staged ? Long.MAX_VALUE : staged + amount);
        }
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
        prepareCycle();
        expirePatternDemand();
        long now = gameTime.getAsLong();
        long wanted = active.getAsBoolean() && requestTick == now ? target : 0L;
        if (active.getAsBoolean()) {
            long rolling = reserved + tentative;
            for (long amount : continuations.values()) {
                rolling = amount > Long.MAX_VALUE - rolling ? Long.MAX_VALUE : rolling + amount;
            }
            long pattern = patternTarget > Long.MAX_VALUE - reserved - tentative
                    ? Long.MAX_VALUE : reserved + tentative + patternTarget;
            wanted = Math.max(wanted, Math.max(rolling, pattern));
        } else {
            wanted = 0L;
        }
        long missing = wanted > mBuffer ? wanted - mBuffer : 0L;
        // Consumption must not reopen network allocations already used this tick.
        long remainingLimit = Math.max(0L, getLimit() - cycleReceived - cycleReturned);
        cycleRemaining = Math.min(missing, remainingLimit);
    }

    private void prepareCycle() {
        long now = gameTime.getAsLong();
        if (cycleTick != now) {
            cycleTick = now;
            cycleReceived = 0L;
            cycleReturned = 0L;
        }
    }

    @Override
    public long getRequest() {
        long now = gameTime.getAsLong();
        return active.getAsBoolean() && (requestTick == now || !continuations.isEmpty() || patternTarget > 0L) && cycleTick == now
                ? Math.min(cycleRemaining, Long.MAX_VALUE - mBuffer) : 0L;
    }

    @Override
    public void addToBuffer(long amount) {
        if (amount < 0L || amount > getRequest()) throw new IllegalArgumentException("Invalid Flux allocation");
        if (amount == 0L) return;
        if (mBuffer == 0L) bufferSources.clear();
        bufferSources.merge(networkId == null ? -1 : networkId.getAsInt(), amount, Math::addExact);
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
        debitSources(amount);
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
        debitSources(amount);
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
            metadata.put("sources", saveSources());
            tag.put("mmcr_prefetch", metadata);
        }
    }

    @Override
    public void readCustomTag(CompoundTag tag, byte type) {
        super.readCustomTag(tag, type);
        if (type == FluxConstants.NBT_SAVE_ALL) {
            reserved = Math.min(mBuffer, Math.max(0L, tag.getCompound("mmcr_prefetch").getLong("reserved")));
            CompoundTag metadata = tag.getCompound("mmcr_prefetch");
            loadSources(metadata.getCompound("sources"), metadata.contains("buffer_network")
                    ? metadata.getInt("buffer_network") : tag.getInt(FluxConstants.NETWORK_ID));
            recipeOwners.clear();
            continuations.clear();
            idleConfirmed = false;
            resetTransientState();
            cycleTick = Long.MIN_VALUE;
            cycleReceived = 0L;
            cycleReturned = 0L;
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
            resetSources(config.networkId());
            resetTransientState();
        } else {
            clearDemand();
        }
        mirror.setAmount(mBuffer);
    }

    @Override
    public void readPacketBuffer(FriendlyByteBuf buffer, byte type) {
        long previous = mBuffer;
        super.readPacketBuffer(buffer, type);
        if (mBuffer != previous) resetSources(-1);
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
        searchOwner = null;
        searchWarmup = 0L;
        searchCandidateEligible = false;
        clearPatternDemand();
        clearDemand();
    }

    private void debitSources(long amount) {
        var iterator = bufferSources.entrySet().iterator();
        while (iterator.hasNext() && amount > 0L) {
            var entry = iterator.next();
            long removed = Math.min(amount, entry.getValue());
            amount -= removed;
            if (removed == entry.getValue()) iterator.remove();
            else entry.setValue(entry.getValue() - removed);
        }
    }

    private void resetSources(int sourceNetwork) {
        bufferSources.clear();
        if (mBuffer > 0L) bufferSources.put(sourceNetwork, mBuffer);
    }

    public CompoundTag saveSources() {
        ListTag sources = new ListTag();
        bufferSources.forEach((network, amount) -> {
            CompoundTag source = new CompoundTag();
            source.putInt("network", network);
            source.putLong("amount", amount);
            sources.add(source);
        });
        CompoundTag data = new CompoundTag();
        data.put("balances", sources);
        return data;
    }

    public void loadSources(CompoundTag data, int legacyNetwork) {
        resetSources(legacyNetwork);
        if (!data.contains("balances")) return;
        Map<Integer, Long> restored = new LinkedHashMap<>();
        ListTag sources = data.getList("balances", Tag.TAG_COMPOUND);
        long total = 0L;
        for (int index = 0; index < sources.size(); index++) {
            CompoundTag source = sources.getCompound(index);
            long amount = source.getLong("amount");
            if (!source.contains("network", Tag.TAG_INT) || !source.contains("amount", Tag.TAG_LONG)
                    || amount <= 0L || amount > mBuffer - total) {
                resetSources(-1);
                return;
            }
            total += amount;
            restored.merge(source.getInt("network"), amount, Math::addExact);
        }
        if (total != mBuffer) {
            resetSources(-1);
            return;
        }
        bufferSources.clear();
        bufferSources.putAll(restored);
    }

    private void changed() {
        mirror.setAmount(mBuffer);
        onChange.run();
    }
}
