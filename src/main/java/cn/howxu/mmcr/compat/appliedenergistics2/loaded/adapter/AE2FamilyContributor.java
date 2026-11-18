package cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter;

import java.util.function.Consumer;

/** Startup service loaded before any ME port snapshots its bindings. Providers must guard optional dependencies.
 * @author howxu <dev@howxu.cn>
 */
public interface AE2FamilyContributor {
    void contribute(Consumer<AE2ResourceFamily<?>> register);
}
