package cn.howxu.mmcr.publicapi.data;

import net.minecraft.resources.ResourceLocation;

/** User-implementable repository. No automatic discovery or transfer is implied.
 * @author howxu <dev@howxu.cn>
 */
public interface Repository {
    ResourceLocation id();
    RepositoryRequest request(RepositoryContext context);
}
