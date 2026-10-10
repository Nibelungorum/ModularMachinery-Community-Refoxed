package cn.howxu.mmcr.client.controller.ui;

import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration.RequestType;
import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration.Result;
import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration.Status;
import cn.howxu.mmcr.internal.network.ui.ControllerUiPayloadCodec;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.LongSupplier;

/** Main-thread-only bounded request IDs and monotonic client waiting deadlines.
 * @author howxu <dev@howxu.cn> */
public final class PendingUiRequests {
    private static final int LIMIT = 32;
    private static final long TIMEOUT_NANOS = Duration.ofSeconds(10).toNanos();
    private final LongSupplier nanos;
    private final Map<Long, Entry<?>> entries = new LinkedHashMap<>();
    private long nextId = 1;
    private boolean closed;

    public PendingUiRequests(LongSupplier nanos) { this.nanos = Objects.requireNonNull(nanos, "nanos"); }

    public <Q, R> Entry<R> add(RequestType<Q, R> type) {
        Objects.requireNonNull(type, "type");
        expire();
        var entry = new Entry<R>(closed || entries.size() >= LIMIT ? 0 : nextId++, nanos.getAsLong(), type);
        if (closed) entry.complete(status(Status.CLOSED));
        else if (entry.requestId() == 0) entry.complete(status(Status.BUSY));
        else entries.put(entry.requestId(), entry);
        return entry;
    }

    public void expire() {
        long now = nanos.getAsLong();
        var expired = new ArrayList<Entry<?>>();
        var iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            Entry<?> entry = iterator.next();
            if (now - entry.started >= TIMEOUT_NANOS) {
                iterator.remove();
                expired.add(entry);
            }
        }
        for (Entry<?> entry : expired) entry.complete(status(Status.TIMEOUT));
    }

    public void respond(long requestId, Status status, Optional<Component> reason, byte[] body, RegistryAccess registries) {
        expire();
        Entry<?> entry = entries.remove(requestId);
        if (entry != null) entry.respond(status, reason, body, registries);
    }

    public void fail(long requestId, Status status) {
        Entry<?> entry = entries.remove(requestId);
        if (entry != null) entry.complete(status(status));
    }

    public void close() {
        if (closed) return;
        closed = true;
        var waiting = entries.values().toArray(Entry<?>[]::new);
        entries.clear();
        for (Entry<?> entry : waiting) entry.complete(status(Status.CLOSED));
    }

    static <R> Result<R> status(Status status) {
        return new Result<>(status, Optional.empty(), Optional.empty());
    }

    /** A typed response decoder and an unmodifiable view of its completion.
     * @author howxu <dev@howxu.cn> */
    public static final class Entry<R> {
        private final long requestId;
        private final long started;
        private final RequestType<?, R> type;
        private final CompletableFuture<Result<R>> future = new CompletableFuture<>();

        private Entry(long requestId, long started, RequestType<?, R> type) {
            this.requestId = requestId;
            this.started = started;
            this.type = type;
        }
        public long requestId() { return requestId; }
        public CompletionStage<Result<R>> result() { return future.minimalCompletionStage(); }
        private void complete(Result<R> result) { future.complete(result); }
        private void respond(Status status, Optional<Component> reason, byte[] body, RegistryAccess registries) {
            try {
                if (status == Status.SUCCESS) {
                    R value = ControllerUiPayloadCodec.decodeExact(type.responseCodec(), body, registries,
                            ControllerUiPayloadCodec.RESPONSE_LIMIT);
                    complete(new Result<>(status, Optional.of(value), reason));
                } else {
                    complete(new Result<>(status, Optional.empty(), reason));
                }
            } catch (RuntimeException failure) {
                complete(status(Status.INVALID_REQUEST));
            }
        }
    }
}
