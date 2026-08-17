package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.client.preview.StructurePreviewCompilationCache;
import java.util.ArrayList;
import java.util.List;

/**
 * Owns previews created by JEI recipe layouts until JEI replaces or closes its layout state.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class JeiPreviewLifecycle {
    private static final List<JeiStructurePreviewWidget> ACTIVE = new ArrayList<>();

    static synchronized void registerActive(JeiStructurePreviewWidget preview) {
        ACTIVE.add(preview);
    }

    public synchronized void register(JeiStructurePreviewWidget preview) {
        ACTIVE.add(preview);
    }

    public static synchronized void closeActive() {
        List<JeiStructurePreviewWidget> previews = List.copyOf(ACTIVE);
        ACTIVE.clear();
        previews.forEach(JeiStructurePreviewWidget::close);
        StructurePreviewCompilationCache.instance().clear();
    }

    public synchronized void closeAll() {
        closeActive();
    }
}
