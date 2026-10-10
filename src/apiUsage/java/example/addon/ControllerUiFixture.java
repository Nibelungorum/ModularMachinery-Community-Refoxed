package example.addon;

import cn.howxu.mmcr.publicapi.client.ui.ControllerUiOpenContext;
import cn.howxu.mmcr.publicapi.client.ui.ControllerUiSession;
import cn.howxu.mmcr.publicapi.client.ui.UiSubscription;
import cn.howxu.mmcr.publicapi.data.DataKey;
import cn.howxu.mmcr.publicapi.data.DataStore;
import cn.howxu.mmcr.publicapi.event.RegisterControllerUiProtocolsEvent;
import cn.howxu.mmcr.publicapi.event.RegisterControllerUisEvent;
import cn.howxu.mmcr.publicapi.ui.UiRequestType;
import cn.howxu.mmcr.publicapi.ui.UiResult;
import cn.howxu.mmcr.publicapi.ui.UiServerContext;
import cn.howxu.mmcr.publicapi.ui.UiStateProvider;
import cn.howxu.mmcr.publicapi.ui.UiStateType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** API-JAR-only addon example; the addon registers its machine and attaches these two listeners.
 * @author howxu <dev@howxu.cn> */
public final class ControllerUiFixture {
    public static final ResourceLocation MACHINE_ID = ResourceLocation.parse("example:controller");
    public static final UiRequestType<SetMode, SetMode> SET_MODE = UiRequestType.of(
            ResourceLocation.parse("example:set_mode"), 1, SetMode.CODEC, SetMode.CODEC);
    public static final UiStateType<ModeState> MODE_STATE = UiStateType.of(
            ResourceLocation.parse("example:mode_state"), 1, ModeState.CODEC);

    private ControllerUiFixture() {}

