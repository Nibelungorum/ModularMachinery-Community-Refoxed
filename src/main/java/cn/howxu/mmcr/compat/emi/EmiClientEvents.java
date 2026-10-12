package cn.howxu.mmcr.compat.emi;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Explicitly registered during Client construction, behind the optional EMI gate.
 * @author howxu <dev@howxu.cn>
 */
public final class EmiClientEvents {
    private static boolean registered;

    private EmiClientEvents() {}

    public static synchronized void register(IEventBus modBus) {
        if (registered) return;
        registered = true;
        modBus.addListener(EmiClientEvents::registerReloadListener);
        NeoForge.EVENT_BUS.addListener(EmiClientEvents::tick);
        NeoForge.EVENT_BUS.addListener(EmiClientEvents::endFrame);
        NeoForge.EVENT_BUS.addListener(EmiClientEvents::logout);
    }

    private static void tick(ClientTickEvent.Post event) {
        EmiRuntimeReloader.tick();
        if (EmiRuntimeReloader.isLoaded() && Minecraft.getInstance().screen instanceof EmiInputRouter.Host host) {
            host.mmcrInputRouter().tick();
        }
    }

    private static void endFrame(RenderFrameEvent.Post event) { EmiPreviewSessions.instance().endFrame(); }

    private static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        onRenderThread(() -> {
            if (Minecraft.getInstance().screen instanceof EmiInputRouter.Host host) host.mmcrInputRouter().close();
            EmiPreviewSessions.instance().invalidate();
        });
    }

    private static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new SimplePreparableReloadListener<Void>() {
            @Override protected Void prepare(ResourceManager resources, ProfilerFiller profiler) { return null; }
            @Override protected void apply(Void prepared, ResourceManager resources, ProfilerFiller profiler) {
                onRenderThread(() -> {
                    if (Minecraft.getInstance().screen instanceof EmiRuntimeReloader.ScreenHost host) host.mmcrInvalidateRecipes();
                    EmiPreviewSessions.instance().invalidate();
                    if (EmiRuntimeReloader.isLoaded() && Minecraft.getInstance().screen instanceof EmiRuntimeReloader.ScreenHost host) {
                        host.mmcrRefreshRecipes();
                    }
                });
            }
        });
    }

    static void onRenderThread(Runnable action) {
        if (RenderSystem.isOnRenderThread()) action.run();
        else RenderSystem.recordRenderCall(action::run);
    }
}
