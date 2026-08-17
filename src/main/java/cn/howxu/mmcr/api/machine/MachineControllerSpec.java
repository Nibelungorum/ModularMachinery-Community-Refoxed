package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.MMCR;
import net.minecraft.resources.Identifier;

import java.util.List;

public record MachineControllerSpec(
        Identifier id,
        Identifier frontTexture,
        Identifier sideTexture,
        Identifier topTexture,
        Identifier bottomTexture,
        boolean allowVerticalFacing,
        boolean fullyRotationallySymmetric,
        boolean requireVerticalFacing,
        List<String> tooltip) {

    public MachineControllerSpec(
            Identifier id,
            Identifier frontTexture,
            Identifier sideTexture,
            Identifier topTexture,
            Identifier bottomTexture,
            boolean allowVerticalFacing) {
        this(id, frontTexture, sideTexture, topTexture, bottomTexture, allowVerticalFacing, false, false);
    }

    public MachineControllerSpec(
            Identifier id,
            Identifier frontTexture,
            Identifier sideTexture,
            Identifier topTexture,
            Identifier bottomTexture,
            boolean allowVerticalFacing,
            boolean fullyRotationallySymmetric) {
        this(id, frontTexture, sideTexture, topTexture, bottomTexture, allowVerticalFacing, fullyRotationallySymmetric, false);
    }

    public MachineControllerSpec(
            Identifier id,
            Identifier frontTexture,
            Identifier sideTexture,
            Identifier topTexture,
            Identifier bottomTexture,
            boolean allowVerticalFacing,
            boolean fullyRotationallySymmetric,
            boolean requireVerticalFacing) {
        this(id, frontTexture, sideTexture, topTexture, bottomTexture, allowVerticalFacing,
                fullyRotationallySymmetric, requireVerticalFacing, List.of());
    }

    public MachineControllerSpec {
        if (id == null) throw new IllegalArgumentException("id null");
        if (frontTexture == null) throw new IllegalArgumentException("frontTexture null");
        if (sideTexture == null) throw new IllegalArgumentException("sideTexture null");
        if (topTexture == null) throw new IllegalArgumentException("topTexture null");
        if (bottomTexture == null) throw new IllegalArgumentException("bottomTexture null");
        tooltip = tooltip == null ? List.of() : List.copyOf(tooltip);
    }

    public static MachineControllerSpec defaultsFor(Identifier machineId) {
        if (machineId == null) throw new IllegalArgumentException("machineId null");
        String controllerPath = machineId.getPath() + "_controller";
        Identifier controllerId = Identifier.fromNamespaceAndPath(machineId.getNamespace(), controllerPath);
        Identifier basicController = MMCR.id("block/basic_controller");
        Identifier basicCasing = MMCR.id("block/basic_casing");
        return new MachineControllerSpec(
                controllerId,
                basicController,
                basicCasing,
                basicCasing,
                basicCasing,
                false,
                false,
                false);
    }
}
