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

        if (!snapshot.machine().isEmpty()) {
            tooltip.add(row("machine", Component.literal(snapshot.machine()).withStyle(ChatFormatting.WHITE)));
        }
        tooltip.add(row("structure", Component.translatable("jade.mmcr.machine_controller.structure." + (snapshot.formed() ? "formed" : "unformed"))
                .withStyle(snapshot.formed() ? ChatFormatting.GREEN : ChatFormatting.RED)));
        tooltip.add(row("state", Component.translatable("jade.mmcr.machine_controller.status." + snapshot.status())));

        if (!snapshot.activeRecipe().isEmpty()) {
            tooltip.add(row("recipe", Component.literal(snapshot.activeRecipe()).withStyle(ChatFormatting.WHITE)));
        }
        if (snapshot.hasProgress()) {
            tooltip.add(row("progress", Component.translatable("jade.mmcr.machine_controller.progress.value",
                    snapshot.progressPercent(), snapshot.tick(), snapshot.totalTick())));
        }
        if (snapshot.shouldShowParallelism()) {
            tooltip.add(row("parallelism", Component.translatable("jade.mmcr.machine_controller.parallelism.value",
                    snapshot.parallelism(), snapshot.maxParallelism())));
        }
        if (snapshot.shouldShowFactoryLanes()) {
            tooltip.add(row("factory_lanes", Component.translatable("jade.mmcr.machine_controller.factory_lanes.value",
                    snapshot.factoryLanes())));
        }
        tooltip.add(row("components", Component.translatable("jade.mmcr.machine_controller.components.value",
                snapshot.itemInputs(), snapshot.itemOutputs(),
                snapshot.fluidInputs(), snapshot.fluidOutputs(),
                snapshot.energyInputs(), snapshot.energyOutputs())));
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
            int factoryLanes,
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
                    tag.getIntOr("parallelism", 1),
                    tag.getIntOr("maxParallelism", 1),
                    tag.getIntOr("factoryLanes", 0),
                    tag.getIntOr("itemInputs", 0),
                    tag.getIntOr("itemOutputs", 0),
                    tag.getIntOr("fluidInputs", 0),
                    tag.getIntOr("fluidOutputs", 0),
                    tag.getIntOr("energyInputs", 0),
                    tag.getIntOr("energyOutputs", 0));
        }

        String status() {
            if (!formed) return "unformed";
            if (active) return activeRecipe.isEmpty() ? "waiting" : "working";
            return "idle";
        }

        boolean hasProgress() {
            return active && totalTick > 0;
        }

        int progressPercent() {
            if (!hasProgress()) return 0;
            return Math.clamp(Math.round(tick * 100.0F / totalTick), 0, 100);
        }

        boolean shouldShowParallelism() {
            return parallelism > 1 || maxParallelism > 1;
        }

        boolean shouldShowFactoryLanes() {
            return factoryLanes > 0;
        }
    }
}
