package cn.howxu.mmcr.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.script.ScriptType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * KubeJS event group for MMCR declaration scripts.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface MMCREvents {
    String STARTUP_ID = "mmcr.startup";
    String SERVER_ID = "mmcr.server";
    EventGroup GROUP = EventGroup.of("mmcr");

    static EventGroup group() {
        Holder.init();
        return GROUP;
    }

    static void postStartup() {
        Holder.STARTUP.post(ScriptType.STARTUP, new MMCRStartupEventJS());
    }

    static void postServer() {
        Holder.SERVER.post(ScriptType.SERVER, new MMCRServerEventJS());
    }

    static Map<String, String> events() {
        Map<String, String> events = new LinkedHashMap<>();
        events.put(STARTUP_ID, STARTUP_ID);
        events.put(SERVER_ID, SERVER_ID);
        return events;
    }

    final class Holder {
        private static final dev.latvian.mods.kubejs.event.EventHandler STARTUP = MMCREvents.GROUP.startup("startup",
                () -> MMCRStartupEventJS.class);
        private static final dev.latvian.mods.kubejs.event.EventHandler SERVER = MMCREvents.GROUP.server("server",
                () -> MMCRServerEventJS.class);

        private static void init() {
        }

        private Holder() {
        }
    }
}
