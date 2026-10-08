package cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile;

/**
 * Remembers an interface menu page for the lifetime of its host without depending on ExtendedAE.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface InterfaceMenuPageHost {
    int getInterfaceMenuPage();

    void setInterfaceMenuPage(int page);
}
