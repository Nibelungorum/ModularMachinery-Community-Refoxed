package cn.howxu.mmcr.compat.jade;

import cn.howxu.mmcr.MMCR;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * @author howxu <dev@howxu.cn>
 */
public enum MachineControllerComponentProvider implements IComponentProvider<BlockAccessor> {
    INSTANCE;

    static final Identifier UID = MMCR.id("machine_controller");

    @Override
    public Identifier getUid() {
        return UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        Snapshot snapshot = Snapshot.from(accessor.getServerData());

        for (String key : lineKeys(snapshot)) {
            tooltip.add(row(key, lineValue(snapshot, key)));
        }
    }

    static List<String> lineKeys(Snapshot snapshot) {
        List<String> keys = new ArrayList<>();
        if (!snapshot.machine().isEmpty()) keys.add("machine");
        keys.add("structure");
        keys.add("state");
        if (!snapshot.hasFactoryController() && !snapshot.activeRecipe().isEmpty()) keys.add("recipe");
        if (!snapshot.hasFactoryController() && snapshot.hasProgress()) keys.add("progress");
        if (snapshot.shouldShowParallelSlots()) keys.add("parallel_slots");
        if (snapshot.shouldShowParallelism()) keys.add("parallelism");
        if (snapshot.shouldShowFactoryLanes()) keys.add("threads");
        return keys;
    }

    private static Component lineValue(Snapshot snapshot, String key) {
        return switch (key) {
            case "machine" -> Component.literal(snapshot.machine()).withStyle(ChatFormatting.WHITE);
            case "structure" -> Component.translatable("jade.mmcr.machine_controller.structure." + (snapshot.formed() ? "formed" : "unformed"))
                    .withStyle(snapshot.formed() ? ChatFormatting.GREEN : ChatFormatting.RED);
            case "state" -> Component.translatable("jade.mmcr.machine_controller.status." + snapshot.status());
            case "recipe" -> Component.literal(snapshot.activeRecipe()).withStyle(ChatFormatting.WHITE);
            case "progress" -> Component.translatable("jade.mmcr.machine_controller.progress.value",
                    snapshot.progressPercent(), snapshot.tick(), snapshot.totalTick());
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
            String machine,
            boolean formed,
            boolean active,
            String activeRecipe,
            int tick,
            int totalTick,
            int parallelism,
            int maxParallelism,
            int parallelSlots,
            int maxParallelSlots,
            boolean factorySupported,
            boolean factoryPresent,
            int factoryLanes,
            int factoryThreadLimit,
            int itemInputs,
            int itemOutputs,
            int fluidInputs,
            int fluidOutputs,
            int energyInputs,
            int energyOutputs
    ) {

        static Snapshot from(CompoundTag tag) {
            return new Snapshot(
                    tag.getStringOr("machine", ""),
                    tag.getBooleanOr("formed", false),
                    tag.getBooleanOr("active", false),
                    tag.getStringOr("activeRecipe", ""),
                    tag.getIntOr("tick", 0),
                    tag.getIntOr("totalTick", 0),
                    tag.getIntOr("parallelism", 0),
                    tag.getIntOr("maxParallelism", 1),
                    tag.getIntOr("parallelSlots", 0),
                    tag.getIntOr("maxParallelSlots", 0),
                    tag.getBooleanOr("factorySupported", false),
                    tag.getBooleanOr("factoryPresent", false),
                    tag.getIntOr("factoryLanes", 0),
                    tag.getIntOr("factoryThreadLimit", 1),
                    tag.getIntOr("itemInputs", 0),
                    tag.getIntOr("itemOutputs", 0),
                    tag.getIntOr("fluidInputs", 0),
                    tag.getIntOr("fluidOutputs", 0),
                    tag.getIntOr("energyInputs", 0),
                    tag.getIntOr("energyOutputs", 0));
        }

        String status() {
            if (!formed) return "unformed";
            if (hasActiveWork()) return "working";
            return "idle";
        }

        boolean hasProgress() {
            return hasActiveWork() && totalTick > 0;
        }

        int progressPercent() {
            if (!hasProgress()) return 0;
            return Math.clamp(Math.round(tick * 100.0F / totalTick), 0, 100);
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
            return active && (!activeRecipe.isEmpty() || factoryLanes > 0);
        }
    }
}
