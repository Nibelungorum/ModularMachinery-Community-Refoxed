package cn.howxu.mmcr.publicapi.ui;

import cn.howxu.mmcr.internal.api.facade.ui.UiProtocolAdapters;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.ApiStatus;

/** MMCR-produced request outcome; rejection messages are defensively copied.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface UiResult<R> {
    /** Handler and transport outcomes.
     * @author howxu <dev@howxu.cn>
     */
    enum Status {
        SUCCESS, REJECTED, UNSUPPORTED, VERSION_MISMATCH, INVALID_REQUEST,
        CLOSED, TIMEOUT, BUSY, HANDLER_FAILED
    }

    static <R> UiResult<R> success(R value) { return UiProtocolAdapters.success(value); }
    static <R> UiResult<R> reject(Component reason) { return UiProtocolAdapters.reject(reason); }
    Status status();
    Optional<R> value();
    Optional<Component> message();
}
