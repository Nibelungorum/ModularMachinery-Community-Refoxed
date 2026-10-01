package cn.howxu.mmcr.publicapi.registration;

import cn.howxu.mmcr.publicapi.machine.MachineDraft;
import cn.howxu.mmcr.publicapi.machine.MachineSpec;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

/** MMCR-provided registration window; returned maps are read-only snapshots.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface MachineRegistrar {
    void registerMachine(ResourceLocation id, Consumer<MachineDraft> configuration);
    void registerMachine(MachineSpec definition);
    Map<ResourceLocation, MachineSpec> definitions();
}
