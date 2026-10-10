package cn.howxu.mmcr.publicapi.ui;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.ApiStatus;

/** MMCR-provided registration window, valid only during protocol event dispatch.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface UiProtocolRegistrar {
    <Q, R> void request(Identifier machineId, UiRequestType<Q, R> type, UiRequestHandler<Q, R> handler);
    <T> void state(Identifier machineId, UiStateType<T> type, UiStateProvider<T> provider);
}
