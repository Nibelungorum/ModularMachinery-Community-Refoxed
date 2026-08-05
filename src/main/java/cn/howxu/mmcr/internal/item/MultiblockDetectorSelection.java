package cn.howxu.mmcr.internal.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Selection stored on the multiblock detector item stack.
 *
 * @author howxu <dev@howxu.cn>
 */
public record MultiblockDetectorSelection(
        @Nullable BlockPos controllerPos,
        @Nullable Direction controllerFace,
        @Nullable BlockPos firstPos,
        @Nullable BlockPos secondPos) {

    public static final MultiblockDetectorSelection EMPTY = new MultiblockDetectorSelection(null, null, null, null);

    public static final Codec<MultiblockDetectorSelection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.optionalFieldOf("controller_pos").forGetter(selection -> Optional.ofNullable(selection.controllerPos())),
            Direction.CODEC.optionalFieldOf("controller_face").forGetter(selection -> Optional.ofNullable(selection.controllerFace())),
            BlockPos.CODEC.optionalFieldOf("first_pos").forGetter(selection -> Optional.ofNullable(selection.firstPos())),
            BlockPos.CODEC.optionalFieldOf("second_pos").forGetter(selection -> Optional.ofNullable(selection.secondPos()))
    ).apply(instance, (controllerPos, controllerFace, firstPos, secondPos) -> new MultiblockDetectorSelection(
            controllerPos.orElse(null), controllerFace.orElse(null), firstPos.orElse(null), secondPos.orElse(null))));

    public static final StreamCodec<RegistryFriendlyByteBuf, MultiblockDetectorSelection> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), selection -> Optional.ofNullable(selection.controllerPos()),
            ByteBufCodecs.optional(Direction.STREAM_CODEC), selection -> Optional.ofNullable(selection.controllerFace()),
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), selection -> Optional.ofNullable(selection.firstPos()),
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), selection -> Optional.ofNullable(selection.secondPos()),
            (controllerPos, controllerFace, firstPos, secondPos) -> new MultiblockDetectorSelection(
                    controllerPos.orElse(null), controllerFace.orElse(null), firstPos.orElse(null), secondPos.orElse(null)));

    public MultiblockDetectorSelection withController(BlockPos pos, Direction face) {
        return new MultiblockDetectorSelection(pos, face, firstPos, secondPos);
    }

    public MultiblockDetectorSelection withFirst(BlockPos pos) {
        return new MultiblockDetectorSelection(controllerPos, controllerFace, pos, secondPos);
    }

    public MultiblockDetectorSelection withSecond(BlockPos pos) {
        return new MultiblockDetectorSelection(controllerPos, controllerFace, firstPos, pos);
    }

    public boolean isComplete() {
        return controllerPos != null && controllerFace != null && firstPos != null && secondPos != null;
    }
}
