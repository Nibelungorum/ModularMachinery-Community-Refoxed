package cn.howxu.mmcr.compat.pneumaticcraft.loaded;

import cn.howxu.mmcr.compat.pneumaticcraft.PneumaticCraftBridge;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.registry.ModBlockEntities;
import me.desht.pneumaticcraft.api.PNCCapabilities;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.util.List;

/** PNC-present registration without eagerly accessing content registries. @author howxu <dev@howxu.cn> */
public final class LoadedPneumaticCraftBridge implements PneumaticCraftBridge {
    @Override public boolean available() { return true; }
    @Override public List<IOPortKind> portKinds() { return List.of(AirInterfaceKind.INPUT, AirInterfaceKind.OUTPUT); }

    @Override
    public void registerPorts(IEventBus bus) {
        bus.addListener(this::registerCapabilities);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        for (IOPortKind kind : portKinds()) {
            event.registerBlockEntity(PNCCapabilities.AIR_HANDLER_MACHINE, ModBlockEntities.BES.get(kind.id()).get(),
                    (entity, side) -> ((AirPortBlockEntity) entity).airHandler());
        }
    }
}
