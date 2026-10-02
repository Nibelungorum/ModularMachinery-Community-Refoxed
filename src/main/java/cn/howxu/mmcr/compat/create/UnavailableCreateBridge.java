package cn.howxu.mmcr.compat.create;

/** Absent-mod bridge: declarations remain decodable but cannot execute.
 * @author howxu <dev@howxu.cn>
 */
public final class UnavailableCreateBridge implements CreateBridge {
    public static final UnavailableCreateBridge INSTANCE = new UnavailableCreateBridge();

    private UnavailableCreateBridge() {
    }

    @Override
    public boolean available() {
        return false;
    }
}
