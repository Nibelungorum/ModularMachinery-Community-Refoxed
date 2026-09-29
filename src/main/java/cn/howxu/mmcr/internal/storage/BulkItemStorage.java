package cn.howxu.mmcr.internal.storage;

/**
 * Single-slot, long-backed native item handler.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class BulkItemStorage extends LongItemStorage {
    public BulkItemStorage(long capacity, Runnable onChange) {
        super(1, capacity, onChange);
    }
}
