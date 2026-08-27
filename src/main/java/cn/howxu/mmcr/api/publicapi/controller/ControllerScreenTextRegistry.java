package cn.howxu.mmcr.api.publicapi.controller;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.api.PublicApiBootstrap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Server-side registry for controller screen text handlers.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ControllerScreenTextRegistry {
    private static final Map<Identifier, List<Entry>> HANDLERS = new LinkedHashMap<>();

    private ControllerScreenTextRegistry() {
    }

    public static synchronized Registration register(Identifier machineId,
                                                     ControllerScreenTextHandler handler) {
        requireMutationThread("register");
        return add(machineId, handler);
    }

    public static void apply(ControllerRuntimeContext context) {
        Objects.requireNonNull(context, "context");
        List<Entry> handlers;
        synchronized (ControllerScreenTextRegistry.class) {
            handlers = List.copyOf(HANDLERS.getOrDefault(context.machineId(), List.of()));
        }
        for (Entry entry : handlers) {
            if (!entry.isRegistered()) continue;
            try {
                entry.handler().apply(context);
            } catch (RuntimeException exception) {
                MMCR.LOG.error("Controller screen text handler failed for machine " + context.machineId(), exception);
            }
        }
    }

    /** Test-only helper. Never call from production code. */
    static synchronized void clearForTesting() {
        HANDLERS.values().stream().flatMap(List::stream).toList().forEach(Entry::deactivateInternal);
        HANDLERS.clear();
    }

    private static Registration add(Identifier machineId, ControllerScreenTextHandler handler) {
        Objects.requireNonNull(machineId, "machineId");
        Objects.requireNonNull(handler, "handler");
        Entry entry = new Entry(machineId, handler);
        HANDLERS.computeIfAbsent(machineId, ignored -> new ArrayList<>()).add(entry);
        return entry;
    }

    private static void requireMutationThread(String operation) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            requireServerThread(server, operation);
            return;
        }
        if (PublicApiBootstrap.isRegistrationOpen()) return;
        throw new IllegalStateException(operation + " requires the server thread");
    }

    private static void requireServerThread(String operation) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) throw new IllegalStateException(operation + " requires the server thread");
        requireServerThread(server, operation);
    }

    private static void requireServerThread(MinecraftServer server, String operation) {
        if (!server.isSameThread()) {
            throw new IllegalStateException(operation + " must run on the server thread");
        }
    }

    public interface Registration {
        void unregister();
    }

    private static final class Entry implements Registration {
        private final Identifier machineId;
        private final ControllerScreenTextHandler handler;
        private boolean registered = true;

        private Entry(Identifier machineId, ControllerScreenTextHandler handler) {
            this.machineId = machineId;
            this.handler = handler;
        }

        private Identifier machineId() {
            return machineId;
        }

        private ControllerScreenTextHandler handler() {
            return handler;
        }

        @Override
        public void unregister() {
            synchronized (ControllerScreenTextRegistry.class) {
                if (!registered) return;
                requireMutationThread("unregister");
                unregisterInternal();
            }
        }

        private void unregisterInternal() {
            if (!registered) return;
            deactivateInternal();
            List<Entry> entries = HANDLERS.get(machineId);
            if (entries != null) {
                entries.remove(this);
                if (entries.isEmpty()) HANDLERS.remove(machineId);
            }
        }

        private void deactivateInternal() {
            registered = false;
        }

        private boolean isRegistered() {
            synchronized (ControllerScreenTextRegistry.class) {
                return registered;
            }
        }
    }
}
