package cn.howxu.mmcr.api.controller.ui;

import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.data.view.DataValue;
import cn.howxu.mmcr.api.recipe.MachineOutput;
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

/** Owned, read-only controller presentation produced by the library.
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
    Optional<ExecutionStatus> failure();
    boolean hasDataStorage();
    Map<String, DataValue> dataStorageValues();
    List<TextLine> lines();
    List<Lane> lanes();

    /** One execution lane, without any UI-local selection state.
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
        Optional<ExecutionStatus> failure();
        List<TextLine> lines();
        RecipePresentation recipe();
    }

    /** Static, already parallel-scaled recipe presentation.
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

    /** Expected output, not an interface inventory; resource reads are isolated copies.
     * @author howxu <dev@howxu.cn> */
    @ApiStatus.NonExtendable
    interface Output {
        MachineOutput resource();
        long amount();
    }

    /** External logic text; component reads return copies.
     * @author howxu <dev@howxu.cn> */
    @ApiStatus.NonExtendable
    interface TextLine {
        enum Scope { CONTROLLER, OPERATION }
        ResourceLocation id();
        Scope scope();
        Component text();
    }
}