    /** Pure request codec: never reads Minecraft, players or worlds.
     * @author howxu <dev@howxu.cn> */
    public record SetMode(int mode) {
        public static final StreamCodec<RegistryFriendlyByteBuf, SetMode> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SetMode::mode, SetMode::new);
    }

    /** Mode -1 explicitly denotes unavailable storage.
     * @author howxu <dev@howxu.cn> */
    public record ModeState(long revision, int mode) {
        public static final StreamCodec<RegistryFriendlyByteBuf, ModeState> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, ModeState::revision,
                ByteBufCodecs.VAR_INT, ModeState::mode, ModeState::new);
    }

    public static void registerProtocols(RegisterControllerUiProtocolsEvent event) {
        event.registrar().request(MACHINE_ID, SET_MODE, (context, request) -> {
            DataStore storage = context.machine().dataStorage();
            if (storage == null || request.mode() < 0 || request.mode() > 2) {
                return UiResult.reject(Component.translatable("gui.mmcr.ui.rejected"));
            }
            storage.set("mode", DataKey.of(request.mode()));
            storage.set("mode_revision", DataKey.of(storage.get("mode_revision")
                    .flatMap(DataKey::asLong).orElse(0L) + 1L));
            return UiResult.success(request);
        });
        event.registrar().state(MACHINE_ID, MODE_STATE, new ModeProvider());
    }

    public static void registerUis(RegisterControllerUisEvent event) {
        event.register(MACHINE_ID, ModeScreen::new);
    }

    /** Both callbacks read the same real storage; no retained server callback context.
     * @author howxu <dev@howxu.cn> */
    private static final class ModeProvider implements UiStateProvider<ModeState> {
        @Override public long revision(UiServerContext context) {
            DataStore storage = context.machine().dataStorage();
            return storage == null ? 0L : storage.get("mode_revision").flatMap(DataKey::asLong).orElse(0L);
        }
        @Override public ModeState snapshot(UiServerContext context) {
            DataStore storage = context.machine().dataStorage();
            return new ModeState(revision(context), storage == null ? -1 : storage.get("mode")
                    .flatMap(DataKey::asInt).orElse(0));
        }
    }

    /** Complete native Screen replacement preserving the original opening menu.
     * @author howxu <dev@howxu.cn> */
    public static final class ModeScreen extends Screen implements MenuAccess<AbstractContainerMenu> {
        private final AbstractContainerMenu menu;
        private final ControllerUiSession session;
        private UiSubscription snapshotSubscription;
        private UiSubscription modeSubscription;
        private UiSubscription closeSubscription;
        private Component machineName;
        private Component mode = Component.translatable("gui.mmcr.ui.loading");
        private Component feedback = Component.empty();
        private Button modeButton;
        private boolean removed;
        private long generation;

        public ModeScreen(ControllerUiOpenContext context) {
            super(context.title());
            menu = context.menu();
            session = context.session();
            machineName = context.title();
        }

        @Override public AbstractContainerMenu getMenu() { return menu; }

        @Override protected void init() {
            releaseSubscriptions();
            removed = false;
            long currentGeneration = ++generation;
            // Native init runs on the Minecraft thread after container installation.
            session.slots().setPlayerInventoryVisible(false);
            modeButton = addRenderableWidget(Button.builder(Component.translatable("gui.mmcr.ui.set_mode"), button ->
                    session.request(SET_MODE, new SetMode(1)).thenAcceptAsync(result -> {
                        if (!removed && currentGeneration == generation) {
                            // ACK is feedback only: mode is exclusively updated by its state subscription.
                            feedback = result.message().orElseGet(() -> Component.translatable(
                                    "gui.mmcr.ui." + switch (result.status()) {
                                        case SUCCESS -> "applied";
                                        case REJECTED -> "rejected";
                                        case UNSUPPORTED -> "unsupported";
                                        case VERSION_MISMATCH -> "version_mismatch";
                                        case INVALID_REQUEST -> "invalid_request";
                                        case CLOSED -> "closed";
                                        case TIMEOUT -> "timeout";
                                        case BUSY -> "busy";
                                        case HANDLER_FAILED -> "handler_failed";
                                    }));
                        }
                    }, minecraft)).bounds(Math.max(0, (width - 120) / 2), Math.max(0, height / 2), 120, 20).build());
            modeButton.active = false;
            snapshotSubscription = session.subscribe(minecraft, snapshot -> {
                if (removed || generation != currentGeneration) return;
                machineName = snapshot.machineName();
                modeButton.active = snapshot.ready() && snapshot.hasDataStorage() && session.supports(SET_MODE);
            });
            modeSubscription = session.subscribe(MODE_STATE, minecraft, state -> {
                if (!removed && generation == currentGeneration) {
                    mode = Component.translatable(state.mode() < 0 ? "gui.mmcr.ui.unavailable" : "gui.mmcr.ui.mode", state.mode());
                }
            });
            closeSubscription = session.onClosed(minecraft, () -> {
                if (!removed && generation == currentGeneration) {
                    modeButton.active = false;
                    feedback = Component.translatable("gui.mmcr.ui.closed");
                }
            });
        }

        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.render(graphics, mouseX, mouseY, partialTick);
            graphics.drawString(font, machineName, 16, 16, 0xFFFFFFFF);
            graphics.drawString(font, mode, 16, 32, 0xFFFFFFFF);
            graphics.drawString(font, feedback, 16, 48, 0xFFFFFFFF);
        }

        @Override public boolean isPauseScreen() { return false; }
        @Override public void onClose() { session.close(); }
        @Override public void removed() {
            removed = true;
            generation++;
            releaseSubscriptions();
            super.removed();
        }
        private void releaseSubscriptions() {
            if (snapshotSubscription != null) snapshotSubscription.close();
            if (modeSubscription != null) modeSubscription.close();
            if (closeSubscription != null) closeSubscription.close();
            snapshotSubscription = null;
            modeSubscription = null;
            closeSubscription = null;
        }
    }
}
