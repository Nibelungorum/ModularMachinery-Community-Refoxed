package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.facet.PersistenceFacet;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.port.CombinedPortSize;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.util.IOType;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Ordinary combined item and fluid storage host.
 *
 * @author howxu <dev@howxu.cn>
 */
public class CombinedPortBlockEntity extends IOPortBlockEntity {
    private static final long FLUID_CAPACITY = 256_000L;
    private static final long ITEM_CAPACITY = 64L;

    private final LongItemStorage itemStorage;
    private final LongFluidStorage fluidStorage;
    private final IOPortKind kind;
    private CapabilitySnapshot capabilitySnapshot;

    public CombinedPortBlockEntity(BlockPos pos, BlockState state) {
        this(pos, state, kindFromState(state, fallback(state)));
    }

    private CombinedPortBlockEntity(BlockPos pos, BlockState state, IOPortKind kind) {
        super(typeForKind(kind), pos, state);
        CombinedPortSize size = kind.combinedPortSize()
                .orElseThrow(() -> new IllegalStateException("Combined port missing size: " + kind.id()));
        this.kind = kind;
        this.itemStorage = new LongItemStorage(size.itemTypes(), ITEM_CAPACITY, this::markStorageChanged);
        this.fluidStorage = new LongFluidStorage(size.fluidTypes(), FLUID_CAPACITY, this::markStorageChanged);
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
    public LongFluidStorage fluidHandler(Direction side) {
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
                    .toList(), List.of(new FluidPersistenceFacet()));
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
        capabilitySnapshot().facets(PersistenceFacet.class).forEach(facet -> {
            CompoundTag state = new CompoundTag();
            facet.save(state, registries);
            output.put(facet.stateKey(), state);
        });
    }

    @Override
    protected void loadAdditional(CompoundTag input, HolderLookup.Provider registries) {
        beginLoadingAdditional();
        try {
            super.loadAdditional(input, registries);
            loadItems(input, registries);
            capabilitySnapshot().facets(PersistenceFacet.class)
                    .forEach(facet -> facet.load(input.getCompound(facet.stateKey()), registries));
        } finally {
            endLoadingAdditional();
        }
    }

    private void saveFluids(CompoundTag output, HolderLookup.Provider registries) {
        for (int slot = 0; slot < fluidStorage.size(); slot++) {
            String suffix = "_" + slot;
            FluidStack resource = fluidStorage.resource(slot);
            output.putBoolean("tankHasFluid" + suffix, !resource.isEmpty());
            if (!resource.isEmpty()) {
                output.put("tankFluid" + suffix, resource.saveOptional(registries));
                output.putLong("tankAmount" + suffix, fluidStorage.amount(slot));
            }
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

    private final class FluidPersistenceFacet implements PersistenceFacet {
        @Override
        public String stateKey() {
            return "fluid";
        }

        @Override
        public void save(CompoundTag output, HolderLookup.Provider registries) {
            saveFluids(output, registries);
        }

        @Override
        public void load(CompoundTag input, HolderLookup.Provider registries) {
            loadFluids(input, registries);
        }
    }

    private static IOPortKind fallback(BlockState state) {
        if (state.getBlock() instanceof IOPortBlock port && port.kind().ioType() == IOType.OUTPUT) {
            return PortKinds.COMBINED_OUTPUT;
        }
        return PortKinds.COMBINED_INPUT;
    }
}
