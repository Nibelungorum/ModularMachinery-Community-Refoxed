package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.event.RegisterBlockStateModels;
import net.neoforged.neoforge.client.event.RegisterItemModelsEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Runtime model definitions for machine controller and I/O port blocks.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RuntimeMachineModelRegistry {
    private RuntimeMachineModelRegistry() {
    }

    public static void registerBlockStateModels(RegisterBlockStateModels event) {
        event.registerModel(DynamicOverlayModelLoader.CONTROLLER_ID, DynamicOverlayModelLoader.CONTROLLER_CODEC);
        event.registerModel(DynamicOverlayModelLoader.PORT_ID, DynamicOverlayModelLoader.PORT_CODEC);
    }

    public static void registerItemModels(RegisterItemModelsEvent event) {
        event.register(DynamicOverlayItemModel.ID, DynamicOverlayItemModel.CODEC);
    }

    static Stream<Block> dynamicBlocks() {
        return dynamicBlockEntries().values().stream();
    }

    static boolean isDynamicBlock(Block block) {
        return describe(null, block) != null;
    }

    static Map<String, Block> dynamicBlockEntries() {
        Map<String, Block> blocks = new LinkedHashMap<>();
        ModBlocks.BLOCKS.forEach((name, holder) -> {
            if (!holder.isBound()) {
                return;
            }
            Block block = holder.get();
            if (describe(name, block) != null) {
                blocks.put(name, block);
            }
        });
        return blocks;
    }

    public static RuntimeBlockStateDefinition dynamicBlockState(Block block) {
        RuntimeBlockModelDescriptor descriptor = describe(null, block);
        if (descriptor != null) return dynamicBlockState(descriptor);
        throw new IllegalArgumentException("Unsupported dynamic machine block: " + block);
    }

    static RuntimeBlockStateDefinition dynamicBlockState(RuntimeBlockModelDescriptor descriptor) {
        if (descriptor.kind() == RuntimeBlockModelDescriptor.Kind.CONTROLLER) {
            return controllerDefinition((MachineControllerBlock) descriptor.block());
        }
        if (descriptor.kind() == RuntimeBlockModelDescriptor.Kind.PORT) {
            return portDefinition((IOPortBlock) descriptor.block());
        }
        return portStyleDefinition(descriptor.block());
    }

    static RuntimeBlockStateDefinition controllerDefinition(MachineControllerBlock block) {
        List<RuntimeVariant> variants = new ArrayList<>();
        for (Direction facing : Direction.values()) {
            for (Direction roll : Direction.Plane.HORIZONTAL) {
                for (boolean formed : List.of(false, true)) {
                    for (boolean active : List.of(false, true)) {
                        variants.add(new RuntimeVariant(
                                "facing=" + facing.getSerializedName()
                                        + ",roll_facing=" + roll.getSerializedName()
                                        + ",formed=" + formed
                                        + ",active=" + active,
                                DynamicOverlayModelLoader.CONTROLLER_ID));
                    }
                }
            }
        }
        return new RuntimeBlockStateDefinition(block.machineId(), variants);
    }

    static RuntimeBlockStateDefinition portDefinition(IOPortBlock block) {
        return new RuntimeBlockStateDefinition(MMCR.id(block.kind().id()),
                List.of(new RuntimeVariant("", DynamicOverlayModelLoader.PORT_ID)));
    }

    static RuntimeBlockStateDefinition portStyleDefinition(Block block) {
        return new RuntimeBlockStateDefinition(BuiltInRegistries.BLOCK.getKey(block),
                List.of(new RuntimeVariant("", DynamicOverlayModelLoader.PORT_ID)));
    }

    static RuntimeBlockModelDescriptor describe(String blockName, Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        String resolvedName = blockName != null ? blockName : id.getPath();
        return RuntimeBlockModelDescriptor.describe(resolvedName, block);
    }

    static String blockStateJson(RuntimeBlockStateDefinition definition) {
        StringBuilder json = new StringBuilder("{\n  \"variants\": {");
        for (int i = 0; i < definition.variants().size(); i++) {
            RuntimeVariant variant = definition.variants().get(i);
            json.append(i == 0 ? "\n" : ",\n")
                    .append("    \"").append(variant.state()).append("\": {\n")
                    .append("      \"type\": \"").append(variant.modelId()).append("\"\n")
                    .append("    }");
        }
        return json.append("\n  }\n}\n").toString();
    }

    static String itemDefinitionJson() {
        return "{\n  \"model\": {\n    \"type\": \"" + DynamicOverlayItemModel.ID + "\"\n  }\n}\n";
    }

    public record RuntimeBlockStateDefinition(Identifier id, List<RuntimeVariant> variants) {}

    public record RuntimeVariant(String state, Identifier modelId) {}
}
