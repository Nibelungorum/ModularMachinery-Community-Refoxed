package cn.howxu.mmcr.compat.emi;

import cn.howxu.mmcr.internal.client.RecipeViewerSnapshot.Version;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/** @author howxu <dev@howxu.cn> */
class EmiPreviewSessionsTest {
    private static final ResourceLocation STRUCTURE = ResourceLocation.parse("mmcr:test");
    private static final Version VERSION = new Version(1, 2, 3, 4);

    @Test void aHundredHoverFramesBorrowOneResourceAndUnusedFrameClosesIt() {
        EmiPreviewSessions sessions = new EmiPreviewSessions();
        Object host = new Object();
        AtomicInteger creates = new AtomicInteger(), closes = new AtomicInteger();
        Supplier<AutoCloseable> factory = () -> { creates.incrementAndGet(); return closes::incrementAndGet; };
        for (int frame = 0; frame < 100; frame++) {
            sessions.hover(host, STRUCTURE, VERSION, factory);
            sessions.endFrame();
        }
        assertEquals(1, creates.get());
        assertEquals(0, closes.get());
        sessions.endFrame();
        sessions.invalidate();
        assertEquals(1, closes.get());
    }

    @Test void pagesHaveIndependentViewsAndWholeVersionTupleInvalidatesHover() {
        EmiPreviewSessions sessions = new EmiPreviewSessions();
        AtomicInteger closes = new AtomicInteger();
        var first = sessions.page(new Object(), STRUCTURE, VERSION, () -> (AutoCloseable) closes::incrementAndGet);
        var second = sessions.page(new Object(), STRUCTURE, VERSION, () -> (AutoCloseable) closes::incrementAndGet);
        assertNotSame(first.resource(), second.resource());
        Object host = new Object();
        var hover = sessions.hover(host, STRUCTURE, VERSION, () -> (AutoCloseable) closes::incrementAndGet);
        Version changed = new Version(1, 2, 3, 5);
        var replacement = sessions.hover(host, STRUCTURE, changed, () -> (AutoCloseable) closes::incrementAndGet);
        assertNotSame(hover, replacement);
        assertFalse(hover.current(VERSION));
        assertFalse(replacement.current(VERSION));
        sessions.invalidate();
        sessions.invalidate();
        assertEquals(4, closes.get());
    }

    @Test void deferredDisposalImmediatelyRejectsLateCompilation() {
        List<Runnable> queued = new ArrayList<>();
        EmiPreviewSessions sessions = new EmiPreviewSessions(queued::add);
        AtomicInteger closes = new AtomicInteger(), installations = new AtomicInteger();
        var session = sessions.page(new Object(), STRUCTURE, VERSION, () -> (AutoCloseable) closes::incrementAndGet);
        sessions.invalidate();
        if (session.current(VERSION)) installations.incrementAndGet();
        assertEquals(0, installations.get());
        assertEquals(0, closes.get());
        queued.forEach(Runnable::run);
        assertEquals(1, closes.get());
    }

    @Test void aNewHostOrAnyChangedVersionComponentNeverBorrowsAnObsoleteSession() {
        EmiPreviewSessions sessions = new EmiPreviewSessions();
        AtomicInteger creates = new AtomicInteger(), closes = new AtomicInteger();
        Supplier<AutoCloseable> factory = () -> { creates.incrementAndGet(); return closes::incrementAndGet; };
        Object host = new Object();
        Version[] versions = { VERSION, new Version(2, 2, 3, 4), new Version(2, 3, 3, 4),
                new Version(2, 3, 4, 4), new Version(2, 3, 4, 5) };
        EmiPreviewSessions.Session<AutoCloseable> previous = null;
        for (Version version : versions) {
            var next = sessions.hover(host, STRUCTURE, version, factory);
            if (previous != null) {
                assertNotSame(previous, next);
                assertFalse(previous.current(version));
            }
            previous = next;
        }
        sessions.hover(new Object(), STRUCTURE, versions[4], factory);
        assertEquals(6, creates.get());
        assertEquals(5, closes.get());
        sessions.invalidate();
        assertEquals(6, closes.get());
    }

    @Test void screenshotCallbackOwnsIndependentResourceAndRestoresScopeAfterException() {
        EmiPreviewSessions sessions = new EmiPreviewSessions();
        AtomicInteger closes = new AtomicInteger();
        Object host = new Object();
        var page = sessions.page(host, STRUCTURE, VERSION, () -> (AutoCloseable) closes::incrementAndGet);
        try (var outer = EmiRenderScope.hover(host)) {
            Runnable callback = EmiRenderScope.screenshot(() -> {
                assertTrue(EmiRenderScope.current().isScreenshot());
                var shot = EmiRenderScope.current().acquire(sessions, STRUCTURE, VERSION,
                        () -> (AutoCloseable) closes::incrementAndGet);
                assertNotSame(page.resource(), shot.resource());
                throw new IllegalStateException("test");
            });
            assertSame(outer, EmiRenderScope.current());
            assertThrows(IllegalStateException.class, callback::run);
            assertSame(outer, EmiRenderScope.current());
            assertEquals(1, closes.get());
            assertTrue(page.current(VERSION));
        }
        assertNull(EmiRenderScope.current());
        sessions.invalidate();
        assertEquals(2, closes.get());
    }

    @Test void failedTemporaryCleanupStillClosesOtherResourcesAndRestoresContext() {
        EmiPreviewSessions sessions = new EmiPreviewSessions();
        AtomicInteger closes = new AtomicInteger();
        PoseStack pose = new PoseStack();
        var entry = pose.last();
        var scope = EmiRenderScope.screenshot();
        scope.acquire(sessions, STRUCTURE, VERSION, () -> (AutoCloseable) closes::incrementAndGet);
        scope.acquire(sessions, STRUCTURE, VERSION, () -> (AutoCloseable) () -> {
            throw new IllegalStateException("cleanup");
        });
        scope.preservePose(pose);
        pose.pushPose();
        pose.translate(10, 20, 0);
        assertThrows(IllegalStateException.class, scope::close);
        assertNull(EmiRenderScope.current());
        assertEquals(1, closes.get());
        assertSame(entry, pose.last());
        assertTrue(pose.clear());
        scope.close();
        assertSame(entry, pose.last());
    }

    @Test void aSwallowedScreenshotRenderFailurePreventsPngReadAndClosesItsResources() {
        EmiPreviewSessions sessions = new EmiPreviewSessions();
        AtomicInteger closes = new AtomicInteger(), reads = new AtomicInteger(), renders = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("mesh preparation");
        Runnable callback = EmiRenderScope.screenshot(() -> {
            renders.incrementAndGet();
            EmiRenderScope.current().acquire(sessions, STRUCTURE, VERSION,
                    () -> (AutoCloseable) closes::incrementAndGet);
            // The actual EMI helper logs and swallows the exception, then returns normally.
            EmiRenderScope.current().failed(failure);
        });
        var thrown = assertThrows(IllegalStateException.class, () -> {
            callback.run();
            reads.incrementAndGet();
        });
        assertSame(failure, thrown.getCause());
        assertEquals(1, renders.get());
        assertEquals(0, reads.get());
        assertEquals(1, closes.get());
        assertNull(EmiRenderScope.current());
    }
}
