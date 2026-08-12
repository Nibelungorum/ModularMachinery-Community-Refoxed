package cn.howxu.mmcr.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;

/**
 * KubeJS server events emitted by smart interface mutations.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface SmartInterfaceEvents {
    String UPDATED_ID = "mmcr.smart_interface.updated";

    static EventGroup group() {
        EventGroup group = Holder.GROUP;
        Registration.COMPLETE = true;
        return group;
    }

    static void post(SmartInterfaceUpdateEventJS event) {
        if (Registration.COMPLETE && Holder.UPDATED.hasListeners()) {
            Holder.UPDATED.post(event);
        }
    }

    final class Registration {
        private static boolean COMPLETE;

        private Registration() {
        }
    }

    final class Holder {
        private static final EventGroup GROUP = EventGroup.of("mmcr.smart_interface");
        private static final dev.latvian.mods.kubejs.event.EventHandler UPDATED = GROUP.server("updated",
                () -> SmartInterfaceUpdateEventJS.class);

        private Holder() {
        }
    }
}
