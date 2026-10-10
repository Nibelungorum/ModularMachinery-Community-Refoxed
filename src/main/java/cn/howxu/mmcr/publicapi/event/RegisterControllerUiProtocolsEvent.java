package cn.howxu.mmcr.publicapi.event;

import cn.howxu.mmcr.internal.api.facade.ui.UiProtocolAdapters;
import cn.howxu.mmcr.publicapi.ui.UiProtocolRegistrar;
import java.util.Collection;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

/** Mod-bus protocol event, collected on both sides during payload registration.
 * The runtime freezes its registrar after dispatch.
 * @author howxu <dev@howxu.cn>
 */
public final class RegisterControllerUiProtocolsEvent extends Event implements IModBusEvent {
    private final UiProtocolRegistrar registrar;

    public RegisterControllerUiProtocolsEvent(Collection<ResourceLocation> machineIds) {
        this.registrar = UiProtocolAdapters.registrar(machineIds);
    }

    public UiProtocolRegistrar registrar() { return registrar; }
}
