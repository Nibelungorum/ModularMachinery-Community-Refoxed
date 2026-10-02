package cn.howxu.mmcr.compat.ars_nouveau.loaded;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.util.IOType;
import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.hollingsworth.arsnouveau.api.source.ISpecialSourceProvider;
import com.hollingsworth.arsnouveau.api.source.SourceManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Source interface with one real store and stable native capability/provider views.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class SourcePortBlockEntity extends IOPortBlockEntity {
    private final IOPortKind kind;
    private final SourcePortStorage storage;
    private final DirectionalSourceHandler externalHandler;
    private final LegacySourceTile legacySource;
    private final ISpecialSourceProvider provider = new ISpecialSourceProvider() {
        @Override
        public boolean isValid() {
            return level != null && !level.isClientSide() && !isRemoved()
                    && level.hasChunkAt(worldPosition)
                    && level.getBlockEntity(worldPosition) == SourcePortBlockEntity.this;
        }

        @Override
        public ISourceTile getSource() {
            return legacySource;
        }

        @Override
        public BlockPos getCurrentPos() {
            return worldPosition;
        }
    };
    private CapabilitySnapshot capabilitySnapshot;

    public SourcePortBlockEntity(BlockPos pos, BlockState state, IOPortKind kind) {
        super(ModBlockEntities.BES.get(kind.id()).get(), pos, state);
        this.kind = kind;
        storage = new SourcePortStorage(this::markSourceChanged);
        externalHandler = new DirectionalSourceHandler(storage, kind.ioType());
        legacySource = new LegacySourceTile(storage, kind.ioType());
    }

    public SourcePortStorage storage() {
        return storage;
    }

    public DirectionalSourceHandler externalHandler() {
        return externalHandler;
    }

    @Override
    public IOType ioType() {
        return kind.ioType();
    }

    @Override
    public IOPortKind kind() {
        return kind;
    }

    @Override
    public CapabilitySnapshot capabilitySnapshot() {
        if (capabilitySnapshot == null) {
            capabilitySnapshot = new CapabilitySnapshot(kind.definition().bindings().stream()
                    .map(this::createCapability).toList());
        }
        return capabilitySnapshot;
    }

    private void markSourceChanged() {
        markAutoIOCacheDirty();
        notifyStorageChanged();
        notifyControllerOfInputChange();
    }

    private void registerProvider() {
        if (level != null && !level.isClientSide()) SourceManager.INSTANCE.addInterface(level, provider);
    }

    private void unregisterProvider() {
        if (level != null && !level.isClientSide()) SourceManager.INSTANCE.getSetForLevel(level).remove(provider);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        initializeAvailabilityBaseline();
        registerProvider();
    }

    @Override
    public void onChunkUnloaded() {
        unregisterProvider();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        unregisterProvider();
        super.setRemoved();
    }

    @Override
    public void onBlockRemoved() {
        unregisterProvider();
        super.onBlockRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag output, HolderLookup.Provider registries) {
        super.saveAdditional(output, registries);
        CompoundTag source = new CompoundTag();
        source.putInt("amount", storage.amount());
        output.put("source", source);
    }

    @Override
    protected void loadAdditional(CompoundTag input, HolderLookup.Provider registries) {
        beginLoadingAdditional();
        try {
            super.loadAdditional(input, registries);
            storage.setAmount(input.getCompound("source").getInt("amount"));
        } finally {
            endLoadingAdditional();
            initializeAvailabilityBaseline();
        }
    }
}
