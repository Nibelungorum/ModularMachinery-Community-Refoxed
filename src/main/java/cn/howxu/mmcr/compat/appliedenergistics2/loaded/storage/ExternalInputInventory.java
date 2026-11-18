package cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage;

import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.util.ConfigInventory;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2ResourceFamilies;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.InputInterfaceBlockEntity;
import cn.howxu.mmcr.mixin.compat.appliedenergistics2.InterfaceLogicAccessor;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Local slots shared by external ME storage and the item, fluid and optional chemical pipe adapters.
 * The machine itself continues to use the unfiltered inventory.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ExternalInputInventory implements GenericInternalInventory, MEStorage {
    private final InputInterfaceBlockEntity host;
    private final ConfigInventory storage;

    public ExternalInputInventory(InputInterfaceBlockEntity host) {
        this.host = host;
        storage = host.getInterfaceLogic().getStorage();
    }

    private boolean accessible(int slot) {
        if (!host.isExternalInputSlot(slot)) return false;
        AEKey key = storage.getKey(slot);
        return key == null || isSupportedType(key);
    }

    @Override public boolean isSupportedType(AEKeyType type) {
        return AE2ResourceFamilies.exposesLocalSlots(type);
    }
    @Override public int size() { return storage.size(); }
    @Override public boolean canInsert() { return storage.canInsert(); }
    @Override public boolean canExtract() { return storage.canExtract(); }
    @Override public @Nullable GenericStack getStack(int slot) { return accessible(slot) ? storage.getStack(slot) : null; }
    @Override public @Nullable AEKey getKey(int slot) { return accessible(slot) ? storage.getKey(slot) : null; }
    @Override public long getAmount(int slot) { return accessible(slot) ? storage.getAmount(slot) : 0; }
    @Override public long getMaxAmount(AEKey key) { return isSupportedType(key) ? storage.getMaxAmount(key) : 0; }
    @Override public long getCapacity(AEKeyType type) { return isSupportedType(type) ? storage.getCapacity(type) : 0; }
    @Override public boolean isAllowedIn(int slot, AEKey key) { return accessible(slot) && isSupportedType(key) && storage.isAllowedIn(slot, key); }

    @Override
    public void setStack(int slot, @Nullable GenericStack stack) {
        if (stack == null ? accessible(slot) : isAllowedIn(slot, stack.what())) storage.setStack(slot, stack);
    }

    @Override
    public long insert(int slot, AEKey key, long amount, Actionable mode) {
        if (!isAllowedIn(slot, key)) return 0;
        return storage.insert(slot, key, Math.min(amount, Long.MAX_VALUE - storage.getAmount(slot)), mode);
    }

    @Override
    public long extract(int slot, AEKey key, long amount, Actionable mode) {
        return accessible(slot) ? storage.extract(slot, key, amount, mode) : 0;
    }

    @Override public void beginBatch() { storage.beginBatch(); }
    @Override public void endBatch() { storage.endBatch(); }
    @Override public void endBatchSuppressed() { storage.endBatchSuppressed(); }
    @Override public void onChange() { storage.onChange(); }

    @Override
    public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
        MEStorage.checkPreconditions(key, amount, mode, source);
        if (!isSupportedType(key)) return 0;
        var logic = (InterfaceLogicAccessor) host.getInterfaceLogic();
        if (logic.mmcr$requestPriority(source).isPresent() && logic.mmcr$isSameGrid(source)) return 0;
        long moved = 0;
        for (int i = 0; i < size() && moved < amount; i++) moved += insert(i, key, amount - moved, mode);
        return moved;
    }

    @Override
    public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
        MEStorage.checkPreconditions(key, amount, mode, source);
        if (!isSupportedType(key)) return 0;
        var logic = (InterfaceLogicAccessor) host.getInterfaceLogic();
        var priority = logic.mmcr$requestPriority(source);
        if (priority.isPresent() && priority.getAsInt() <= host.getPriority() && logic.mmcr$isSameGrid(source)) return 0;
        long moved = 0;
        for (int i = 0; i < size() && moved < amount; i++) moved += extract(i, key, amount - moved, mode);
        return moved;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        for (int i = 0; i < size(); i++) {
            GenericStack stack = getStack(i);
            if (stack != null) out.add(stack.what(), stack.amount());
        }
    }

    @Override public Component getDescription() { return host.getMainMenuIcon().getHoverName(); }
}
