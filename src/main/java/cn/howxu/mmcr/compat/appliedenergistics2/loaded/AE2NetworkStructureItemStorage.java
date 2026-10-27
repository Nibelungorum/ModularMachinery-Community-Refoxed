package cn.howxu.mmcr.compat.appliedenergistics2.loaded;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.howxu.mmcr.internal.assembly.StructureItemSink;
import cn.howxu.mmcr.internal.assembly.StructureItemSource;
import cn.howxu.mmcr.internal.assembly.StructureItemStorage;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

/**
 * Terminal item storage and on-demand fluid source placement/recovery through an AE2 network.
 *
 * @author howxu <dev@howxu.cn>
 */
final class AE2NetworkStructureItemStorage implements StructureItemStorage, StructureItemSource, StructureItemSink {
    private final Supplier<MEStorage> storageSupplier;
    private final IActionSource actionSource;

    AE2NetworkStructureItemStorage(Supplier<MEStorage> storageSupplier, IActionSource actionSource) {
        this.storageSupplier = storageSupplier;
        this.actionSource = actionSource;
    }

    @Override
    public StructureItemSource source() {
        return this;
    }

    @Override
    public StructureItemSink sink() {
        return this;
    }

    @Override
    public List<ItemStack> copyStacks() {
        MEStorage storage = storageSupplier.get();
        if (storage == null) return List.of();
        KeyCounter available = storage.getAvailableStacks();
        List<ItemStack> stacks = new ArrayList<>();
        for (var entry : available) {
            if (entry.getKey() instanceof AEItemKey itemKey && entry.getLongValue() > 0L) {
                stacks.add(itemKey.toStack((int) Math.min(Integer.MAX_VALUE, entry.getLongValue())));
            }
        }
        return stacks;
    }

    @Override
    public boolean canExtractAll(List<ItemStack> requirements) {
        MEStorage storage = storageSupplier.get();
        return storage != null && canExtractAll(storage, requirements);
    }

    private boolean canExtractAll(MEStorage storage, List<ItemStack> requirements) {
        for (ItemStack requirement : requirements) {
            if (requirement.isEmpty()) continue;
            if (storage.extract(AEItemKey.of(requirement), requirement.getCount(), Actionable.SIMULATE, actionSource)
                    != requirement.getCount()) return false;
        }
        return true;
    }

    @Override
    public boolean extractAll(List<ItemStack> requirements) {
        MEStorage storage = storageSupplier.get();
        if (storage == null || !canExtractAll(storage, requirements)) return false;
        for (ItemStack requirement : requirements) {
            if (requirement.isEmpty()) continue;
            if (storage.extract(AEItemKey.of(requirement), requirement.getCount(), Actionable.MODULATE, actionSource)
                    != requirement.getCount()) throw new IllegalStateException("ME network changed during terminal extraction");
        }
        return true;
    }

    @Override
    public Map<Fluid, Long> copyFluidSources() {
        MEStorage storage = storageSupplier.get();
        if (storage == null) return Map.of();
        Map<Fluid, Long> sources = new HashMap<>();
        for (var entry : storage.getAvailableStacks()) {
            if (entry.getLongValue() > 0L && entry.getKey() instanceof AEFluidKey key && key.equals(AEFluidKey.of(key.getFluid()))) {
                long amount = storage.extract(key, entry.getLongValue(), Actionable.SIMULATE, actionSource);
                if (amount >= AEFluidKey.AMOUNT_BLOCK) sources.put(key.getFluid(), amount / AEFluidKey.AMOUNT_BLOCK);
            }
        }
        return sources;
    }

    @Override
    public boolean placeFluidSource(Fluid fluid, BooleanSupplier placement) {
        MEStorage storage = storageSupplier.get();
        if (storage == null) return false;
        AEFluidKey key = AEFluidKey.of(fluid);
        if (storage.extract(key, AEFluidKey.AMOUNT_BLOCK, Actionable.SIMULATE, actionSource)
                != AEFluidKey.AMOUNT_BLOCK) return false;
        long extracted = storage.extract(key, AEFluidKey.AMOUNT_BLOCK, Actionable.MODULATE, actionSource);
        boolean placed = false;
        try {
            placed = extracted == AEFluidKey.AMOUNT_BLOCK && placement.getAsBoolean();
            return placed;
        } finally {
            if (!placed && extracted > 0L
                    && storage.insert(key, extracted, Actionable.MODULATE, actionSource) != extracted) {
                throw new IllegalStateException("Unable to roll back terminal fluid extraction");
            }
        }
    }

    @Override
    public boolean supportsFluidSources() {
        return true;
    }

    @Override
    public boolean recoverFluidSource(Fluid fluid, BooleanSupplier removal) {
        MEStorage storage = storageSupplier.get();
        if (storage == null) return false;
        AEFluidKey key = AEFluidKey.of(fluid);
        if (storage.insert(key, AEFluidKey.AMOUNT_BLOCK, Actionable.SIMULATE, actionSource)
                != AEFluidKey.AMOUNT_BLOCK) return false;
        long inserted = storage.insert(key, AEFluidKey.AMOUNT_BLOCK, Actionable.MODULATE, actionSource);
        boolean removed = false;
        try {
            removed = inserted == AEFluidKey.AMOUNT_BLOCK && removal.getAsBoolean();
            return removed;
        } finally {
            if (!removed && inserted > 0L
                    && storage.extract(key, inserted, Actionable.MODULATE, actionSource) != inserted) {
                throw new IllegalStateException("Unable to roll back terminal fluid recovery");
            }
        }
    }

    @Override
    public boolean accept(ItemStack stack) {
        if (stack.isEmpty()) return true;
        MEStorage storage = storageSupplier.get();
        if (storage == null) return false;
        AEItemKey item = AEItemKey.of(stack);
        if (storage.insert(item, stack.getCount(), Actionable.SIMULATE, actionSource) != stack.getCount()) return false;
        long inserted = storage.insert(item, stack.getCount(), Actionable.MODULATE, actionSource);
        if (inserted == stack.getCount()) return true;
        if (inserted > 0L) storage.extract(item, inserted, Actionable.MODULATE, actionSource);
        return false;
    }
}
