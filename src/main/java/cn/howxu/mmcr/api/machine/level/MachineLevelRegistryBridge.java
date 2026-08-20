package cn.howxu.mmcr.api.machine.level;

import java.util.Collection;

/**
 * Deprecated compatibility bridge for the machine level registry lifecycle.
 *
 * @deprecated use the public snapshot API and lifecycle events instead; this
 * bridge remains only for existing API consumers.
 * @author howxu <dev@howxu.cn>
 */
@Deprecated
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
        MachineLevelRegistry.installSnapshot(types, levels);
    }

    public static void registerType(LevelType type) {
        MachineLevelRegistry.registerType(type);
    }

    public static void registerLevel(MachineLevel level) {
        MachineLevelRegistry.registerLevel(level);
    }
}
