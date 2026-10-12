package cn.howxu.mmcr.compat.emi;

/** Local (widget-group) input dispatched by the EMI host.
 * @author howxu <dev@howxu.cn>
 */
public interface EmiInteractiveWidget extends AutoCloseable {
    boolean mousePressed(double x, double y, int button);
    boolean mouseDragged(double x, double y, int button, double dx, double dy);
    boolean mouseReleased(double x, double y, int button);
    boolean mouseScrolled(double x, double y, double horizontal, double vertical);
    default boolean visible() { return true; }
    default boolean capturesDrag(double x, double y, int button) { return true; }
    void tick();
    @Override void close();
}
