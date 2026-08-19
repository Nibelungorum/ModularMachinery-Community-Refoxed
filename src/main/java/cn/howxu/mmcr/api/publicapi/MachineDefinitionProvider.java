package cn.howxu.mmcr.api.publicapi;

/**
 * Startup extension point for declaring public machine and recipe definitions.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface MachineDefinitionProvider {
    void register();
}
