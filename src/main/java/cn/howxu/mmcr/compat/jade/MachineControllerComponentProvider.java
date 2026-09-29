package cn.howxu.mmcr.compat.jade;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.client.gui.RecipePoolDisplayName;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.JadeUI;
import snownee.jade.api.view.ProgressView;

import java.util.ArrayList;
import java.util.List;

/**
 * @author howxu <dev@howxu.cn>
 */
public enum MachineControllerComponentProvider implements IComponentProvider<BlockAccessor> {
    INSTANCE;

    static final ResourceLocation UID = MMCR.id("machine_controller");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        Snapshot snapshot = Snapshot.from(accessor.getServerData());

        for (String key : lineKeys(snapshot)) {
            if ("progress".equals(key)) continue;
            tooltip.add(row(key, lineValue(snapshot, key)));
            if ("state".equals(key)) appendProgressBar(tooltip, snapshot);
        }
        if (snapshot.tickMachine()) appendProgressBar(tooltip, snapshot);
        for (Component line : JadeTextCodec.read(accessor.getServerData())) {
            tooltip.add(line);
        }
    }

    private static void appendProgressBar(ITooltip tooltip, Snapshot snapshot) {
        if (!snapshot.hasProgress()) return;
        int tick = snapshot.tick();
        int total = snapshot.totalTick();
        float ratio = Math.clamp(tick / (float) total, 0F, 1F);
        Component text = total < 20
                ? Component.translatable("jade.mmcr.machine_controller.progress.tick", tick, total)
                : Component.translatable("jade.mmcr.machine_controller.progress.sec",
                        Math.round(tick / 20F), Math.round(total / 20F));
        // JadeUI.progress(...) reaches into Jade client state (BoxStyle.nestedBox() calls
        // IThemeHelper.get().theme(); JadeUI.progress() reads Minecraft.getInstance().font via
        // DisplayHelper; JadeFont reaches Font.provider via a field made public by Jade's
        // accesstransformer). All of those are null/missing in the unit-test JVM, so this whole
        // block throws there. In production with a live client everything is present, so the
        // catch never fires. Scoped narrowly to the bar so a failing render in tests does not
        // strand the rest of the tooltip.
        try {
            ProgressView view = new ProgressView(
                    ProgressView.Part.of(ratio, 0xFF4CBB17),
                    text,
                    JadeUI.progressStyle(),
                    BoxStyle.nestedBox());
            tooltip.add(JadeUI.progress(view));
        } catch (NullPointerException | IllegalAccessError ignored) {
            // graceful degradation; bar is unobservable in this environment
        }
    }

    static List<String> lineKeys(Snapshot snapshot) {
        if (snapshot.tickMachine()) return List.of("structure");

        List<String> keys = new ArrayList<>();
        keys.add("structure");
        keys.add("state");
        if (snapshot.recipePoolId() != null) keys.add("recipe_pool");
        if (!snapshot.hasFactoryController() && snapshot.hasProgress()) keys.add("progress");
        if (snapshot.shouldShowParallelSlots()) keys.add("parallel_slots");
        if (snapshot.shouldShowParallelism()) keys.add("parallelism");
        if (snapshot.shouldShowFactoryLanes()) keys.add("threads");
        return keys;
    }

    private static Component lineValue(Snapshot snapshot, String key) {
        return switch (key) {
            case "structure" -> Component.translatable("jade.mmcr.machine_controller.structure." + (snapshot.formed() ? "formed" : "unformed"))
                    .withStyle(snapshot.formed() ? ChatFormatting.GREEN : ChatFormatting.RED);
            case "state" -> Component.translatable("jade.mmcr.machine_controller.status." + snapshot.status());
            case "recipe_pool" -> RecipePoolDisplayName.component(snapshot.recipePoolId());
            case "progress" -> Component.empty();
            case "parallel_slots" -> Component.translatable("jade.mmcr.machine_controller.parallel_slots.value",
                    snapshot.parallelSlots());
            case "parallelism" -> Component.translatable("jade.mmcr.machine_controller.parallelism.value",
                    snapshot.parallelism(), snapshot.maxParallelism());
            case "threads" -> Component.translatable("jade.mmcr.machine_controller.threads.value",
                    snapshot.factoryLanes(), snapshot.factoryThreadLimit());
            default -> Component.empty();
        };
    }

    private static Component row(String key, Component value) {
        return Component.translatable("jade.mmcr.machine_controller." + key)
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(value);
    }

    record Snapshot(
        boolean formed,
        boolean active,
        boolean tickMachine,
            int tick,
            int totalTick,
            long parallelism,
            long maxParallelism,
            int parallelSlots,
            long maxParallelSlots,
            boolean factorySupported,
            boolean factoryPresent,
            int factoryLanes,
            int factoryThreadLimit,
            int itemInputs,
            int itemOutputs,
            int fluidInputs,
            int fluidOutputs,
            int energyInputs,
            int energyOutputs,
            ResourceLocation recipePoolId
    ) {

        static Snapshot from(CompoundTag tag) {
            return new Snapshot(
                    tag.getBoolean("formed"),
                    tag.getBoolean("active"),
                    tag.getBoolean("tickMachine"),
                    tag.getInt("tick"),
                    tag.getInt("totalTick"),
                    tag.getLong("parallelism"),
                    tag.contains("maxParallelism") ? tag.getLong("maxParallelism") : 1L,
                    tag.getInt("parallelSlots"),
                    tag.getLong("maxParallelSlots"),
                    tag.getBoolean("factorySupported"),
                    tag.getBoolean("factoryPresent"),
                    tag.getInt("factoryLanes"),
                    tag.contains("factoryThreadLimit") ? tag.getInt("factoryThreadLimit") : 1,
                    tag.getInt("itemInputs"),
                    tag.getInt("itemOutputs"),
                    tag.getInt("fluidInputs"),
                    tag.getInt("fluidOutputs"),
                    tag.getInt("energyInputs"),
                    tag.getInt("energyOutputs"),
                    parseResourceLocation(tag.getString("recipePool")));
        }

        private static ResourceLocation parseResourceLocation(String value) {
            return value == null || value.isEmpty() ? null : ResourceLocation.tryParse(value);
        }

        String status() {
            if (!formed) return "unformed";
            if (hasActiveWork()) return "working";
            return "idle";
        }

        boolean hasProgress() {
            return hasActiveWork() && totalTick > 0;
        }

        boolean shouldShowParallelSlots() {
            return parallelSlots > 0;
        }

        boolean shouldShowParallelism() {
            return !factoryPresent && (maxParallelism > 1 || parallelism > 1);
        }

        boolean hasFactoryController() {
            return factoryPresent;
        }

        boolean shouldShowFactoryLanes() {
            return factoryPresent;
        }

        private boolean hasActiveWork() {
            return active && (factoryLanes > 0 || (tick > 0 && totalTick > 0));
        }
    }
}
