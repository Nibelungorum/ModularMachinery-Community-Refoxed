package cn.howxu.mmcr.api.controller.ui.client;

import cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot;
import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration.RequestType;
import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration.Result;
import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration.StateType;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Client-only UI contracts and the machine-local factory registration window.
 * @author howxu <dev@howxu.cn>
 */
public final class ControllerUiRegistration {
    private final Set<Identifier> machineIds;
    private final Map<Identifier, Factory> factories = new LinkedHashMap<>();
    private boolean frozen;

    public ControllerUiRegistration(Collection<Identifier> machineIds) {
        this.machineIds = Set.copyOf(machineIds);
    }

    public void register(Identifier machineId, Factory factory) {
        Objects.requireNonNull(machineId, "machineId");
        Objects.requireNonNull(factory, "factory");
        if (frozen) throw new IllegalStateException("Controller UI registration is frozen");
        if (!machineIds.contains(machineId)) throw new IllegalStateException("Unknown machine: " + machineId);
        if (factories.putIfAbsent(machineId, factory) != null) {
            throw new IllegalStateException("Duplicate controller UI factory: " + machineId);
        }
    }

    public void freeze() { frozen = true; }
    public Optional<Factory> find(Identifier machineId) {
        return Optional.ofNullable(factories.get(Objects.requireNonNull(machineId, "machineId")));
    }

    /** Complete screen factory, invoked on the client main thread.
     * @author howxu <dev@howxu.cn> */
    @FunctionalInterface
    public interface Factory { Screen create(OpenContext context); }

    /** Native opening arguments; the returned screen must retain this exact menu.
     * @author howxu <dev@howxu.cn> */
    public interface OpenContext {
        AbstractContainerMenu menu();
        Inventory inventory();
        Component title();
        Session session();
    }

    /** Cross-thread reads and requests; codecs must only process pure data.
     * @author howxu <dev@howxu.cn> */
    public interface Session {
        UUID id();
        boolean isOpen();
        ControllerUiSnapshot snapshot();
        Slots slots();
        boolean supports(RequestType<?, ?> type);
        boolean supports(StateType<?> type);
        <Q, R> CompletionStage<Result<R>> request(RequestType<Q, R> type, Q body);
        <Q, R> CompletionStage<Result<R>> request(RequestType<Q, R> type, String laneId, Q body);
        Subscription subscribe(Executor executor, Consumer<ControllerUiSnapshot> listener);
        <T> Subscription subscribe(StateType<T> type, Executor executor, Consumer<T> listener);
        <T> Optional<T> state(StateType<T> type);
        Subscription onClosed(Executor executor, Runnable listener);
        void close();
    }

    /** Menu-local visibility; writes are main-thread-only.
     * @author howxu <dev@howxu.cn> */
    public interface Slots {
        boolean isPlayerInventoryVisible();
        void setPlayerInventoryVisible(boolean visible);
    }

    /** Cancels even callbacks already queued on an author's executor.
     * @author howxu <dev@howxu.cn> */
    public interface Subscription extends AutoCloseable { @Override void close(); }
}
