package cn.howxu.mmcr.compat.appliedenergistics2.loaded;

import cn.howxu.mmcr.api.compat.appliedenergistics2.BulkPatternProvider;
import org.jetbrains.annotations.Nullable;

/** Internal bridge to the actual host behind AE's registered provider logic.
 * @author HowXu {@literal <dev@howxu.cn>}
 */
public interface BulkPatternProviderSource {
    @Nullable BulkPatternProvider mmcr$bulkPatternProvider();
}
