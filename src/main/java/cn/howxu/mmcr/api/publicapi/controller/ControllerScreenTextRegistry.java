package cn.howxu.mmcr.api.publicapi.controller;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.api.PublicApiBootstrap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Iterator;
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
    private static final List<Entry> PENDING_SERVER_SCRIPT_HANDLERS = new ArrayList<>();
    private static boolean serverScriptReloading;

    private ControllerScreenTextRegistry() {
    }

    public static synchronized Registration register(Identifier machineId,
                                                     ControllerScreenTextHandler handler) {
        requireMutationThread(Source.PUBLIC_API, "register");
        return add(machineId, handler, Source.PUBLIC_API, false);
    }

    /**
     * Internal source entry point for server-script integrations.
     */
    static synchronized Registration registerServerScript(Identifier machineId,
                                                           ControllerScreenTextHandler handler) {
        requireMutationThread(Source.SERVER_SCRIPT, "server-script register");
        return add(machineId, handler, Source.SERVER_SCRIPT, serverScriptReloading);
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

    public static synchronized void beginServerScriptReload() {
        requireServerThread("begin server-script reload");
        beginServerScriptReloadInternal();
    }

    /** Internal reload hook used while KubeJS evaluates scripts off the server thread. */
    public static synchronized void beginServerScriptReloadFromReloadHook() {
        beginServerScriptReloadInternal();
    }

    private static void beginServerScriptReloadInternal() {
        for (Entry entry : List.copyOf(PENDING_SERVER_SCRIPT_HANDLERS)) entry.unregisterInternal();
        PENDING_SERVER_SCRIPT_HANDLERS.clear();
        serverScriptReloading = true;
    }

    public static synchronized void endServerScriptReload() {
        requireServerThread("end server-script reload");
        endServerScriptReloadInternal();
    }

    /** Internal reload hook; the caller dispatches this operation to the server thread. */
    public static synchronized void endServerScriptReloadFromReloadHook() {
        endServerScriptReloadInternal();
    }

    private static void endServerScriptReloadInternal() {
        if (!serverScriptReloading) return;
        removeServerScriptHandlers();
        for (Entry entry : PENDING_SERVER_SCRIPT_HANDLERS) {
            HANDLERS.computeIfAbsent(entry.machineId(), ignored -> new ArrayList<>()).add(entry);
        }
        PENDING_SERVER_SCRIPT_HANDLERS.clear();
        serverScriptReloading = false;
    }

    public static synchronized void abortServerScriptReload() {
        requireServerThread("abort server-script reload");
        abortServerScriptReloadInternal();
    }

    /** Internal reload hook; the caller dispatches this operation to the server thread. */
    public static synchronized void abortServerScriptReloadFromReloadHook() {
        abortServerScriptReloadInternal();
    }

    private static void abortServerScriptReloadInternal() {
        if (!serverScriptReloading) return;
        for (Entry entry : List.copyOf(PENDING_SERVER_SCRIPT_HANDLERS)) entry.unregisterInternal();
        PENDING_SERVER_SCRIPT_HANDLERS.clear();
        serverScriptReloading = false;
    }

    /** Test-only helper. Never call from production code. */
    static synchronized void clearForTesting() {
        HANDLERS.values().stream().flatMap(List::stream).toList().forEach(Entry::deactivateInternal);
        List.copyOf(PENDING_SERVER_SCRIPT_HANDLERS).forEach(Entry::deactivateInternal);
        HANDLERS.clear();
        PENDING_SERVER_SCRIPT_HANDLERS.clear();
        serverScriptReloading = false;
    }

    private static Registration add(Identifier machineId,
                                    ControllerScreenTextHandler handler, Source source,
                                    boolean pendingServerScript) {
        Objects.requireNonNull(machineId, "machineId");
        Objects.requireNonNull(handler, "handler");
        Entry entry = new Entry(machineId, handler, source);
        if (pendingServerScript) {
            PENDING_SERVER_SCRIPT_HANDLERS.add(entry);
        } else {
            HANDLERS.computeIfAbsent(machineId, ignored -> new ArrayList<>()).add(entry);
        }
        return entry;
    }

    private static void removeServerScriptHandlers() {
        Iterator<Map.Entry<Identifier, List<Entry>>> iterator = HANDLERS.entrySet().iterator();
        while (iterator.hasNext()) {
            List<Entry> entries = iterator.next().getValue();
            entries.removeIf(entry -> {
                if (entry.source() != Source.SERVER_SCRIPT) return false;
                entry.deactivateInternal();
                return true;
            });
            if (entries.isEmpty()) iterator.remove();
        }
    }

    private static void requireMutationThread(Source source, String operation) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            requireServerThread(server, operation);
            return;
        }
        if (source == Source.PUBLIC_API && PublicApiBootstrap.isRegistrationOpen()) return;
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

    private enum Source {
        PUBLIC_API,
        SERVER_SCRIPT
    }

    private static final class Entry implements Registration {
        private final Identifier machineId;
        private final ControllerScreenTextHandler handler;
        private final Source source;
        private boolean registered = true;

        private Entry(Identifier machineId, ControllerScreenTextHandler handler, Source source) {
            this.machineId = machineId;
            this.handler = handler;
            this.source = source;
        }

        private Identifier machineId() {
            return machineId;
        }

        private ControllerScreenTextHandler handler() {
            return handler;
        }

        private Source source() {
            return source;
        }

        @Override
        public void unregister() {
            synchronized (ControllerScreenTextRegistry.class) {
                if (!registered) return;
                requireMutationThread(source, "unregister");
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
            PENDING_SERVER_SCRIPT_HANDLERS.remove(this);
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
