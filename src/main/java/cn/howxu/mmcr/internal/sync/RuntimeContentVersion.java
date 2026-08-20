package cn.howxu.mmcr.internal.sync;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Single monotonic version source for server-authoritative runtime content.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RuntimeContentVersion {
    private static final AtomicLong CURRENT = new AtomicLong();

    private RuntimeContentVersion() {
    }

    public static long current() {
        return CURRENT.get();
    }

    public static long advance() {
        return CURRENT.incrementAndGet();
    }
}
