package cn.howxu.mmcr.compat.pneumaticcraft;

/** No native classes are resolved when PneumaticCraft is absent.
 * @author howxu <dev@howxu.cn>
 */
public final class UnavailablePneumaticCraftBridge implements PneumaticCraftBridge {
    public static final UnavailablePneumaticCraftBridge INSTANCE = new UnavailablePneumaticCraftBridge();

    private UnavailablePneumaticCraftBridge() {
    }

    @Override
    public boolean available() {
        return false;
    }
}
