package cn.howxu.mmcr.internal.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;

public class MachineCasingBlock extends Block {
    public MachineCasingBlock(Properties props) {
        super(props.strength(3.5F).sound(SoundType.METAL));
    }
}
