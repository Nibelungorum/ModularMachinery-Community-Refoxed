package org.nibelungorum.client;

import cn.howxu.mmcr.publicapi.client.ui.ControllerUiOpenContext;
import cn.howxu.mmcr.publicapi.client.ui.ControllerUiSession;
import cn.howxu.mmcr.publicapi.client.ui.UiSubscription;
import cn.howxu.mmcr.publicapi.event.RegisterControllerUisEvent;
import cn.howxu.mmcr.publicapi.ui.ControllerUiSnapshot;
import cn.howxu.mmcr.publicapi.ui.UiResult;
import icyllis.modernui.R;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.graphics.drawable.StateListDrawable;
import icyllis.modernui.mc.MenuScreen;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.util.ColorStateList;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import org.nibelungorum.builtin.MODERN_UI;

import java.util.Optional;
import java.util.concurrent.Executor;

/** Complete Modern UI replacement for the development storage demo.
 * @author howxu <dev@howxu.cn>
 */
@EventBusSubscriber(value = Dist.CLIENT)
public final class ModernUiControllerUi {
    private ModernUiControllerUi() {
    }

    @SubscribeEvent
    public static void register(RegisterControllerUisEvent event) {
        if (ModList.get().isLoaded("modernui")) {
            event.register(MODERN_UI.MACHINE_ID, ModernUiControllerUi::create);
        }
    }

    private static Screen create(ControllerUiOpenContext context) {
        // The factory runs on Minecraft's thread; slot writes must stay here.
        context.session().slots().setPlayerInventoryVisible(false);
        return new StorageScreen(new StorageFragment(context), context);
    }

    /** Keeps the exact opening menu while all labels come from the Fragment.
     * @author howxu <dev@howxu.cn>
     */
    private static final class StorageScreen extends MenuScreen<AbstractContainerMenu> {
        private final ControllerUiSession session;

        private StorageScreen(Fragment fragment, ControllerUiOpenContext context) {
            super(fragment, null, context.menu(), context.inventory(), context.title());
            session = context.session();
        }

        @Override
        protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        }

