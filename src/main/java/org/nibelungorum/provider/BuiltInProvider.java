package org.nibelungorum.provider;

import cn.howxu.mmcr.api.publicapi.MachineDefinitionProvider;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;
import org.nibelungorum.builtin.BLAST_FURNACE;

/** Provides built-in machine definitions before dynamic controller registration.
 * @author howxu <dev@howxu.cn>
 */
public final class BuiltInProvider implements MachineDefinitionProvider {
    @Override
    public void register(MMCRMachineDefinationsEvent event) {
        BLAST_FURNACE.registerDefinitions(event);
    }
}
