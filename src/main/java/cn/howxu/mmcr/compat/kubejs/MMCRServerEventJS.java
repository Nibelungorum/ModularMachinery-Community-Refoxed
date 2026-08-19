package cn.howxu.mmcr.compat.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;

/**
 * Server-script MMCR declarations collected during reload.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MMCRServerEventJS implements KubeEvent {
    private final KubeJSApi api = new KubeJSApi();

    public KubeJSApi getAPI() {
        return api;
    }

    public MachineStructureBuilderJS createStructure(String id) {
        return new MachineStructureBuilderJS(id);
    }

}
