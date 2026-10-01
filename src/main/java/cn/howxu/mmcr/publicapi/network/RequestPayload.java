package cn.howxu.mmcr.publicapi.network;

import cn.howxu.mmcr.internal.api.facade.network.NetworkAdapters;
import cn.howxu.mmcr.publicapi.data.DataKey;
import org.jetbrains.annotations.ApiStatus;
import java.util.Map;
import java.util.Optional;

/** Immutable MMCR-produced request data. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface RequestPayload {
    static RequestPayload of(Map<String, DataKey> values) { return NetworkAdapters.payload(values); }
    Map<String, DataKey> values();
    Optional<DataKey> get(String key);
}
