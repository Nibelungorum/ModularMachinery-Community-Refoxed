package cn.howxu.mmcr.api.data;

import net.minecraft.resources.Identifier;

/** Public extension point for future lazy data repositories.
 *
 * <p>Repository discovery, transfer, and controller binding are intentionally
 * outside this contract. Current data-storage blocks remain independently
 * owned by one controller.</p>
 *
 * @author howxu <dev@howxu.cn>
 */
public interface DataRepository {
    Identifier id();

    DataRepositoryRequest request(DataRepositoryContext context);
}
