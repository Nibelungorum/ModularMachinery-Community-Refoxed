package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.MMCR;
import net.minecraft.resources.Identifier;

/**
 * Startup-declared visual base textures for a machine family.
 *
 * @author howxu <dev@howxu.cn>
 */
public record MachineAppearanceSpec(
        Identifier machineBasicBlock,
        Identifier controllerBaseTexture,
        Identifier formedPortBaseTexture
) {
    public MachineAppearanceSpec {
        if (machineBasicBlock == null) throw new IllegalArgumentException("machineBasicBlock null");
        if (controllerBaseTexture == null) throw new IllegalArgumentException("controllerBaseTexture null");
        if (formedPortBaseTexture == null) throw new IllegalArgumentException("formedPortBaseTexture null");
    }

    public static MachineAppearanceSpec defaults() {
        return new MachineAppearanceSpec(MMCR.id("basic_casing"), MMCR.id("block/basic_casing"), MMCR.id("block/basic_casing"));
    }

    public static MachineAppearanceSpec fromBasicBlock(Identifier blockId) {
        if (blockId == null) throw new IllegalArgumentException("blockId null");
        Identifier texture = Identifier.fromNamespaceAndPath(blockId.getNamespace(), "block/" + blockId.getPath());
        return new MachineAppearanceSpec(blockId, texture, texture);
    }
}
