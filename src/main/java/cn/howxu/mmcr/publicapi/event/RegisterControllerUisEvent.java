package cn.howxu.mmcr.publicapi.event;

import cn.howxu.mmcr.internal.api.facade.client.UiClientAdapters;
import cn.howxu.mmcr.publicapi.client.ui.ControllerUiFactory;
import java.util.Collection;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;
import org.jetbrains.annotations.ApiStatus;

/** Client-only mod-bus event dispatched during native menu screen registration.
 * Register one complete UI factory per known physical machine ID in this callback.
 * The runtime freezes the registrar in finally, including when a listener fails;
 * retaining the event does not permit later registration. Factories run on the client
 * main thread and must return a Screen implementing MenuAccess for the exact opening menu.
 * @author howxu <dev@howxu.cn>
 */
@OnlyIn(Dist.CLIENT)
public final class RegisterControllerUisEvent extends Event implements IModBusEvent {
    private final Registrar registrar;

    public RegisterControllerUisEvent(Collection<Identifier> machineIds) {
        registrar = UiClientAdapters.registration(machineIds);
    }

    public Registrar registrar() { return registrar; }

    /** Rejects unknown IDs, duplicates and registrations after the callback window closes. */
    public void register(Identifier machineId, ControllerUiFactory factory) {
        registrar.register(machineId, factory);
    }

    /** Runtime-provided client registration handle; only valid during event dispatch.
     * Rejections use RegistrationException and never replace an earlier factory.
     * @author howxu <dev@howxu.cn>
     */
    @OnlyIn(Dist.CLIENT)
    @ApiStatus.NonExtendable
    public interface Registrar {
        void register(Identifier machineId, ControllerUiFactory factory);
    }
}
