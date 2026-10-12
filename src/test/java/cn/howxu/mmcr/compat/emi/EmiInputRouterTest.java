package cn.howxu.mmcr.compat.emi;

import dev.emi.emi.api.widget.Bounds;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** @author howxu <dev@howxu.cn> */
class EmiInputRouterTest {
    @Test void convertsCoordinatesAndReleasesOutsideTheOwner() {
        EmiInputRouter router = new EmiInputRouter();
        FakeWidget widget = new FakeWidget();
        router.replace(List.of(binding(widget)));
        assertTrue(router.mousePressed(30, 40, 0));
        assertEquals(10, widget.x);
        assertEquals(10, widget.y);
        assertTrue(router.mouseDragged(300, 400, 0, 270, 360));
        assertEquals(280, widget.x);
        assertTrue(router.mouseReleased(300, 400, 0));
        assertEquals(370, widget.y);
        assertEquals(1, widget.releases);
        assertFalse(router.mouseReleased(300, 400, 0));
    }

    @Test void pageReplacementCancelsCaptureAndClosesExactlyOnce() {
        EmiInputRouter router = new EmiInputRouter();
        FakeWidget widget = new FakeWidget();
        router.replace(List.of(binding(widget), binding(widget)));
        router.mousePressed(30, 40, 0);
        router.replace(List.of());
        router.close();
        assertEquals(1, widget.releases);
        assertEquals(1, widget.closes);
        assertFalse(router.mouseDragged(30, 40, 0, 1, 1));
    }

    @Test void controlPressDoesNotCaptureAndHiddenPageDoesNotConsumeInput() {
        EmiInputRouter router = new EmiInputRouter();
        FakeWidget widget = new FakeWidget();
        widget.capture = false;
        router.replace(List.of(binding(widget)));
        assertTrue(router.mousePressed(30, 40, 0));
        assertFalse(router.mouseDragged(30, 40, 0, 1, 1));
        assertEquals(0, widget.drags);
        assertFalse(router.mouseScrolled(1, 1, 0, 1));
        widget.visible = false;
        assertFalse(router.mousePressed(30, 40, 0));
        assertFalse(router.mouseScrolled(30, 40, 0, 1));
        router.tick();
        assertEquals(0, widget.ticks);
    }

    private static EmiInputRouter.Binding binding(FakeWidget widget) {
        return new EmiInputRouter.Binding(widget, 20, 30, () -> new Bounds(0, 0, 100, 100));
    }

    private static final class FakeWidget implements EmiInteractiveWidget {
        double x, y;
        int drags, releases, closes, ticks;
        boolean capture = true, visible = true;
        public boolean mousePressed(double x, double y, int button) { this.x = x; this.y = y; return true; }
        public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
            this.x = x; this.y = y; drags++; return true;
        }
        public boolean mouseReleased(double x, double y, int button) {
            this.x = x; this.y = y; releases++; return true;
        }
        public boolean mouseScrolled(double x, double y, double horizontal, double vertical) { return true; }
        public boolean capturesDrag(double x, double y, int button) { return capture; }
        public boolean visible() { return visible; }
        public void tick() { ticks++; }
        public void close() { closes++; }
    }
}
