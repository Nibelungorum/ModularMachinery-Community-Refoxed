package cn.howxu.mmcr.compat.jade;

import cn.howxu.mmcr.internal.runtime.JadeTextSnapshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.nbt.NbtOps;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Encodes and decodes custom machine text for Jade server data.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class JadeTextCodec {
    private static final String LINES_KEY = "mmcr_jade_text";
    private static final String ID_KEY = "id";
    private static final String TEXT_KEY = "text";

    private JadeTextCodec() {
    }

    public static void write(CompoundTag data, JadeTextSnapshot snapshot) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(snapshot, "snapshot");
        ListTag lines = new ListTag();
        for (JadeTextSnapshot.Line line : snapshot.lines()) {
            CompoundTag encodedLine = new CompoundTag();
            encodedLine.putString(ID_KEY, line.lineId().toString());
            ComponentSerialization.CODEC.encodeStart(NbtOps.INSTANCE, line.text())
                    .result()
                    .ifPresent(value -> encodedLine.put(TEXT_KEY, value));
            if (encodedLine.contains(TEXT_KEY)) lines.add(encodedLine);
        }
        data.put(LINES_KEY, lines);
    }

    public static List<Component> read(CompoundTag data) {
        if (data == null) return List.of();
        ListTag lines = data.getListOrEmpty(LINES_KEY);
        List<Component> decoded = new ArrayList<>();
        for (int index = 0; index < Math.min(lines.size(), JadeTextSnapshot.MAX_LINES); index++) {
            CompoundTag encodedLine = lines.getCompoundOrEmpty(index);
            String serializedId = encodedLine.getStringOr(ID_KEY, "");
            Tag encodedText = encodedLine.get(TEXT_KEY);
            if (serializedId.isEmpty() || encodedText == null) continue;
            try {
                Identifier.parse(serializedId);
            } catch (RuntimeException ignored) {
                continue;
            }
            ComponentSerialization.CODEC.parse(NbtOps.INSTANCE, encodedText)
                    .result()
                    .map(Component::copy)
                    .ifPresent(decoded::add);
        }
        return List.copyOf(decoded);
    }
}
