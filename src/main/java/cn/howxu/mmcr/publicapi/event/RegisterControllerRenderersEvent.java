package cn.howxu.mmcr.publicapi.event;

import cn.howxu.mmcr.internal.api.facade.client.ClientRegistrationAdapters;
import cn.howxu.mmcr.publicapi.client.render.ControllerRenderer;
import cn.howxu.mmcr.publicapi.client.render.RendererRegistrar;
import java.util.Collection;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

/** Client-only Java renderer event dispatched to all mod buses during native renderer registration.
 * @author howxu <dev@howxu.cn>
 */
public final class RegisterControllerRenderersEvent extends Event implements IModBusEvent {
    private final RendererRegistrar registrar;
    public RegisterControllerRenderersEvent(Collection<Identifier> machineIds) {
        registrar = ClientRegistrationAdapters.renderers(machineIds);
    }
    public RendererRegistrar registrar() { return registrar; }
    public void register(Identifier id, ControllerRenderer renderer) { registrar.register(id, renderer); }
    public Map<Identifier, ControllerRenderer> renderers() { return registrar.renderers(); }
}
