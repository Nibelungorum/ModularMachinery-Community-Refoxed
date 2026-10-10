package cn.howxu.mmcr.publicapi.client.ui;

import cn.howxu.mmcr.publicapi.ui.ControllerUiSnapshot;
import cn.howxu.mmcr.publicapi.ui.UiRequestType;
import cn.howxu.mmcr.publicapi.ui.UiResult;
import cn.howxu.mmcr.publicapi.ui.UiStateType;
import org.jetbrains.annotations.ApiStatus;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** One menu opening. Reads and requests may run on any UI thread; pure-data codecs
 * freeze request bodies before returning. Results complete on the client main thread.
 * Subscriptions are serial per subscriber and supply independently owned values.
 * @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface ControllerUiSession {
    UUID id();
    boolean isOpen();
    ControllerUiSnapshot snapshot();
    ControllerUiSlots slots();
    boolean supports(UiRequestType<?, ?> type);
    boolean supports(UiStateType<?> type);
    <Q, R> CompletionStage<UiResult<R>> request(UiRequestType<Q, R> type, Q body);
    <Q, R> CompletionStage<UiResult<R>> request(UiRequestType<Q, R> type, String laneId, Q body);
    UiSubscription subscribe(Executor executor, Consumer<ControllerUiSnapshot> listener);
    <T> UiSubscription subscribe(UiStateType<T> type, Executor executor, Consumer<T> listener);
    <T> Optional<T> state(UiStateType<T> type);
    UiSubscription onClosed(Executor executor, Runnable listener);
    void close();
}
