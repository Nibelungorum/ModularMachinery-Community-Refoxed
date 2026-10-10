package cn.howxu.mmcr.internal.network.ui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration;
import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration.RequestRegistration;
import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration.ServerContext;
import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration.Status;
import cn.howxu.mmcr.internal.menu.ControllerUiMenu;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiServerSession;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Supplier;

/** Main-thread actual-menu authorization precedes every author decoder and handler.
 * @author howxu <dev@howxu.cn>
 */
public final class ControllerUiRequestDispatcher {
    private ControllerUiRequestDispatcher() { }

    public static void dispatch(ServerPlayer player, PktControllerUiRequestPayload request) {
        var server = player.level().getServer();
        if (!server.isSameThread()) {
            server.execute(() -> dispatch(player, request));
            return;
        }
        if (!(player.containerMenu instanceof ControllerUiMenu menu)) return;
        ControllerUiServerSession session = menu.uiServerSession();
        if (session == null || !session.validate(player, request.containerId(), request.sessionId())) return;
        Status gate = session.consumeRequest(request.requestId());
        PktControllerUiResponsePayload response;
        if (gate != null) {
            response = error(request, gate);
        } else {
            response = dispatchRegistered(session.protocols(), session.openData().machineId(), request,
                    () -> session.laneExists(request.laneId()),
                    () -> new ServerContext(player, session.owner().controllerUiBehaviorContext(session.owner().runtimeSnapshot()),
                            request.laneId()),
                    player.level().registryAccess());
        }
        // The handler can close/replace the menu. Its correlated result still belongs only to its original token.
        player.connection.send(new ClientboundCustomPayloadPacket(response));
        if (gate == null) session.broadcastChanges();
    }

    /** Resolves the already authorized session's typed definition before decoding any body. */
    static PktControllerUiResponsePayload dispatchRegistered(UiProtocolRegistration protocols, ResourceLocation machineId,
            PktControllerUiRequestPayload request, Supplier<Boolean> laneExists,
            Supplier<ServerContext> context, RegistryAccess registries) {
        var registration = protocols.request(machineId, request.messageId());
        if (registration.isEmpty()) return error(request, Status.UNSUPPORTED);
        if (registration.orElseThrow().type().version() != request.version()) return error(request, Status.VERSION_MISMATCH);
        if (!laneExists.get()) return error(request, Status.INVALID_REQUEST);
        return dispatchTyped(registration.orElseThrow(), machineId, request, context, registries);
    }

    private static <Q, R> PktControllerUiResponsePayload dispatchTyped(RequestRegistration<Q, R> registration,
            ResourceLocation machineId, PktControllerUiRequestPayload request, Supplier<ServerContext> context,
            RegistryAccess registries) {
        Q value;
        try {
            value = ControllerUiPayloadCodec.decodeExact(registration.type().requestCodec(), request.body(),
                    registries, ControllerUiPayloadCodec.REQUEST_LIMIT);
        } catch (RuntimeException exception) {
            return error(request, Status.INVALID_REQUEST);
        }
        try {
            var result = registration.handler().handle(context.get(), value);
            byte[] bytes = result.status() == Status.SUCCESS
                    ? ControllerUiPayloadCodec.encodeBounded(registration.type().responseCodec(), result.value().orElseThrow(),
                            registries, ControllerUiPayloadCodec.RESPONSE_LIMIT) : new byte[0];
            var response = new PktControllerUiResponsePayload(request.containerId(), request.sessionId(), request.requestId(),
                    result.status(), result.message(), bytes);
            ControllerUiPayloadCodec.encodeBounded(PktControllerUiResponsePayload.STREAM_CODEC, response,
                    registries, ControllerUiPayloadCodec.SNAPSHOT_LIMIT);
            return response;
        } catch (Exception exception) {
            MMCR.LOG.error("Controller UI handler failed machine={} message={} session={}",
                    machineId, request.messageId(), request.sessionId(), exception);
            return error(request, Status.HANDLER_FAILED);
        }
    }

    private static PktControllerUiResponsePayload error(PktControllerUiRequestPayload request, Status status) {
        String key = switch (status) {
            case UNSUPPORTED -> "unsupported";
            case VERSION_MISMATCH -> "version_mismatch";
            case BUSY -> "busy";
            case HANDLER_FAILED -> "handler_failed";
            default -> "invalid_request";
        };
        return new PktControllerUiResponsePayload(request.containerId(), request.sessionId(), request.requestId(),
                status, Optional.of(Component.translatable("gui.mmcr.controller.ui." + key)), new byte[0]);
    }

    /** Constant-space single-use monotonic IDs, including rejected/over-budget requests.
     * @author howxu <dev@howxu.cn>
     */
    public static final class RequestWindow {
        private long lastRequestId;
        private long budgetTick = Long.MIN_VALUE;
        private int requestCount;

        public @Nullable Status consume(long requestId, long gameTick) {
            if (requestId <= lastRequestId) return Status.INVALID_REQUEST;
            lastRequestId = requestId;
            if (budgetTick != gameTick) {
                budgetTick = gameTick;
                requestCount = 0;
            }
            if (requestCount >= 16) return Status.BUSY;
            requestCount++;
            return null;
        }
    }
}
