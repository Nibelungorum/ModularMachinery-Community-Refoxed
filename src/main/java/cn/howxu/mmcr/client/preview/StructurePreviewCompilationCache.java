package cn.howxu.mmcr.client.preview;

import cn.howxu.mmcr.api.machine.Machine;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import net.minecraft.resources.Identifier;

/**
 * Client-owned lazy compilation cache for JEI structure previews.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class StructurePreviewCompilationCache implements AutoCloseable {
    private static final StructurePreviewCompilationCache INSTANCE = new StructurePreviewCompilationCache();
    private final Map<Identifier, StructurePreviewCompilation> entries = new ConcurrentHashMap<>();
    private final StructurePreviewSchemaFactory factory;
    private final Executor executor;

    public StructurePreviewCompilationCache() { this(new StructurePreviewSchemaFactory(), ForkJoinPool.commonPool()); }
    public static StructurePreviewCompilationCache instance() { return INSTANCE; }
    StructurePreviewCompilationCache(StructurePreviewSchemaFactory factory, Executor executor) {
        this.factory = factory;
        this.executor = executor;
    }
    public StructurePreviewCompilation acquire(Machine machine) {
        return entries.computeIfAbsent(machine.registryName(), ignored -> create(machine));
    }
    public boolean has(Identifier machineId) { return entries.containsKey(machineId); }
    public void clear() { entries.clear(); }
    @Override public void close() { clear(); }

    private StructurePreviewCompilation create(Machine machine) {
        StructurePreviewCompilation[] reference = new StructurePreviewCompilation[1];
        reference[0] = new StructurePreviewCompilation(() -> executor.execute(() -> {
            try {
                reference[0].complete(factory.create(machine), null);
            } catch (Throwable throwable) {
                reference[0].complete(null, throwable);
            }
        }));
        return reference[0];
    }
}
