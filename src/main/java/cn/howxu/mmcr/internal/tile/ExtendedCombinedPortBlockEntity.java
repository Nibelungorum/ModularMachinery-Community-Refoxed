package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.port.ExtendedCombinedPortSize;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Storage host for extended combined item and fluid ports.
 *
 * @author howxu <dev@howxu.cn>
 */
public class ExtendedCombinedPortBlockEntity extends IOPortBlockEntity {
    private final LongItemStorage itemStorage;
    private final LongFluidStorage fluidStorage;
    private final IOPortKind kind;
    private CapabilitySnapshot capabilitySnapshot;

    public ExtendedCombinedPortBlockEntity(BlockPos pos, BlockState state) {
        this(pos, state, kindFromState(state, fallback(state)));
    }

    private ExtendedCombinedPortBlockEntity(BlockPos pos, BlockState state, IOPortKind kind) {
        super(typeForKind(kind), pos, state);
        ExtendedCombinedPortSize size = kind.extendedCombinedPortSize()
                .orElseThrow(() -> new IllegalStateException("Extended combined port missing size: " + kind.id()));
        this.kind = kind;
        this.itemStorage = new LongItemStorage(size.itemTypes(), Long.MAX_VALUE, this::markStorageChanged);
        this.fluidStorage = new LongFluidStorage(size.fluidTypes(), Long.MAX_VALUE, this::markStorageChanged);
    }

    @Override
    public IOType ioType() {
        return kind.ioType();
    }

    @Override
    public IOPortKind kind() {
        return kind;
    }

    /** Native item handler; the Transfer-backed port accessor is migrated by the capability task. */
    public LongItemStorage itemHandler() {
        return itemStorage;
    }

    @Override
    public LongItemStorage nativeItemHandler() {
        return itemStorage;
    }

    /** Native fluid handler; the Transfer-backed port accessor is migrated by the capability task. */
    public LongFluidStorage fluidHandler() {
        return fluidStorage;
    }

    @Override
    public LongFluidStorage nativeFluidHandler() {
        return fluidStorage;
    }

    @Override
    public void dropContents() {
        ItemBusBlockEntity.dropItemResources(level, worldPosition, itemStorage);
    }

    @Override
    public CapabilitySnapshot capabilitySnapshot() {
        if (capabilitySnapshot == null) {
            capabilitySnapshot = new CapabilitySnapshot(kind.definition().bindings().stream()
                    .filter(binding -> binding.directions().supports(kind.ioType()))
                    .map(this::createCapability)
                    .toList());
        }
        return capabilitySnapshot;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag output, HolderLookup.Provider registries) {
        super.saveAdditional(output, registries);
        saveItems(output, registries);
        saveFluids(output, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag input, HolderLookup.Provider registries) {
        beginLoadingAdditional();
        try {
            super.loadAdditional(input, registries);
            loadItems(input, registries);
            loadFluids(input, registries);
        } finally {
            endLoadingAdditional();
        }
    }

    private void saveItems(CompoundTag output, HolderLookup.Provider registries) {
        for (int slot = 0; slot < itemStorage.size(); slot++) {
            String suffix = "_" + slot;
            ItemStack resource = itemStorage.resource(slot);
            output.putBoolean("itemHasResource" + suffix, !resource.isEmpty());
            if (!resource.isEmpty()) {
                output.put("itemResource" + suffix, resource.save(registries));
                output.putLong("itemAmount" + suffix, itemStorage.amount(slot));
            }
        }
    }

    private void loadItems(CompoundTag input, HolderLookup.Provider registries) {
        for (int slot = 0; slot < itemStorage.size(); slot++) {
            String suffix = "_" + slot;
            if (input.getBoolean("itemHasResource" + suffix)) {
                ItemStack resource = ItemStack.parseOptional(registries, input.getCompound("itemResource" + suffix));
                itemStorage.setContents(slot, resource, input.getLong("itemAmount" + suffix));
            } else {
                itemStorage.setContents(slot, ItemStack.EMPTY, 0L);
            }
        }
    }

    private void saveFluids(CompoundTag output, HolderLookup.Provider registries) {
        for (int slot = 0; slot < fluidStorage.size(); slot++) {
            String suffix = "_" + slot;
            FluidStack resource = fluidStorage.resource(slot);
            boolean hasFluid = !resource.isEmpty();
            output.putBoolean("tankHasFluid" + suffix, hasFluid);
            if (hasFluid) {
                output.put("tankFluid" + suffix, resource.saveOptional(registries));
                output.putLong("tankAmount" + suffix, fluidStorage.amount(slot));
            }
        }
    }

    private void loadFluids(CompoundTag input, HolderLookup.Provider registries) {
        for (int slot = 0; slot < fluidStorage.size(); slot++) {
            String suffix = "_" + slot;
            if (input.getBoolean("tankHasFluid" + suffix)) {
                FluidStack resource = FluidStack.parseOptional(registries, input.getCompound("tankFluid" + suffix));
                fluidStorage.setContents(slot, resource, input.getLong("tankAmount" + suffix));
            } else {
                fluidStorage.setContents(slot, FluidStack.EMPTY, 0L);
            }
        }
    }

    private void markStorageChanged() {
        markAutoIOCacheDirty();
        notifyStorageChanged();
        notifyControllerOfInputChange();
    }

    private static IOPortKind fallback(BlockState state) {
        if (state.getBlock() instanceof IOPortBlock port && port.kind().ioType() == IOType.OUTPUT) {
            return PortKinds.EXTENDED_COMBINED_OUTPUT;
        }
        return PortKinds.EXTENDED_COMBINED_INPUT;
    }
}
