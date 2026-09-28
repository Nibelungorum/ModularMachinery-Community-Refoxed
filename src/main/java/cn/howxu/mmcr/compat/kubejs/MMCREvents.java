package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.internal.client.JeiWorkstationRegistry;
import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.script.ScriptType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * KubeJS event group for MMCR declaration scripts and client recipe information.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface MMCREvents {
    String STARTUP_ID = "mmcr.startup";
    String SERVER_ID = "mmcr.server";
    String CLIENT_ID = "mmcr.client";
    EventGroup GROUP = EventGroup.of("mmcr");

    static EventGroup group() {
        Holder.init();
        return GROUP;
    }

    static void postStartup() {
        MMCRStartupEventJS event = new MMCRStartupEventJS();
        Holder.STARTUP.post(ScriptType.STARTUP, event);
        JeiWorkstationRegistry.replaceKubeJS(event.jeiWorkstations());
    }

    static void postServer() {
        Holder.SERVER.post(ScriptType.SERVER, new MMCRServerEventJS());
    }

    static void postClient(RecipeInformationEventJS event) {
        Holder.CLIENT.post(ScriptType.CLIENT, event);
    }

    static Map<String, String> events() {
        Map<String, String> events = new LinkedHashMap<>();
        events.put(STARTUP_ID, STARTUP_ID);
        events.put(SERVER_ID, SERVER_ID);
        events.put(CLIENT_ID, CLIENT_ID);
        return events;
    }

    final class Holder {
        private static final EventHandler STARTUP = MMCREvents.GROUP.startup("startup",
                () -> MMCRStartupEventJS.class);
        private static final EventHandler SERVER = MMCREvents.GROUP.server("server",
                () -> MMCRServerEventJS.class);
        private static final EventHandler CLIENT = MMCREvents.GROUP.client("client",
                () -> RecipeInformationEventJS.class);

        private static void init() {
        }

        private Holder() {
        }
    }
}
