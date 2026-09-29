package cn.howxu.mmcr.client.preview;

import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineStructureStage;
import cn.howxu.mmcr.client.RuntimeContentClientApplier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-owned lazy compilation cache for JEI structure previews.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class StructurePreviewCompilationCache implements AutoCloseable {
    public static final int DEFAULT_STAGE_NUMBER = 0;

    private static final StructurePreviewCompilationCache INSTANCE = new StructurePreviewCompilationCache();
    private final Map<CacheKey, StructurePreviewCompilation> entries = new ConcurrentHashMap<>();
    private final StructurePreviewSchemaFactory factory;
    private final Executor executor;

    public StructurePreviewCompilationCache() { this(new StructurePreviewSchemaFactory(), ForkJoinPool.commonPool()); }
    public static StructurePreviewCompilationCache instance() { return INSTANCE; }
    StructurePreviewCompilationCache(StructurePreviewSchemaFactory factory, Executor executor) {
        this.factory = factory;
        this.executor = executor;
    }
    public StructurePreviewCompilation acquire(Machine machine) {
        return acquire(machine, DEFAULT_STAGE_NUMBER, RuntimeContentClientApplier.appliedContentVersion());
    }
    public StructurePreviewCompilation acquire(Machine machine, long contentVersion) {
        return acquire(machine, DEFAULT_STAGE_NUMBER, contentVersion);
    }
    public StructurePreviewCompilation acquire(Machine machine, int stageNumber) {
        return acquire(machine, stageNumber, RuntimeContentClientApplier.appliedContentVersion());
    }
    private StructurePreviewCompilation acquire(Machine machine, int stageNumber, long contentVersion) {
        return entries.computeIfAbsent(new CacheKey(machine.registryName(), contentVersion, stageNumber),
                ignored -> create(machine, stageNumber));
    }
    public boolean has(ResourceLocation machineId) {
        long currentVersion = RuntimeContentClientApplier.appliedContentVersion();
        return entries.keySet().stream().anyMatch(key -> key.machineId().equals(machineId)
                && key.contentVersion() == currentVersion);
    }
    public void clear() { entries.clear(); }
    @Override public void close() { clear(); }

    private StructurePreviewCompilation create(Machine machine, int stageNumber) {
        StructurePreviewCompilation[] reference = new StructurePreviewCompilation[1];
        reference[0] = new StructurePreviewCompilation(() -> executor.execute(() -> {
            try {
                StructurePreviewSchema schema;
                if (stageNumber == DEFAULT_STAGE_NUMBER) {
                    schema = factory.create(machine);
                } else {
                    MachineStructureStage stage = machine.structureStages().stream()
                            .filter(candidate -> candidate.number() == stageNumber)
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Unknown machine structure stage: " + stageNumber));
                    Direction facing = machine.controller().requireVerticalFacing() ? Direction.UP : Direction.SOUTH;
                    schema = factory.create(stage, machine.registryName(), facing);
                }
                reference[0].complete(schema, null);
            } catch (Throwable throwable) {
                reference[0].complete(null, throwable);
            }
        }));
        return reference[0];
    }

    private record CacheKey(ResourceLocation machineId, long contentVersion, int stageNumber) {
    }
}