        @Override
        public void onClose() {
            session.close();
        }
    }

    /** Widget state and every callback below belong to Modern UI's UI thread.
     * @author howxu <dev@howxu.cn>
     */
    public static final class StorageFragment extends Fragment {
        private static final Executor UI_THREAD = MuiModApi::postToUiThread;
        private static final int PANEL = 0xFF182430;
        private static final int SURFACE = 0xFF101B26;
        private static final int BORDER = 0xFF30475A;
        private static final int TEXT = 0xFFF1F6FA;
        private static final int MUTED = 0xFF94A9BA;
        private static final int ACCENT = 0xFF66D8C8;
        private static final int WARNING = 0xFFE9BD74;
        private static final int ERROR = 0xFFF19494;
        private final ControllerUiSession session;
        private final Component openingTitle;
        private UiSubscription snapshotSubscription;
        private UiSubscription closeSubscription;
        private TextView title;
        private TextView status;
        private EditText input;
        private Button apply;
        private TextView value;
        private TextView controllerText;
        private TextView feedback;
        private String draft = "";
        private long generation;
        private boolean ready;
        private boolean pending;

        private StorageFragment(ControllerUiOpenContext context) {
            session = context.session();
            openingTitle = context.title();
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, DataSet savedInstanceState) {
            var scroll = new ScrollView(requireContext());
            scroll.setFillViewport(true);
            var root = new LinearLayout(requireContext());
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER);
            int outerPadding = root.dp(16);
            root.setPadding(outerPadding, outerPadding, outerPadding, outerPadding);
            var content = new LinearLayout(requireContext());
            content.setOrientation(LinearLayout.VERTICAL);
            int padding = content.dp(22);
            content.setPadding(padding, padding, padding, padding);
            content.setBackground(surface(content, PANEL, BORDER));

            var header = new LinearLayout(requireContext());
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            var heading = new LinearLayout(requireContext());
            heading.setOrientation(LinearLayout.VERTICAL);
            title = label(openingTitle.getString(), 20, TEXT);
            heading.addView(title);
            addRow(heading, label(text("subtitle"), 12, MUTED), 4);
            header.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            status = label(text("status_loading"), 11, WARNING);
            status.setGravity(Gravity.CENTER);
            status.setPadding(status.dp(10), status.dp(6), status.dp(10), status.dp(6));
            status.setBackground(surface(status, SURFACE, BORDER));
            var statusParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            statusParams.setMarginStart(header.dp(12));
            header.addView(status, statusParams);
            content.addView(header);

            var valueCard = new LinearLayout(requireContext());
            valueCard.setOrientation(LinearLayout.VERTICAL);
            int cardPadding = valueCard.dp(14);
            valueCard.setPadding(cardPadding, cardPadding, cardPadding, cardPadding);
            valueCard.setBackground(surface(valueCard, SURFACE, BORDER));
            valueCard.addView(label(text("stored_label"), 11, MUTED));
            value = label(text("empty_value"), 36, TEXT);
            addRow(valueCard, value, 4);
            addRow(valueCard, label(text("key_hint", MODERN_UI.VALUE_KEY), 11, ACCENT), 2);
            addRow(content, valueCard, 18);

            addRow(content, label(text("input_label"), 13, TEXT), 16);
            var editor = new LinearLayout(requireContext());
            editor.setOrientation(LinearLayout.HORIZONTAL);
            editor.setGravity(Gravity.CENTER_VERTICAL);

            input = new EditText(requireContext());
            input.setSingleLine();
            input.setTextSize(16);
            input.setTextColor(TEXT);
            input.setHintTextColor(MUTED);
            input.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            input.setPadding(input.dp(12), 0, input.dp(12), 0);
            var inputBackground = new StateListDrawable();
            inputBackground.addState(new int[]{R.attr.state_focused}, surface(input, SURFACE, ACCENT));
            inputBackground.addState(new int[]{}, surface(input, SURFACE, BORDER));
            input.setBackground(inputBackground);
            input.setHint(text("input_hint"));
            input.setText(draft);
            editor.addView(input, new LinearLayout.LayoutParams(0, editor.dp(44), 1));

            apply = new Button(requireContext());
            apply.setTextSize(14);
            apply.setGravity(Gravity.CENTER);
            apply.setPadding(apply.dp(10), 0, apply.dp(10), 0);
            apply.setTextColor(new ColorStateList(new int[][]{new int[]{R.attr.state_enabled}, new int[]{}},
                    new int[]{SURFACE, MUTED}));
            var buttonBackground = new StateListDrawable();
            buttonBackground.addState(new int[]{-R.attr.state_enabled}, surface(apply, 0xFF263749, BORDER));
            buttonBackground.addState(new int[]{R.attr.state_pressed}, surface(apply, 0xFF49BAAA, ACCENT));
            buttonBackground.addState(new int[]{R.attr.state_hovered}, surface(apply, 0xFF85E3D4, ACCENT));
            buttonBackground.addState(new int[]{}, surface(apply, ACCENT, ACCENT));
            apply.setBackground(buttonBackground);
            apply.setText(text("apply"));
            apply.setEnabled(false);
            apply.setOnClickListener(button -> submit());
            var buttonParams = new LinearLayout.LayoutParams(editor.dp(104), editor.dp(44));
            buttonParams.setMarginStart(editor.dp(12));
            editor.addView(apply, buttonParams);
            addRow(content, editor, 8);
            addRow(content, label(text("input_help"), 11, MUTED), 6);

            addRow(content, label(text("context_label"), 13, TEXT), 16);
            controllerText = label(text("waiting_text"), 13, ACCENT);
            int contextPadding = controllerText.dp(12);
            controllerText.setPadding(contextPadding, contextPadding, contextPadding, contextPadding);
            controllerText.setBackground(surface(controllerText, SURFACE, BORDER));
            addRow(content, controllerText, 8);
            feedback = label(text("feedback_hint"), 12, MUTED);
            feedback.setMinHeight(feedback.dp(30));
            feedback.setGravity(Gravity.CENTER_VERTICAL);
            addRow(content, feedback, 12);
            root.addView(content, new LinearLayout.LayoutParams(root.dp(480), ViewGroup.LayoutParams.WRAP_CONTENT));
            scroll.addView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return scroll;
        }

        @Override
        public void onViewCreated(View view, DataSet savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            ready = false;
            pending = false;
            long currentGeneration = ++generation;
            snapshotSubscription = session.subscribe(UI_THREAD, snapshot -> {
                if (generation == currentGeneration) updateSnapshot(snapshot);
            });
            closeSubscription = session.onClosed(UI_THREAD, () -> {
                if (generation != currentGeneration) return;
                ready = false;
                apply.setEnabled(false);
                status.setText(text("status_closed"));
                status.setTextColor(WARNING);
                feedback.setTextColor(WARNING);
                feedback.setText(text("closed"));
            });
        }

        private void updateSnapshot(ControllerUiSnapshot snapshot) {
            title.setText(snapshot.machineName().getString());
            ready = session.isOpen() && snapshot.ready() && snapshot.formed() && snapshot.hasDataStorage()
                    && session.supports(MODERN_UI.SET_VALUE);
            status.setText(text(!session.isOpen() ? "status_closed"
                    : !snapshot.ready() ? "status_loading"
                    : !snapshot.formed() ? "status_unformed"
                    : !snapshot.hasDataStorage() ? "status_no_storage"
                    : ready ? "status_ready" : "status_unsupported"));
            status.setTextColor(ready ? ACCENT : WARNING);
            apply.setEnabled(ready && !pending);
            if (snapshot.hasDataStorage()) {
                int stored = Optional.ofNullable(snapshot.dataStorageValues().get(MODERN_UI.VALUE_KEY))
                        .flatMap(key -> key.asInt()).orElse(0);
                value.setText(Integer.toString(stored));
            } else {
                value.setText(text("empty_value"));
            }
            controllerText.setText(snapshot.lines().stream()
                    .filter(line -> line.id().equals(MODERN_UI.VALUE_LINE))
                    .map(line -> line.text().getString()).findFirst().orElseGet(() -> text("waiting_text")));
        }

        private void submit() {
            if (!ready || pending) return;
            final int number;
            try {
                number = Integer.parseInt(input.getText().toString().trim());
            } catch (NumberFormatException invalid) {
                feedback.setTextColor(WARNING);
                feedback.setText(text("invalid_number"));
                return;
            }
            pending = true;
            apply.setEnabled(false);
            apply.setText(text("applying"));
            feedback.setTextColor(MUTED);
            feedback.setText(text("applying"));
            long currentGeneration = generation;
            session.request(MODERN_UI.SET_VALUE, new MODERN_UI.SetValue(number)).whenCompleteAsync((result, failure) -> {
                if (generation != currentGeneration) return;
                pending = false;
                apply.setText(text("apply"));
                apply.setEnabled(ready && session.isOpen());
                if (failure != null) {
                    feedback.setTextColor(ERROR);
                    feedback.setText(text("failed"));
                } else {
                    // ACK is feedback only; both displayed values come from the server snapshot.
                    feedback.setTextColor(result.status() == UiResult.Status.SUCCESS ? ACCENT : ERROR);
                    feedback.setText(result.message().map(Component::getString).orElseGet(() -> text(switch (result.status()) {
                        case SUCCESS -> "applied";
                        case CLOSED -> "closed";
                        case TIMEOUT -> "timeout";
                        case BUSY -> "busy";
                        default -> "failed";
                    })));
                }
            }, UI_THREAD);
        }

        @Override
        public void onDestroyView() {
            generation++;
            draft = input.getText().toString();
            if (snapshotSubscription != null) snapshotSubscription.close();
            if (closeSubscription != null) closeSubscription.close();
            snapshotSubscription = null;
            closeSubscription = null;
            title = null;
            status = null;
            input = null;
            apply = null;
            value = null;
            controllerText = null;
            feedback = null;
            super.onDestroyView();
        }

        private TextView label(String value, float size, int color) {
            var label = new TextView(requireContext());
            label.setText(value);
            label.setTextSize(size);
            label.setTextColor(color);
            return label;
        }

        private static void addRow(LinearLayout parent, View child, int topMargin) {
            var params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = parent.dp(topMargin);
            parent.addView(child, params);
        }

        private static ShapeDrawable surface(View view, int color, int border) {
            var surface = new ShapeDrawable();
            surface.setColor(color);
            surface.setCornerRadius(view.dp(10));
            surface.setStroke(view.dp(1), border);
            return surface;
        }

        private static String text(String key, Object... arguments) {
            return Component.translatable("gui.mmcr_test.modern_ui." + key, arguments).getString();
        }
    }
}
