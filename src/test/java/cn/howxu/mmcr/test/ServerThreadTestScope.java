package cn.howxu.mmcr.test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.Proxy;
import com.mojang.authlib.GameProfile;
import net.minecraft.SystemReport;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.debugchart.SampleLogger;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Installs a server identity owned by the current test thread and restores the previous server on close.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ServerThreadTestScope implements AutoCloseable {
    private final Field currentServer;
    private final MinecraftServer previousServer;

    private ServerThreadTestScope(Field currentServer, MinecraftServer previousServer) {
        this.currentServer = currentServer;
        this.previousServer = previousServer;
    }

    public static ServerThreadTestScope open() throws ReflectiveOperationException {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        TestServer server = (TestServer) ((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(TestServer.class);
        server.serverThread = Thread.currentThread();
        Field currentServer = ServerLifecycleHooks.class.getDeclaredField("currentServer");
        currentServer.setAccessible(true);
        ServerThreadTestScope scope = new ServerThreadTestScope(currentServer,
                ServerLifecycleHooks.getCurrentServer());
        currentServer.set(null, server);
        return scope;
    }

    @Override
    public void close() throws IllegalAccessException {
        currentServer.set(null, previousServer);
    }

    /**
     * Minimal server identity matching AsyncRequirementPlannerTest's server-thread fixture.
     *
     * @author howxu <dev@howxu.cn>
     */
    private static final class TestServer extends MinecraftServer {
        private Thread serverThread;

        private TestServer() {
            super(null, null, null, null, Proxy.NO_PROXY, null, null, null);
        }

        @Override
        public Thread getRunningThread() {
            return serverThread;
        }

        @Override
        protected boolean initServer() throws IOException {
            return false;
        }

        @Override
        public int getOperatorUserPermissionLevel() {
            return 4;
        }

        @Override
        public int getFunctionCompilationLevel() {
            return 4;
        }

        @Override
        public boolean shouldRconBroadcast() {
            return false;
        }

        @Override
        public boolean isDedicatedServer() {
            return false;
        }

        @Override
        public int getRateLimitPacketsPerSecond() {
            return 0;
        }

        @Override
        public boolean isEpollEnabled() {
            return false;
        }

        @Override
        public boolean isCommandBlockEnabled() {
            return false;
        }

        @Override
        public boolean isPublished() {
            return false;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }

        @Override
        public boolean isSingleplayerOwner(GameProfile profile) {
            return false;
        }

        @Override
        protected SampleLogger getTickTimeLogger() {
            return null;
        }

        @Override
        public boolean isTickTimeLoggingEnabled() {
            return false;
        }

        @Override
        public int getMaxPlayers() {
            return 1;
        }

        @Override
        public SystemReport fillServerSystemReport(SystemReport report) {
            return report;
        }
    }
}
