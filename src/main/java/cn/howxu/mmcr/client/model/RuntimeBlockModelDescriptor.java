package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.block.FactorySchedulerBlock;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.block.ParallelControllerBlock;
import cn.howxu.mmcr.internal.port.IOPortKind;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * Shared client-side description for blocks backed by runtime dynamic models.
 *
 * @author howxu <dev@howxu.cn>
 */
public record RuntimeBlockModelDescriptor(
        Kind kind,
        String blockName,
        Block block,
        @Nullable Identifier machineId,
        @Nullable IOPortKind portKind) {

    public RuntimeBlockModelDescriptor {
        if (kind == null) throw new IllegalArgumentException("kind null");
        if (blockName == null) throw new IllegalArgumentException("blockName null");
        if (block == null) throw new IllegalArgumentException("block null");
    }

    public enum Kind {
        CONTROLLER,
        PORT,
        PORT_STYLE
    }

    public static @Nullable RuntimeBlockModelDescriptor describe(String blockName, Block block) {
        if (block instanceof MachineControllerBlock controller) {
            return new RuntimeBlockModelDescriptor(Kind.CONTROLLER, blockName, block, controller.machineId(), null);
        }
        if (block instanceof IOPortBlock port) {
            return new RuntimeBlockModelDescriptor(Kind.PORT, blockName, block, MMCR.id(port.kind().id()), port.kind());
        }
        if (block instanceof ParallelControllerBlock || block instanceof FactorySchedulerBlock) {
            return new RuntimeBlockModelDescriptor(Kind.PORT_STYLE, blockName, block, MMCR.id(blockName), null);
        }
        return null;
    }

    public Identifier modelId() {
        return kind == Kind.CONTROLLER ? DynamicOverlayModelLoader.CONTROLLER_ID : DynamicOverlayModelLoader.PORT_ID;
    }
}
