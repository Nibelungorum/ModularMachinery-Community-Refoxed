package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot;
import cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot.Kind;
import cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot.Lane;
import cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot.Role;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Default UI semantics from one owned session snapshot; never queries a controller.
 * @author howxu <dev@howxu.cn> */
final class ControllerUiTextLines {
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance();

    private ControllerUiTextLines() {}

    static Optional<Lane> selectedLane(ControllerUiSnapshot snapshot, String laneId) {
        return snapshot.lanes().stream().filter(lane -> lane.id().equals(laneId)).findFirst()
                .or(() -> snapshot.lanes().stream().filter(Lane::base).findFirst())
                .or(() -> snapshot.lanes().stream().findFirst());
    }

    static List<ControllerTextLine> create(ControllerUiSnapshot snapshot, String laneId) {
        Lane lane = selectedLane(snapshot, laneId).orElse(null);
        List<ControllerTextLine> lines = new ArrayList<>(details(snapshot, laneId));
        appendExternal(lines, snapshot.lines());
        if (lane != null && snapshot.kind() != Kind.TICK) {
            appendExternal(lines, lane.lines());
            lines.addAll(ControllerRecipeTextLines.createSnapshot(lane.recipe()));
        }
        return List.copyOf(lines);
    }

    static List<ControllerTextLine> details(ControllerUiSnapshot snapshot, String laneId) {
        boolean factory = snapshot.kind() == Kind.FACTORY;
        boolean tick = snapshot.kind() == Kind.TICK;
        Lane lane = selectedLane(snapshot, laneId).orElse(null);
        boolean active = factory ? lane != null && lane.active() : snapshot.active();
        List<ControllerTextLine> lines = new ArrayList<>();
        lines.add(MachineControllerScreen.statusLine(snapshot.formed(), active));
        Identifier pool = MachineControllerScreen.displayedRecipePoolId(
                snapshot.currentRecipePoolId().orElse(null), snapshot.recipePoolIds());
        if (snapshot.formed() && pool != null) {
            lines.add(label(Component.translatable("gui.mmcr.controller.recipe_pool",
                    RecipePoolDisplayName.component(pool))));
        }
        if (snapshot.formed() && snapshot.matchedStage() > 0 && snapshot.stageCount() > 1) {
            lines.add(label(MachineControllerScreen.matchedStageLine(snapshot.matchedStage())));
        }
        for (Identifier id : snapshot.foundLevelIds()) {
            MachineLevel level = MachineLevelRegistry.getLevel(id);
            if (level != null) lines.add(label(MachineControllerScreen.levelLine(level)));
        }
        if (!tick) {
            Optional<ExecutionStatus> failure = factory && lane != null
                    ? lane.failure().or(() -> lane.active() ? Optional.empty() : snapshot.failure())
                    : snapshot.failure();
            failure.ifPresent(value -> lines.add(label(Component.translatable("gui.mmcr.controller.last_failure",
                    Component.translatable(failureKey(value))))));
        }
        lines.addAll(MachineControllerScreen.moduleStatusLines(snapshot.role() == Role.HOST,
                snapshot.role() == Role.MODULE, snapshot.installedModuleCount(), snapshot.connectedHostId()));
        if (!tick && (factory || snapshot.formed())) {
            if (snapshot.parallelSlots() > 0) lines.add(label(MachineControllerScreen.parallelSlotLine(snapshot.parallelSlots())));
            lines.add(label(MachineControllerScreen.parallelLine(lane != null && lane.active() ? lane.parallelism() : 0,
                    snapshot.maxParallelism())));
        }
        if (!factory && !tick && lane != null && lane.active() && lane.totalTick() > 0) {
            lines.add(progress(lane));
        }
        if (snapshot.redstonePaused()) lines.add(label(Component.translatable("gui.mmcr.controller.redstone_stopped")));
        if (factory) {
            lines.add(label(Component.translatable("gui.mmcr.controller.threads",
                    Component.literal(NUMBER_FORMAT.format(snapshot.activeThreadCount())),
                    Component.literal(NUMBER_FORMAT.format(snapshot.threadLimit())))));
            if (lane != null && lane.totalTick() > 0) lines.add(progress(lane));
        }
        return List.copyOf(lines);
    }

    static String selectedFailureKey(ControllerUiSnapshot snapshot, String laneId) {
        Lane lane = selectedLane(snapshot, laneId).orElse(null);
        return (lane == null ? snapshot.failure()
                : lane.failure().or(() -> lane.active() ? Optional.empty() : snapshot.failure()))
                .map(ControllerUiTextLines::failureKey).orElse("");
    }

    private static String failureKey(ExecutionStatus failure) {
        return (failure.reason() == null ? BuiltinFailureReasons.UNKNOWN : failure.reason()).translationKey();
    }

    private static ControllerTextLine label(Component text) {
        return new ControllerTextLine(text, MachineControllerScreen.STATUS_LABEL_COLOR);
    }

    private static ControllerTextLine progress(Lane lane) {
        return new ControllerTextLine(Component.translatable("gui.mmcr.controller.progress",
                MachineControllerScreen.progressPercent(lane.tick(), lane.totalTick()) + "%"), -1);
    }

    private static void appendExternal(List<ControllerTextLine> lines, List<ControllerUiSnapshot.TextLine> external) {
        for (ControllerUiSnapshot.TextLine line : external) {
            lines.add(new ControllerTextLine(line.text(), ControllerScreenTextComposer.DEFAULT_EXTERNAL_COLOR));
        }
    }
}
