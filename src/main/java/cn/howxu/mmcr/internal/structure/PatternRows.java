package cn.howxu.mmcr.internal.structure;

import it.unimi.dsi.fastutil.chars.Char2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.BlockPos;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;

/** Immutable rectangular script view; the builders and compiler read the indexed non-padding spans.
 * @author HowXu {@literal <dev@howxu.cn>}
 */
public final class PatternRows extends AbstractList<List<String>> implements RandomAccess {
    private final List<Layer> layers;
    private final Map<Character, Occurrence> symbols;
    private final int width, height;

    private PatternRows(Builder builder) {
        layers = List.copyOf(builder.layers);
        var found = new LinkedHashMap<Character, Occurrence>();
        builder.symbols.forEach((c, counter) -> found.put(c, new Occurrence(counter.count, counter.last.immutable())));
        symbols = Collections.unmodifiableMap(found);
        width = builder.width;
        height = builder.height;
    }

    public static PatternRows copyOf(List<List<String>> layers) {
        if (layers instanceof PatternRows indexed) return indexed;
        Builder builder = new Builder();
        for (var layer : layers) builder.add(layer.toArray(String[]::new));
        return builder.freeze();
    }

    @Override public int size() { return layers.size(); }
    @Override public List<String> get(int slice) { return layers.get(slice); }
    public int width() { return width; }
    public int height() { return height; }

    public boolean hasSymbol(char c) { return symbols.containsKey(c); }

    public BlockPos origin(Character controller) {
        Occurrence found = controller == null ? null : symbols.get(controller);
        return found == null ? new BlockPos(width / 2, height / 2, size() / 2) : found.last;
    }

    public void validateBindings(Map<Character, ?> bindings, char controller) {
        for (char c : symbols.keySet()) {
            if (c != ' ' && !bindings.containsKey(c)) throw new IllegalStateException("Unbound pattern symbol: " + c);
        }
        Occurrence found = symbols.get(controller);
        if (found == null || found.count != 1) throw new IllegalStateException("Pattern must contain exactly one controller symbol");
    }

    /** Receives a bound or skipped interval in the original rectangular coordinate system.
     * @author HowXu {@literal <dev@howxu.cn>}
     */
    @FunctionalInterface
    public interface RunConsumer { void accept(int x, int y, int z, int length, char symbol); }

    public void visit(boolean includeSpaces, RunConsumer consumer) {
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < layers.size(); z++) layers.get(z).rows[y].visit(width, y, z, includeSpaces, consumer);
        }
    }

    private record Occurrence(long count, BlockPos last) {}

    private static final class Counter {
        long count;
        final BlockPos.MutableBlockPos last = new BlockPos.MutableBlockPos();
    }

    /** Append-only slice parser; published snapshots are independent of later appends.
     * @author HowXu {@literal <dev@howxu.cn>}
     */
    public static final class Builder {
        private final List<Layer> layers = new ArrayList<>();
        private final Char2ObjectLinkedOpenHashMap<Counter> symbols = new Char2ObjectLinkedOpenHashMap<>();
        private int width, height;
        private PatternRows snapshot;

        public boolean contains(char c) { return symbols.containsKey(c); }

        public void add(String... rows) {
            if (rows.length == 0) throw new IllegalArgumentException("pattern must contain at least one row");
            int w = rows[0].length();
            if (w == 0) throw new IllegalArgumentException("pattern rows must not be empty");
            for (String row : rows) if (row.length() != w) throw new IllegalArgumentException("All rows must have the same width");
            if (!layers.isEmpty() && w != width) throw new IllegalArgumentException("All pattern slices must have the same width");
            if (!layers.isEmpty() && rows.length != height) throw new IllegalArgumentException("All pattern slices must have the same height");
            width = w;
            height = rows.length;
            Row[] parsed = new Row[height];
            for (int y = 0; y < height; y++) parsed[y] = parse(rows[y], y, layers.size());
            layers.add(new Layer(parsed, width));
            snapshot = null;
        }

        private Row parse(String text, int y, int z) {
            IntArrayList ranges = null;
            StringBuilder payload = null;
            int span = 0;
            for (int x = 0; x < width;) {
                char c = text.charAt(x);
                int end = x + 1;
                while (end < width && text.charAt(end) == c) end++;
                Counter counter = symbols.get(c);
                if (counter == null) { counter = new Counter(); symbols.put(c, counter); }
                counter.count += end - x;
                if (counter.last.getY() <= y) counter.last.set(end - 1, y, z);
                if (c == ' ') {
                    if (x == 0 && end == width) return Row.EMPTY;
                    if (ranges == null) { ranges = new IntArrayList(); payload = new StringBuilder(); }
                    if (span < x) { ranges.add(span); ranges.add(x); payload.append(text, span, x); }
                    span = end;
                }
                x = end;
            }
            if (ranges == null) return new Row(text, null);
            if (span < width) { ranges.add(span); ranges.add(width); payload.append(text, span, width); }
            return new Row(payload.toString(), ranges.toIntArray());
        }

        public PatternRows freeze() { return snapshot == null ? (snapshot = new PatternRows(this)) : snapshot; }
    }

    private static final class Layer extends AbstractList<String> implements RandomAccess {
        final Row[] rows;
        final int width;
        Layer(Row[] rows, int width) { this.rows = rows; this.width = width; }
        @Override public int size() { return rows.length; }
        @Override public String get(int y) { return rows[y].text(width); }
    }

    private record Row(String payload, int[] ranges) {
        static final Row EMPTY = new Row("", new int[0]);
        String text(int width) {
            if (ranges == null) return payload;
            char[] out = new char[width];
            Arrays.fill(out, ' ');
            for (int r = 0, p = 0; r < ranges.length; r += 2) {
                int len = ranges[r + 1] - ranges[r];
                payload.getChars(p, p + len, out, ranges[r]);
                p += len;
            }
            return new String(out);
        }
        void visit(int width, int y, int z, boolean spaces, RunConsumer consumer) {
            int x = 0, p = 0;
            int spans = ranges == null ? 1 : ranges.length / 2;
            for (int r = 0; r < spans; r++) {
                int start = ranges == null ? 0 : ranges[2 * r];
                int end = ranges == null ? width : ranges[2 * r + 1];
                if (spaces && x < start) consumer.accept(x, y, z, start - x, ' ');
                for (x = start; x < end;) {
                    char c = payload.charAt(p++);
                    int next = x + 1;
                    while (next < end && payload.charAt(p) == c) { next++; p++; }
                    consumer.accept(x, y, z, next - x, c);
                    x = next;
                }
            }
            if (spaces && x < width) consumer.accept(x, y, z, width - x, ' ');
        }
    }
}
