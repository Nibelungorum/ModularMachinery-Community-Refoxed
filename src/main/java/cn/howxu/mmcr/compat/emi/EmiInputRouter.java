package cn.howxu.mmcr.compat.emi;

import dev.emi.emi.api.widget.Bounds;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** Owns page input and a drag capture, including the owner's coordinate transform.
 * @author howxu <dev@howxu.cn>
 */
public final class EmiInputRouter implements AutoCloseable {
    private List<Binding> bindings = List.of();
    private Binding captured;
    private int capturedButton;
    private double lastX, lastY;

    public record Binding(EmiInteractiveWidget widget, double originX, double originY,
                          Supplier<Bounds> bounds) {
        boolean contains(double x, double y) {
            Bounds area = bounds.get();
            double localX = x - originX, localY = y - originY;
            return widget.visible() && localX >= area.x() && localY >= area.y()
                    && localX < area.x() + area.width() && localY < area.y() + area.height();
        }
    }

    public interface Host {
        EmiInputRouter mmcrInputRouter();
    }

    public void replace(List<Binding> next) {
        cancelCapture();
        Set<EmiInteractiveWidget> retained = identitySet();
        next.forEach(binding -> retained.add(binding.widget()));
        Set<EmiInteractiveWidget> closed = identitySet();
        for (Binding binding : bindings) {
            if (!retained.contains(binding.widget()) && closed.add(binding.widget())) binding.widget().close();
        }
        bindings = List.copyOf(next);
    }

    public boolean mousePressed(double x, double y, int button) {
        cancelCapture();
        for (Binding binding : bindings) {
            if (!binding.contains(x, y)) continue;
            double lx = x - binding.originX(), ly = y - binding.originY();
            if (!binding.widget().mousePressed(lx, ly, button)) continue;
            if (binding.widget().capturesDrag(lx, ly, button)) {
                captured = binding;
                capturedButton = button;
                lastX = lx;
                lastY = ly;
            }
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (captured == null || button != capturedButton) return false;
        if (!captured.widget().visible()) { cancelCapture(); return false; }
        lastX = x - captured.originX();
        lastY = y - captured.originY();
        captured.widget().mouseDragged(lastX, lastY, button, dx, dy);
        return true;
    }

    public boolean mouseReleased(double x, double y, int button) {
        if (captured == null || button != capturedButton) return false;
        Binding owner = captured;
        captured = null;
        owner.widget().mouseReleased(x - owner.originX(), y - owner.originY(), button);
        return true;
    }

    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        for (Binding binding : bindings) {
            if (binding.contains(x, y) && binding.widget().mouseScrolled(x - binding.originX(),
                    y - binding.originY(), horizontal, vertical)) return true;
        }
        return false;
    }

    public void tick() {
        Set<EmiInteractiveWidget> ticked = identitySet();
        for (Binding binding : bindings) {
            if (binding.widget().visible() && ticked.add(binding.widget())) binding.widget().tick();
        }
        if (captured != null && !captured.widget().visible()) cancelCapture();
    }

    private void cancelCapture() {
        if (captured == null) return;
        Binding owner = captured;
        captured = null;
        owner.widget().mouseReleased(lastX, lastY, capturedButton);
    }

    private static Set<EmiInteractiveWidget> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    @Override public void close() { replace(List.of()); }
}
