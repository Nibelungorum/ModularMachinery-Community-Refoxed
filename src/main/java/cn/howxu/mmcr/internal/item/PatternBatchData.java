package cn.howxu.mmcr.internal.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** World- and AE-independent identity of a virtual pattern batch.
 * @author howxu <dev@howxu.cn>
 */
public record PatternBatchData(int version, ResourceLocation recipePoolId, ResourceLocation recipeId,
                               long recipeCraftsPerPattern, long multiplier) {
    public static final int VERSION = 1;
    private static final PatternBatchData INVALID = new PatternBatchData(0,
            ResourceLocation.parse("mmcr:invalid_pattern_batch"), ResourceLocation.parse("mmcr:invalid_pattern_batch"), 1, 1);
    private static final Codec<Long> POSITIVE_LONG_CODEC = Codec.LONG.validate(amount -> amount > 0L
            ? DataResult.success(amount) : DataResult.error(() -> "Pattern batch quantities must be positive"));

    // Preserve unknown versions and malformed markers: a partial component map must not turn a batch into a bare job.
    public static final Codec<PatternBatchData> CODEC = RecordCodecBuilder.<PatternBatchData>create(instance -> instance.group(
            Codec.INT.fieldOf("version").forGetter(PatternBatchData::version),
            ResourceLocation.CODEC.fieldOf("recipe_pool").forGetter(PatternBatchData::recipePoolId),
            ResourceLocation.CODEC.fieldOf("recipe").forGetter(PatternBatchData::recipeId),
            POSITIVE_LONG_CODEC.fieldOf("recipe_crafts_per_pattern").forGetter(PatternBatchData::recipeCraftsPerPattern),
            POSITIVE_LONG_CODEC.fieldOf("multiplier").forGetter(PatternBatchData::multiplier)
    ).apply(instance, PatternBatchData::new)).orElse(INVALID);

    public static final StreamCodec<RegistryFriendlyByteBuf, PatternBatchData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PatternBatchData::version,
            ResourceLocation.STREAM_CODEC, PatternBatchData::recipePoolId,
            ResourceLocation.STREAM_CODEC, PatternBatchData::recipeId,
            ByteBufCodecs.VAR_LONG, PatternBatchData::recipeCraftsPerPattern,
            ByteBufCodecs.VAR_LONG, PatternBatchData::multiplier,
            PatternBatchData::new);

    public PatternBatchData {
        Objects.requireNonNull(recipePoolId, "recipePoolId");
        Objects.requireNonNull(recipeId, "recipeId");
        if (recipeCraftsPerPattern < 1 || multiplier < 1) {
            throw new IllegalArgumentException("Pattern batch quantities must be positive");
        }
    }
}
