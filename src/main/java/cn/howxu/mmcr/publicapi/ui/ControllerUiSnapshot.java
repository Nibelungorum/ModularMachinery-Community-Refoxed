package cn.howxu.mmcr.publicapi.ui;

import cn.howxu.mmcr.publicapi.data.DataKey;
import cn.howxu.mmcr.publicapi.runtime.OutputView;
import cn.howxu.mmcr.publicapi.runtime.RuntimeFailure;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Public facade of the core-owned controller presentation.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface ControllerUiSnapshot {
    enum Kind { NORMAL, TICK, FACTORY }
    enum Role { NORMAL, HOST, MODULE }

    UUID sessionId();
    long revision();
    boolean ready();
    ResourceKey<Level> dimension();
    BlockPos controllerPos();
    ResourceLocation machineId();
    Kind kind();
    Role role();
    Component machineName();
    boolean formed();
    boolean active();
    boolean redstonePaused();
    int installedModuleCount();
    Optional<ResourceLocation> connectedHostId();
    int matchedStage();
    int stageCount();
    List<ResourceLocation> foundLevelIds();
    int parallelSlots();
    long maxParallelism();
    int threadLimit();
    int activeThreadCount();
    List<ResourceLocation> recipePoolIds();
    Optional<ResourceLocation> currentRecipePoolId();
    Optional<RuntimeFailure> failure();
    boolean hasDataStorage();
    Map<String, DataKey> dataStorageValues();
    List<TextLine> lines();
    List<Lane> lanes();

    /** Read-only execution lane.
     * @author howxu <dev@howxu.cn> */
    @ApiStatus.NonExtendable
    interface Lane {
        String id();
        int index();
        boolean base();
        boolean core();
        boolean active();
        Optional<ResourceLocation> recipeId();
        int tick();
        int totalTick();
        long parallelism();
        Optional<RuntimeFailure> failure();
        List<TextLine> lines();
        RecipePresentation recipe();
    }

    /** Already parallel-scaled expected recipe outputs and rates.
     * @author howxu <dev@howxu.cn> */
    @ApiStatus.NonExtendable
    interface RecipePresentation {
        List<Output> outputs();
        long energyInputPerTick();
        long energyOutputPerTick();
        double heatOutputPerTick();
        int durationTicks();
        long parallelism();
    }

    /** An isolated resource view and its independent expected total amount.
     * @author howxu <dev@howxu.cn> */
    @ApiStatus.NonExtendable
    interface Output {
        OutputView resource();
        long amount();
    }

    /** External logic text with copy-on-read components.
     * @author howxu <dev@howxu.cn> */
    @ApiStatus.NonExtendable
    interface TextLine {
        enum Scope { CONTROLLER, OPERATION }
        ResourceLocation id();
        Scope scope();
        Component text();
    }
}
