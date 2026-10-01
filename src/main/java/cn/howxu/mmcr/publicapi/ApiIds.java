package cn.howxu.mmcr.publicapi;

import cn.howxu.mmcr.MMCR;
import net.minecraft.resources.ResourceLocation;

/** Factory for MMCR-owned identifiers.
 * @author howxu <dev@howxu.cn>
 */
public final class ApiIds {
    private ApiIds() {}

    public static ResourceLocation id(String path) {
        return MMCR.id(path);
    }
}
