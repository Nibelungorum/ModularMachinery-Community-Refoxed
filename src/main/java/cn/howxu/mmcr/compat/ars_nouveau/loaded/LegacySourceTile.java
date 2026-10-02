package cn.howxu.mmcr.compat.ars_nouveau.loaded;

import cn.howxu.mmcr.util.IOType;
import com.hollingsworth.arsnouveau.api.source.ISourceTile;

/**
 * Transfer-only legacy projection, including Ars' extraction restoration path.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class LegacySourceTile implements ISourceTile {
    private final SourcePortStorage storage;
    private final IOType ioType;

    public LegacySourceTile(SourcePortStorage storage, IOType ioType) {
        this.storage = storage;
        this.ioType = ioType;
    }

    @Override
    public int getTransferRate() {
        return ioType == IOType.OUTPUT ? storage.capacity() : 0;
    }

    @Override
    public boolean canAcceptSource() {
        return ioType == IOType.INPUT && storage.amount() < storage.capacity();
    }

    @Override
    public boolean canProvideSource() {
        return ioType == IOType.OUTPUT && storage.amount() > 0;
    }

    @Override
    public int getSource() {
        return ioType == IOType.OUTPUT ? storage.amount() : 0;
    }

    @Override
    public int getMaxSource() {
        return ioType == IOType.OUTPUT ? storage.amount() : storage.capacity() - storage.amount();
    }

    @Override
    public int setSource(int amount) {
        return getSource();
    }

    @Override
    public int addSource(int amount) {
        // Ars restores a previous extraction through this overload, even on outputs.
        storage.move(amount, true, false);
        return getSource();
    }

    @Override
    public int addSource(int amount, boolean simulate) {
        return ioType == IOType.INPUT ? (int) storage.move(amount, true, simulate) : 0;
    }

    @Override
    public int removeSource(int amount) {
        if (ioType == IOType.OUTPUT) storage.move(amount, false, false);
        return getSource();
    }

    @Override
    public int removeSource(int amount, boolean simulate) {
        return ioType == IOType.OUTPUT ? (int) storage.move(amount, false, simulate) : 0;
    }
}
