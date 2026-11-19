package cn.howxu.mmcr.api.port;

/** Local isolation policy of a physical material port, independent of its controller.
 * @author howxu <dev@howxu.cn>
 */
public interface InputIsolationHost {
    boolean supportsInputIsolation();
    boolean isInputIsolated();
    void setInputIsolated(boolean isolated);
}
