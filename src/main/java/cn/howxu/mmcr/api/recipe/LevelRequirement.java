package cn.howxu.mmcr.api.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Required machine level for a recipe.
 *
 * @author howxu <dev@howxu.cn>
 */
public record LevelRequirement(Identifier typeId, Identifier levelId) {
    public static final Codec<LevelRequirement> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("type").forGetter(LevelRequirement::typeId),
            Identifier.CODEC.fieldOf("level").forGetter(LevelRequirement::levelId)
    ).apply(instance, LevelRequirement::new));

    public LevelRequirement {
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(levelId, "levelId");
    }
}
