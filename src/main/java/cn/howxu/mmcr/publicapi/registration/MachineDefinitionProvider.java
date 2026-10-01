package cn.howxu.mmcr.publicapi.registration;

import cn.howxu.mmcr.publicapi.event.RegisterMachineDefinitionsEvent;

/** Service-loaded startup extension, invoked before definition event subscribers.
 * @author howxu <dev@howxu.cn>
 */
@FunctionalInterface
public interface MachineDefinitionProvider {
    void register(RegisterMachineDefinitionsEvent event);
}
