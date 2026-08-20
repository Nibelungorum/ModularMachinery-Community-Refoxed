package cn.howxu.mmcr.api.machine.level;

import java.util.Collection;

/** Internal lifecycle bridge used by the structure registration event and tests.
 * @author howxu <dev@howxu.cn>
 */
public final class MachineLevelRegistryBridge {
    private MachineLevelRegistryBridge() {
    }

    public static void beginRegistration() {
        MachineLevelRegistry.beginRegistration();
    }

    public static void freezeRegistration() {
        MachineLevelRegistry.freezeRegistration();
    }

    public static void install(Collection<LevelType> types, Collection<MachineLevel> levels) {
        MachineLevelRegistry.install(types, levels);
    }

    public static void registerType(LevelType type) {
        MachineLevelRegistry.registerType(type);
    }

    public static void registerLevel(MachineLevel level) {
        MachineLevelRegistry.registerLevel(level);
    }
}
