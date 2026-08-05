package cn.howxu.mmcr.internal.export;

import cn.howxu.mmcr.api.machine.BlockRotator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Pure export helpers plus the shared single-thread writer executor.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MultiblockExportService {

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
    private static final ExecutorService EXPORT_EXECUTOR = Executors.newSingleThreadExecutor(new ExportThreadFactory());

    private MultiblockExportService() {}

    public static ExecutorService executor() {
        return EXPORT_EXECUTOR;
    }

    public static BlockPos normalizeOffset(BlockPos offset, Direction controllerFace) {
        Direction current = controllerFace;
        BlockPos normalized = offset;
        while (current != Direction.SOUTH) {
            current = current.getCounterClockWise();
            normalized = BlockRotator.rotateYCCW(normalized);
        }
        return normalized;
    }

    public static String renderJava(List<SnapshotEntry> entries, Direction controllerFace) {
        List<RenderedEntry> rendered = entries.stream()
                .filter(entry -> !entry.air())
                .map(entry -> new RenderedEntry(normalizeOffset(entry.offset(), controllerFace), entry.blockId()))
                .sorted(Comparator
                        .comparingInt((RenderedEntry entry) -> entry.pos().getY())
                        .thenComparingInt(entry -> entry.pos().getZ())
                        .thenComparingInt(entry -> entry.pos().getX())
                        .thenComparing(entry -> entry.blockId().toString()))
                .toList();

        LinkedHashMap<Identifier, String> variables = new LinkedHashMap<>();
        for (RenderedEntry entry : rendered) {
            variables.computeIfAbsent(entry.blockId(), id -> uniqueName(id, variables));
        }

        String newline = System.lineSeparator();
        StringBuilder out = new StringBuilder();
        out.append("import cn.howxu.mmcr.api.machine.BlockArray;").append(newline);
        out.append("import cn.howxu.mmcr.api.machine.BlockPredicate;").append(newline);
        out.append("import net.minecraft.core.BlockPos;").append(newline);
        out.append("import net.minecraft.core.registries.BuiltInRegistries;").append(newline);
        out.append("import net.minecraft.resources.Identifier;").append(newline);
        out.append("import net.minecraft.world.level.block.Block;").append(newline);
        out.append("import java.util.LinkedHashMap;").append(newline);
        out.append("import java.util.Map;").append(newline);
        out.append(newline);
        out.append("Map<BlockPos, BlockPredicate> blocks = new LinkedHashMap<>();").append(newline);
        for (Map.Entry<Identifier, String> variable : variables.entrySet()) {
            out.append("Block ").append(variable.getValue()).append(" = BuiltInRegistries.BLOCK.getValue(Identifier.parse(\"")
                    .append(variable.getKey()).append("\")); // ").append(variable.getKey()).append(newline);
        }
        for (RenderedEntry entry : rendered) {
            BlockPos pos = entry.pos();
            out.append("blocks.put(new BlockPos(")
                    .append(pos.getX()).append(", ")
                    .append(pos.getY()).append(", ")
                    .append(pos.getZ()).append("), new BlockPredicate.OfBlock(")
                    .append(variables.get(entry.blockId())).append("));")
                    .append(newline);
        }
        out.append("BlockArray pattern = new BlockArray(Map.copyOf(blocks));").append(newline);
        return out.toString();
    }

    public static Path nextExportPath(Path gameDir, LocalDateTime timestamp) {
        String prefix = FILE_TIME.format(timestamp) + "-多方块导出-";
        int index = 1;
        while (true) {
            Path path = gameDir.resolve(prefix + index + ".txt");
            if (!Files.exists(path)) return path;
            index++;
        }
    }

    public static Path writeExport(Path gameDir, LocalDateTime timestamp, List<SnapshotEntry> entries,
                                   Direction controllerFace) throws IOException {
        String text = renderJava(entries, controllerFace);
        Path path = nextExportPath(gameDir, timestamp);
        Files.writeString(path, text);
        return path;
    }

    private static String uniqueName(Identifier id, Map<Identifier, String> existing) {
        String base = sanitize(id.getPath());
        if (base.isBlank()) base = "block";
        if (Character.isDigit(base.charAt(0))) base = "block_" + base;
        String candidate = base;
        int index = 2;
        while (existing.containsValue(candidate)) {
            candidate = base + index;
            index++;
        }
        return candidate;
    }

    private static String sanitize(String path) {
        StringBuilder out = new StringBuilder(path.length());
        boolean upperNext = false;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                out.append(upperNext ? Character.toUpperCase(c) : c);
                upperNext = false;
            } else {
                upperNext = out.length() > 0;
            }
        }
        return out.toString();
    }

    public record SnapshotEntry(BlockPos offset, Identifier blockId, boolean air) {}

    private record RenderedEntry(BlockPos pos, Identifier blockId) {}

    private static final class ExportThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "MMCR Multiblock Export");
            thread.setDaemon(true);
            return thread;
        }
    }
}
