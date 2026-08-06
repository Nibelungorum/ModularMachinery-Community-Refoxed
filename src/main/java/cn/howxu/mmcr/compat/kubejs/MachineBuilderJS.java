package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import dev.latvian.mods.kubejs.registry.BuilderBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

public class MachineBuilderJS extends BuilderBase<DynamicMachine> {
    public transient String localizedName = "Unknown Machine";
    public transient BlockArray pattern = new BlockArray(Map.of());
    public transient Identifier controllerFrontTexture;
    public transient Identifier controllerSideTexture;
    public transient Identifier controllerTopTexture;
    public transient Identifier controllerBottomTexture;
    public transient boolean allowVerticalFacing = false;
    public transient boolean fullyRotationallySymmetric = false;
    public transient boolean requireVerticalFacing = false;

    public MachineBuilderJS(Identifier id) {
        super(id);
    }

    public MachineBuilderJS(String id) {
        this(Identifier.parse(id));
    }

    public MachineBuilderJS localizedName(String name) {
        this.localizedName = name;
        return this;
    }

    public MachineBuilderJS pattern(String grid, Map<String, Object> keys) {
        var blocks = new HashMap<BlockPos, BlockPredicate>();
        var rows = grid.trim().split("\\s+");

        for (int y = 0; y < rows.length; y++) {
            var row = rows[y];

            for (int x = 0; x < row.length(); x++) {
                var key = row.charAt(x);

                if (key == '_' || key == '.') {
                    continue;
                }

                var value = keys.get(String.valueOf(key));

                if (value == null) {
                    continue;
                }

                blocks.put(new BlockPos(x, y, 0), toPredicate(value));
            }
        }

        pattern = new BlockArray(Map.copyOf(blocks));
        return this;
    }

    @Override
    public DynamicMachine createObject() {
        return new DynamicMachine(id, localizedName, pattern, controllerSpec());
    }

    public MachineBuilderJS controllerTextures(String front, String otherFive) {
        return controllerTextures(Identifier.parse(front), Identifier.parse(otherFive));
    }

    public MachineBuilderJS controllerTextures(Identifier front, Identifier otherFive) {
        this.controllerFrontTexture = front;
        this.controllerSideTexture = otherFive;
        this.controllerTopTexture = otherFive;
        this.controllerBottomTexture = otherFive;
        return this;
    }

    public MachineBuilderJS controllerTextures(Identifier front, Identifier side, Identifier top, Identifier bottom) {
        this.controllerFrontTexture = front;
        this.controllerSideTexture = side;
        this.controllerTopTexture = top;
        this.controllerBottomTexture = bottom;
        return this;
    }

    public MachineBuilderJS controllerFrontTexture(String texture) {
        return controllerFrontTexture(Identifier.parse(texture));
    }

    public MachineBuilderJS controllerFrontTexture(Identifier texture) {
        this.controllerFrontTexture = texture;
        return this;
    }

    public MachineBuilderJS controllerSideTexture(String texture) {
        return controllerSideTexture(Identifier.parse(texture));
    }

    public MachineBuilderJS controllerSideTexture(Identifier texture) {
        this.controllerSideTexture = texture;
        return this;
    }

    public MachineBuilderJS controllerTopTexture(String texture) {
        return controllerTopTexture(Identifier.parse(texture));
    }

    public MachineBuilderJS controllerTopTexture(Identifier texture) {
        this.controllerTopTexture = texture;
        return this;
    }

    public MachineBuilderJS controllerBottomTexture(String texture) {
        return controllerBottomTexture(Identifier.parse(texture));
    }

    public MachineBuilderJS controllerBottomTexture(Identifier texture) {
        this.controllerBottomTexture = texture;
        return this;
    }

    public MachineBuilderJS allowVerticalFacing() {
        return allowVerticalFacing(true);
    }

    public MachineBuilderJS allowVerticalFacing(boolean allow) {
        this.allowVerticalFacing = allow;
        return this;
    }

    public MachineBuilderJS fullyRotationallySymmetric() {
        return fullyRotationallySymmetric(true);
    }

    public MachineBuilderJS fullyRotationallySymmetric(boolean symmetric) {
        this.fullyRotationallySymmetric = symmetric;
        return this;
    }

    public MachineBuilderJS requireVerticalFacing() {
        return requireVerticalFacing(true);
    }

    public MachineBuilderJS requireVerticalFacing(boolean required) {
        this.requireVerticalFacing = required;
        if (required) this.allowVerticalFacing = true;
        return this;
    }

    public void registerObject() {
        var machine = createObject();
        MachineDefinitions.register(machine);
        MachineRegistry.register(machine);
    }

    public MachineBuilderJS register() {
        registerObject();
        return this;
    }

    private static BlockPredicate toPredicate(Object value) {
        return switch (value) {
            case String blockId -> new BlockPredicate.OfBlock(BuiltInRegistries.BLOCK.getValue(Identifier.parse(blockId)));
            case Block block -> new BlockPredicate.OfBlock(block);
            case BlockState state -> new BlockPredicate.OfBlockState(state);
            case BlockPredicate predicate -> predicate;
            default -> throw new IllegalArgumentException("Unknown pattern key value: " + value);
        };
    }

    private MachineControllerSpec controllerSpec() {
        MachineControllerSpec defaults = MachineControllerSpec.defaultsFor(id);
        return new MachineControllerSpec(
                defaults.id(),
                controllerFrontTexture != null ? controllerFrontTexture : defaults.frontTexture(),
                controllerSideTexture != null ? controllerSideTexture : defaults.sideTexture(),
                controllerTopTexture != null ? controllerTopTexture : defaults.topTexture(),
                controllerBottomTexture != null ? controllerBottomTexture : defaults.bottomTexture(),
                allowVerticalFacing,
                fullyRotationallySymmetric,
                requireVerticalFacing);
    }
}
