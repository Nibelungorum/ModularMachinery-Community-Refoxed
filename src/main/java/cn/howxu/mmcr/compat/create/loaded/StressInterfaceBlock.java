package cn.howxu.mmcr.compat.create.loaded;

import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.MachinePort;
import cn.howxu.mmcr.client.model.MachineModelDataKeys;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.AbstractShaftBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/** @author howxu <dev@howxu.cn> */
public final class StressInterfaceBlock extends AbstractShaftBlock implements MachinePort {
    private final StressInterfaceKind kind;
    private final Supplier<? extends BlockEntityType<?>> type;

    public StressInterfaceBlock(StressInterfaceKind kind, Supplier<? extends BlockEntityType<?>> type, Properties properties) {
        super(properties.strength(3.5F).sound(SoundType.METAL).noOcclusion());
        this.kind = kind;
        this.type = type;
    }

    @Override public IOPortKind kind() { return kind; }

    @Override
    public BlockState getAppearance(BlockState state, BlockAndTintGetter level, BlockPos pos, Direction side,
                                    @Nullable BlockState sourceState, @Nullable BlockPos sourcePos) {
        KineticBlockEntity entity = getBlockEntity(level, pos);
        if (entity == null) return state;
        var data = entity.getModelData();
        var source = data.get(MachineModelDataKeys.PORT_TEXTURE_SOURCE);
        if (!Boolean.TRUE.equals(data.get(MachineModelDataKeys.PORT_LINKED))
                || source == null || source.overrideTexture() != null) return state;
        BlockState appearance = BuiltInRegistries.BLOCK.get(source.blockId()).defaultBlockState();
        return Block.isShapeFullBlock(appearance.getShape(level, pos)) ? appearance : state;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<KineticBlockEntity> getBlockEntityClass() {
        return (Class<KineticBlockEntity>) (Class<?>) (kind == StressInterfaceKind.INPUT
                ? StressInputBlockEntity.class : StressOutputBlockEntity.class);
    }

    @SuppressWarnings("unchecked")
    @Override
    public BlockEntityType<? extends KineticBlockEntity> getBlockEntityType() {
        return (BlockEntityType<? extends KineticBlockEntity>) type.get();
    }
}
