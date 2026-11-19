package cn.howxu.mmcr.api.port;

/** Local isolation policy of a material input port, independent of any one controller.
 * @author howxu <dev@howxu.cn>
 */
public interface InputIsolationHost {
    boolean supportsInputIsolation();
    boolean isInputIsolated();
    void setInputIsolated(boolean enabled);
}
