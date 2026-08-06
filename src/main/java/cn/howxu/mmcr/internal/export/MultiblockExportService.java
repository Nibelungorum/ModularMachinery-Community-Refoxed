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
import java.util.HashMap;
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
        return BlockRotator.normalizeFromFace(offset, controllerFace);
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

        String newline = System.lineSeparator();
        StringBuilder out = new StringBuilder();
        out.append("import cn.howxu.mmcr.api.machine.BlockArray;").append(newline);
        out.append("import cn.howxu.mmcr.api.machine.BlockPredicate;").append(newline);

        LinkedHashMap<Identifier, Character> symbols = assignSymbols(rendered);
        out.append("import net.minecraft.core.registries.BuiltInRegistries;").append(newline);
        out.append("import net.minecraft.resources.Identifier;").append(newline);
        out.append(newline);

        out.append("BlockArray pattern = BlockArray.builder()").append(newline);
        if (rendered.isEmpty()) {
            out.append("        .pattern(\" \")").append(newline);
        } else {
            int minX = rendered.stream().mapToInt(entry -> entry.pos().getX()).min().orElse(0);
            int maxX = rendered.stream().mapToInt(entry -> entry.pos().getX()).max().orElse(0);
            int minY = rendered.stream().mapToInt(entry -> entry.pos().getY()).min().orElse(0);
            int maxY = rendered.stream().mapToInt(entry -> entry.pos().getY()).max().orElse(0);
            int minZ = rendered.stream().mapToInt(entry -> entry.pos().getZ()).min().orElse(0);
            int maxZ = rendered.stream().mapToInt(entry -> entry.pos().getZ()).max().orElse(0);

            Map<BlockPos, Character> charsByPos = new HashMap<>();
            for (RenderedEntry entry : rendered) {
                charsByPos.put(entry.pos(), symbols.get(entry.blockId()));
            }

            for (int z = minZ; z <= maxZ; z++) {
                out.append("        .pattern(");
                for (int y = minY; y <= maxY; y++) {
                    if (y > minY) out.append(", ");
                    out.append('"');
                    for (int x = minX; x <= maxX; x++) {
                        out.append(charsByPos.getOrDefault(new BlockPos(x, y, z), ' '));
                    }
                    out.append('"');
                }
                out.append(")").append(newline);
            }
        }
        for (Map.Entry<Identifier, Character> symbol : symbols.entrySet()) {
            out.append("        .set('").append(symbol.getValue()).append("', ")
                    .append(predicateExpression(symbol.getKey())).append(")")
                    .append(newline);
        }
        out.append("        .build();").append(newline);
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

    private static LinkedHashMap<Identifier, Character> assignSymbols(List<RenderedEntry> rendered) {
        LinkedHashMap<Identifier, Character> symbols = new LinkedHashMap<>();
        Map<Identifier, Integer> counts = new HashMap<>();
        for (RenderedEntry entry : rendered) {
            counts.merge(entry.blockId(), 1, Integer::sum);
        }

        Identifier controller = null;
        Identifier casing = null;
        for (Identifier id : counts.keySet()) {
            String path = id.getPath();
            if (controller == null && (path.endsWith("_controller") || path.equals("controller"))) {
                controller = id;
            }
            if (casing == null || counts.get(id) > counts.get(casing)) {
                casing = id;
            }
        }

        if (controller != null) symbols.put(controller, 'C');
        if (casing != null && !symbols.containsKey(casing)) symbols.put(casing, 'X');

        String available = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        for (RenderedEntry entry : rendered) {
            if (symbols.containsKey(entry.blockId())) continue;
            for (int i = 0; i < available.length(); i++) {
                char c = available.charAt(i);
                if (c == 'C' || c == 'X' || symbols.containsValue(c)) continue;
                symbols.put(entry.blockId(), c);
                break;
            }
            if (!symbols.containsKey(entry.blockId())) {
                throw new IllegalArgumentException("Too many unique blocks to export as single-character pattern symbols");
            }
        }
        return symbols;
    }

    private static String predicateExpression(Identifier id) {
        return "new BlockPredicate.OfBlock(BuiltInRegistries.BLOCK.getValue(Identifier.parse(\"" + id + "\")))";
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
