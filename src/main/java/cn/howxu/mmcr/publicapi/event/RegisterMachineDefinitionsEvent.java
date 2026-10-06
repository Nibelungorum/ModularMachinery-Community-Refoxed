package cn.howxu.mmcr.publicapi.event;

import cn.howxu.mmcr.internal.api.facade.registration.RegistrationAdapters;
import cn.howxu.mmcr.publicapi.machine.MachineDraft;
import cn.howxu.mmcr.publicapi.machine.MachineSpec;
import cn.howxu.mmcr.publicapi.registration.MachineRegistrar;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;

/** Startup definition event posted on NeoForge.EVENT_BUS, before controller registration.
 * @author howxu <dev@howxu.cn>
 */
public final class RegisterMachineDefinitionsEvent extends Event {
    private final MachineRegistrar registrar;
    public RegisterMachineDefinitionsEvent() { this(RegistrationAdapters.definitions()); }
    public RegisterMachineDefinitionsEvent(MachineRegistrar registrar) {
        this.registrar = Objects.requireNonNull(registrar, "registrar");
    }
    public MachineRegistrar registrar() { return registrar; }
    public void registerMachine(ResourceLocation id, Consumer<MachineDraft> configuration) {
        registrar.registerMachine(id, configuration);
    }
    public void registerMachine(MachineSpec definition) { registrar.registerMachine(definition); }
    public Map<ResourceLocation, MachineSpec> definitions() { return registrar.definitions(); }
}
