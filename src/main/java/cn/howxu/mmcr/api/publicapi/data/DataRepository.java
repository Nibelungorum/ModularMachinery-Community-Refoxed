package cn.howxu.mmcr.api.publicapi.data;

import net.minecraft.resources.ResourceLocation;

/**
 * Public extension point for future lazy data repositories.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface DataRepository {
    ResourceLocation id();

    DataRepositoryRequest request(DataRepositoryContext context);
}
