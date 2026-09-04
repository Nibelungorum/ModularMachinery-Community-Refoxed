package cn.howxu.mmcr.internal.assembly;

/**
 * Item storage used by structure assembly and dismantling.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface StructureItemStorage {
    StructureItemSource source();

    StructureItemSink sink();
}
