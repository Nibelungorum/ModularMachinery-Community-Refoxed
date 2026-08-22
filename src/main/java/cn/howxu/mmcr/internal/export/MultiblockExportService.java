package cn.howxu.mmcr.internal.export;

import cn.howxu.mmcr.api.machine.BlockRotator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
        return normalizeOffset(offset, controllerFace, Direction.SOUTH);
    }

    public static BlockPos normalizeOffset(BlockPos offset, Direction controllerFace, Direction rollFacing) {
        return BlockRotator.normalizeFromFace(offset, controllerFace, rollFacing);
    }

    public static String renderJava(List<SnapshotEntry> entries, Direction controllerFace) {
        return renderJava(entries, controllerFace, Direction.SOUTH);
    }

    public static String renderJava(List<SnapshotEntry> entries, Direction controllerFace, Direction rollFacing) {
        PreparedExport prepared = prepare(entries, controllerFace, rollFacing);
        StringBuilder out = new StringBuilder(".pattern(p -> p").append(System.lineSeparator());
        appendLayers(out, prepared, ".layer(");
        for (Map.Entry<PredicateKey, Character> symbol : prepared.symbols().entrySet()) {
            if (symbol.getValue() == 'C') continue;
            out.append("        .where('").append(symbol.getValue()).append("', ")
                    .append(predicateExpression(symbol.getKey())).append(")").append(System.lineSeparator());
        }
        return out.append(")").append(System.lineSeparator()).toString();
    }

    public static String renderKubeJS(List<SnapshotEntry> entries, Direction controllerFace) {
        return renderKubeJS(entries, controllerFace, Direction.SOUTH);
    }

    public static String renderKubeJS(List<SnapshotEntry> entries, Direction controllerFace, Direction rollFacing) {
        PreparedExport prepared = prepare(entries, controllerFace, rollFacing);
        StringBuilder out = new StringBuilder();
        appendLayers(out, prepared, null);
        for (Map.Entry<PredicateKey, Character> symbol : prepared.symbols().entrySet()) {
            out.append(".set('").append(symbol.getValue()).append("', ")
                    .append(kubeJsPredicateExpression(symbol.getKey())).append(")")
                    .append(System.lineSeparator());
        }
        return out.toString();
    }

    private static PreparedExport prepare(List<SnapshotEntry> entries, Direction controllerFace, Direction rollFacing) {
        Direction normalizedRoll = BlockRotator.normalizedRoll(controllerFace, rollFacing);
        List<RenderedEntry> rendered = entries.stream()
                .filter(entry -> !entry.air())
                .map(entry -> new RenderedEntry(normalizeOffset(entry.offset(), controllerFace, normalizedRoll),
                        new PredicateKey(entry.blockId(), entry.state())))
                .sorted(Comparator.comparingInt((RenderedEntry entry) -> entry.pos().getZ())
                        .thenComparingInt(entry -> entry.pos().getY())
                        .thenComparingInt(entry -> entry.pos().getX())
                        .thenComparing(entry -> entry.predicate().blockId().toString())
                        .thenComparing(entry -> entry.predicate().state() == null ? "" : entry.predicate().state().toString()))
                .toList();
        PredicateKey controller = entries.stream()
                .filter(SnapshotEntry::controller)
                .map(entry -> new PredicateKey(entry.blockId(), entry.state()))
                .findFirst()
                .orElse(null);
        return new PreparedExport(rendered, assignSymbols(rendered, controller));
    }

    private static void appendLayers(StringBuilder out, PreparedExport prepared, String method) {
        List<RenderedEntry> rendered = prepared.rendered();
        Map<BlockPos, Character> charsByPos = new HashMap<>();
        for (RenderedEntry entry : rendered) charsByPos.put(entry.pos(), prepared.symbols().get(entry.predicate()));
        int minX = rendered.stream().mapToInt(entry -> entry.pos().getX()).min().orElse(0);
        int maxX = rendered.stream().mapToInt(entry -> entry.pos().getX()).max().orElse(0);
        int minY = rendered.stream().mapToInt(entry -> entry.pos().getY()).min().orElse(0);
        int maxY = rendered.stream().mapToInt(entry -> entry.pos().getY()).max().orElse(0);
        int minZ = rendered.stream().mapToInt(entry -> entry.pos().getZ()).min().orElse(0);
        int maxZ = rendered.stream().mapToInt(entry -> entry.pos().getZ()).max().orElse(0);
        if (method == null) {
            if (rendered.isEmpty()) {
                out.append(".pattern(\" \")").append(System.lineSeparator());
                return;
            }
            for (int z = minZ; z <= maxZ; z++) {
                out.append(".pattern(");
                for (int y = minY; y <= maxY; y++) {
                    StringBuilder row = new StringBuilder();
                    for (int x = minX; x <= maxX; x++) row.append(charsByPos.getOrDefault(new BlockPos(x, y, z), ' '));
                    if (y > minY) out.append(", ");
                    out.append('"').append(row).append('"');
                }
                out.append(")").append(System.lineSeparator());
            }
            return;
        }
        if (rendered.isEmpty()) {
            appendLayer(out, method, List.of(" "));
            return;
        }
        for (int z = minZ; z <= maxZ; z++) {
            List<String> rows = new ArrayList<>();
            for (int y = minY; y <= maxY; y++) {
                StringBuilder row = new StringBuilder();
                for (int x = minX; x <= maxX; x++) row.append(charsByPos.getOrDefault(new BlockPos(x, y, z), ' '));
                rows.add(row.toString());
            }
            appendLayer(out, method, rows);
        }
    }

    private static void appendLayer(StringBuilder out, String method, List<String> rows) {
        out.append("        ");
        if (method != null) out.append(method);
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) out.append(", ");
            out.append('"').append(rows.get(i)).append('"');
        }
        out.append(")").append(System.lineSeparator());
    }

    public static Path nextExportPath(Path gameDir, LocalDateTime timestamp) {
        String prefix = FILE_TIME.format(timestamp) + "-多方块导出-";
        Path exportDir = gameDir.resolve("mmcr_structure_export");
        int index = 1;
        while (true) {
            Path path = exportDir.resolve(prefix + index + ".txt");
            if (!Files.exists(path)) return path;
            index++;
        }
    }

    public static Path writeExport(Path gameDir, LocalDateTime timestamp, List<SnapshotEntry> entries,
                                   Direction controllerFace) throws IOException {
        return writeExport(gameDir, timestamp, entries, controllerFace, Direction.SOUTH, false);
    }

    public static Path writeExport(Path gameDir, LocalDateTime timestamp, List<SnapshotEntry> entries,
                                   Direction controllerFace, Direction rollFacing) throws IOException {
        return writeExport(gameDir, timestamp, entries, controllerFace, rollFacing, false);
    }

    public static Path writeExport(Path gameDir, LocalDateTime timestamp, List<SnapshotEntry> entries,
                                   Direction controllerFace, Direction rollFacing, boolean kubeJs) throws IOException {
        String text = kubeJs ? renderKubeJS(entries, controllerFace, rollFacing) : renderJava(entries, controllerFace, rollFacing);
        Path path = nextExportPath(gameDir, timestamp);
        Files.createDirectories(path.getParent());
        Files.writeString(path, text);
        return path;
    }

    private static LinkedHashMap<PredicateKey, Character> assignSymbols(List<RenderedEntry> rendered, PredicateKey explicitController) {
        LinkedHashMap<PredicateKey, Character> symbols = new LinkedHashMap<>();
        Map<PredicateKey, Integer> counts = new LinkedHashMap<>();
        for (RenderedEntry entry : rendered) counts.merge(entry.predicate(), 1, Integer::sum);
        PredicateKey controller = explicitController;
        PredicateKey casing = null;
        for (PredicateKey key : counts.keySet()) {
            String path = key.blockId().getPath();
            if (controller == null && (path.endsWith("_controller") || path.equals("controller"))) controller = key;
            if (!key.equals(controller) && (casing == null || counts.get(key) > counts.get(casing))) casing = key;
        }
        if (controller != null) {
            for (PredicateKey key : counts.keySet()) {
                if (key.equals(controller)) {
                    symbols.put(key, 'C');
                    break;
                }
            }
        }
        if (casing != null && !symbols.containsKey(casing)) symbols.put(casing, 'X');
        String available = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        for (RenderedEntry entry : rendered) {
            if (symbols.containsKey(entry.predicate())) continue;
            for (int i = 0; i < available.length(); i++) {
                char c = available.charAt(i);
                if (c == 'C' || c == 'X' || symbols.containsValue(c)) continue;
                symbols.put(entry.predicate(), c);
                break;
            }
            if (!symbols.containsKey(entry.predicate())) throw new IllegalArgumentException("Too many unique blocks to export as single-character pattern symbols");
        }
        return symbols;
    }

    private static String predicateExpression(PredicateKey key) {
        String block = "BuiltInRegistries.BLOCK.getValue(Identifier.parse(\"" + escapeJava(key.blockId().toString()) + "\"))";
        if (key.state() == null) return "new BlockPredicate.OfBlock(" + block + ")";
        String expression = block + ".defaultBlockState()";
        for (Property<?> property : key.state().getProperties().stream().sorted(Comparator.comparing(Property::getName)).toList()) {
            String propertyExpression = javaPropertyExpression(block, property);
            expression += ".setValue(" + propertyExpression + ", "
                    + propertyExpression + ".getValue(\""
                    + escapeJava(propertyValueName(property, key.state().getValue(property))) + "\").orElseThrow())";
        }
        return "new BlockPredicate.OfBlockState(" + expression + ")";
    }

    private static String javaPropertyExpression(String block, Property<?> property) {
        return "((net.minecraft.world.level.block.state.properties.Property) " + block
                + ".getStateDefinition().getProperty(\"" + escapeJava(property.getName()) + "\"))";
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String propertyValueName(Property<?> property, Object value) {
        return ((Property) property).getName((Comparable) value);
    }

    private static String kubeJsPredicateExpression(PredicateKey key) {
        if (key.state() == null) return "api.block('" + escapeKubeJs(key.blockId().toString()) + "')";
        StringBuilder state = new StringBuilder(key.blockId().toString()).append('[');
        key.state().getProperties().stream().sorted(Comparator.comparing(Property::getName)).forEach(property -> {
            if (state.charAt(state.length() - 1) != '[') state.append(',');
            state.append(property.getName()).append('=').append(key.state().getValue(property));
        });
        return "api.state('" + escapeKubeJs(state.append(']').toString()) + "')";
    }

    private static String escapeJava(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String escapeKubeJs(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    public record SnapshotEntry(BlockPos offset, Identifier blockId, BlockState state, boolean air, boolean controller) {
        public SnapshotEntry(BlockPos offset, BlockState state, boolean air, boolean controller) {
            this(offset, BuiltInRegistries.BLOCK.getKey(state.getBlock()), state, air, controller);
        }

        public SnapshotEntry(BlockPos offset, BlockState state, boolean air) {
            this(offset, state, air, false);
        }

        /**
         * Legacy identifier-only capture; nullable {@link #state} keeps this entry as a plain Block predicate.
         */
        public SnapshotEntry(BlockPos offset, Identifier blockId, boolean air) {
            this(offset, blockId, null, air, false);
        }

        public SnapshotEntry(BlockPos offset, Identifier blockId, boolean air, boolean controller) {
            this(offset, blockId, null, air, controller);
        }
    }

    private record PredicateKey(Identifier blockId, BlockState state) {}

    private record RenderedEntry(BlockPos pos, PredicateKey predicate) {}

    private record PreparedExport(List<RenderedEntry> rendered, LinkedHashMap<PredicateKey, Character> symbols) {}

    private static final class ExportThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "MMCR Multiblock Export");
            thread.setDaemon(true);
            return thread;
        }
    }
}
