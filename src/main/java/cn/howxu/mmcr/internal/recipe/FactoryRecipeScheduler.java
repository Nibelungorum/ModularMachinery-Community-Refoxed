package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Server-thread factory lane scheduler foundation. It never runs recipe work off-thread and async search must use snapshots only.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FactoryRecipeScheduler {

    private final int threadLimit;
    private final List<Lane> lanes = new ArrayList<>();

    public FactoryRecipeScheduler(int threadLimit) {
        this.threadLimit = Math.max(1, threadLimit);
    }

    public boolean startLane(Lane lane) {
        if (lane == null || !hasCapacity()) return false;
        lane.start();
        lanes.add(lane);
        return true;
    }

    public boolean hasCapacity() {
        return lanes.size() < threadLimit;
    }

    public int laneCapacity() {
        return Math.max(0, threadLimit - lanes.size());
    }

    public void tick() {
        tick(0L);
    }

    public void tick(long gameTime) {
        Iterator<Lane> iterator = lanes.iterator();
        while (iterator.hasNext()) {
            Lane lane = iterator.next();
            if (lane.tick(gameTime)) iterator.remove();
        }
    }

    public void stopAll() {
        for (Lane lane : lanes) {
            lane.stop();
        }
        lanes.clear();
    }

    public int activeLaneCount() {
        return lanes.size();
    }

    public int threadLimit() {
        return threadLimit;
    }

    public static RecipeSnapshot captureSnapshot(Identifier recipeId,
                                                 int parallelism,
                                                 long structureVersion,
                                                 List<ProcessingComponent> components) {
        List<ComponentSnapshot> componentSnapshots = new ArrayList<>();
        for (ProcessingComponent component : components == null ? List.<ProcessingComponent>of() : components) {
            Identifier typeId = component.getType() == null ? null : component.getType().getRegistryName();
            if (typeId == null && component.getComponent() != null && component.getComponent().kind() != null) {
                componentSnapshots.add(new ComponentSnapshot(
                        component.getComponent().kind().id(),
                        component.getRelativePos(),
                        component.tags()));
                continue;
            }
            componentSnapshots.add(new ComponentSnapshot(
                    typeId == null ? "unknown" : typeId.toString(),
                    component.getRelativePos(),
                    component.tags()));
        }
        return new RecipeSnapshot(recipeId, Math.max(1, parallelism), structureVersion, componentSnapshots);
    }

    public interface Lane {
        default void start() { }

        default boolean tick(long gameTime) {
            return tick();
        }

        boolean tick();

        void stop();
    }

    public record RecipeSnapshot(Identifier recipeId, int parallelism, long structureVersion,
                                 List<ComponentSnapshot> components) {
        public RecipeSnapshot {
            components = List.copyOf(components == null ? List.of() : components);
        }
    }

    public record ComponentSnapshot(String type, BlockPos relativePos, List<String> tags) {
        public ComponentSnapshot {
            type = type == null ? "unknown" : type;
            relativePos = relativePos == null ? BlockPos.ZERO : relativePos.immutable();
            tags = List.copyOf(tags == null ? List.of() : tags);
        }
    }
}
