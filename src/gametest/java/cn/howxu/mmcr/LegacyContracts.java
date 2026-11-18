package cn.howxu.mmcr;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Frozen saves from the unmodified 634bbd275 serializers, captured in real server scenarios.
 * @author howxu <dev@howxu.cn>
 */
public final class LegacyContracts {
    private LegacyContracts() {}
    public static CompoundTag load(String name) {
        var path = "/fixtures/634bbd275/" + name + ".snbt";
        try (var input = Objects.requireNonNull(LegacyContracts.class.getResourceAsStream(path), path)) {
            return TagParser.parseTag(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception failure) {
            throw new AssertionError("Invalid migration fixture " + path, failure);
        }
    }
}
