package cn.howxu.mmcr.publicapi;

import cn.howxu.mmcr.internal.api.facade.machine.MachineAdapters;
import cn.howxu.mmcr.api.registration.ApiRuntime;
import cn.howxu.mmcr.publicapi.machine.MachineDraft;
import net.minecraft.resources.ResourceLocation;

/** Machine declaration entry point. @author howxu <dev@howxu.cn> */
public final class Machines {
    private Machines() {}

    /** Returns whether startup registration is accepting machine definitions. */
    public static boolean isRegistrationOpen() {
        return ApiRuntime.isRegistrationOpen();
    }

    public static MachineDraft machine(ResourceLocation id) {
        return MachineAdapters.machine(id);
    }
}
