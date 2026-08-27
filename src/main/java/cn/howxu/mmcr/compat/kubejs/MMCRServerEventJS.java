package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextRegistry;
import dev.latvian.mods.kubejs.event.KubeEvent;

import java.util.function.Consumer;

/**
 * Server-script MMCR declarations collected during resource reload.
 * The API is available as {@code event.getAPI()} in KubeJS.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MMCRServerEventJS implements KubeEvent {
    private final KubeJSApi api = new KubeJSApi();

    public KubeJSApi getAPI() {
        return api;
    }

    public void registerControllerScreenText(String machineId, Consumer<ControllerScreenTextEventJS> handler) {
        ControllerScreenTextRegistry.registerServerScript(
                ControllerScreenTextEventJS.parseIdentifier(machineId, "machineId"),
                ControllerScreenTextEventJS.handler(handler));
    }

    public MachineStructureBuilderJS createStructure(String id) {
        return new MachineStructureBuilderJS(id);
    }

}
