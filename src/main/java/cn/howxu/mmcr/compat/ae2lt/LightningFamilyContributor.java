package cn.howxu.mmcr.compat.ae2lt;

import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2FamilyContributor;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2ResourceFamily;
import java.util.function.Consumer;

/** Loaded by the AE2 integration; only the selected bridge may touch native lightning keys.
 * @author howxu <dev@howxu.cn>
 */
public final class LightningFamilyContributor implements AE2FamilyContributor {
    @Override public void contribute(Consumer<AE2ResourceFamily<?>> register) {
        LightningBridge.get().resourceFamilies().forEach(register);
    }
}
