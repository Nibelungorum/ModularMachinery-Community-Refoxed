package cn.howxu.mmcr.compat.ars_nouveau.loaded;

import cn.howxu.mmcr.util.IOType;
import com.hollingsworth.arsnouveau.api.source.ISourceCap;

/**
 * Fixed-direction native capability view of a port's source storage.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class DirectionalSourceHandler implements ISourceCap {
    private final SourcePortStorage storage;
    private final IOType ioType;

    public DirectionalSourceHandler(SourcePortStorage storage, IOType ioType) {
        this.storage = storage;
        this.ioType = ioType;
    }

    @Override
    public int receiveSource(int amount, boolean simulate) {
        return ioType == IOType.INPUT ? (int) storage.move(amount, true, simulate) : 0;
    }

    @Override
    public int extractSource(int amount, boolean simulate) {
        return ioType == IOType.OUTPUT ? (int) storage.move(amount, false, simulate) : 0;
    }

    @Override
    public int getMaxReceive() {
        return ioType == IOType.INPUT ? storage.capacity() : 0;
    }

    @Override
    public int getMaxExtract() {
        return ioType == IOType.OUTPUT ? storage.capacity() : 0;
    }

    @Override
    public boolean canReceive() {
        return ioType == IOType.INPUT;
    }

    @Override
    public boolean canExtract() {
        return ioType == IOType.OUTPUT;
    }

    @Override
    public boolean canAcceptSource(int amount) {
        return receiveSource(amount, true) > 0;
    }

    @Override
    public boolean canProvideSource(int amount) {
        return extractSource(amount, true) > 0;
    }

    @Override
    public int getSource() {
        return storage.amount();
    }

    @Override
    public int getSourceCapacity() {
        return storage.capacity();
    }

    @Override
    public void setSource(int amount) {
        // External force setters must not bypass the fixed transfer direction.
    }

    @Override
    public void setMaxSource(int capacity) {
        // The host owns the storage capacity.
    }
}
