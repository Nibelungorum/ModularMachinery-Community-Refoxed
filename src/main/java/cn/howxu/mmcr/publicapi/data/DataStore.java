package cn.howxu.mmcr.publicapi.data;

import org.jetbrains.annotations.ApiStatus;
import java.util.Map;
import java.util.Optional;

/** Live runtime storage, supplied by MMCR. Ordinary writes/removal are immediate.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface DataStore {
    Optional<DataKey> get(String key);
    boolean contains(String key);
    Map<String, DataKey> values();
    void set(String key, DataKey value);
    /** False means unchanged, not a failed transaction. */
    boolean set(String key, DataKey value, Transaction transaction);
    Optional<DataKey> remove(String key);

    /** Borrowed commit handle, valid only during the callback; supplied by MMCR.
     * @author howxu <dev@howxu.cn>
     */
    @ApiStatus.NonExtendable
    interface Transaction {
    }
}
