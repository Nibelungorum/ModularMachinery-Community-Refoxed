package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.internal.block.DataStorageBlock;
import cn.howxu.mmcr.internal.block.FactorySchedulerBlock;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.block.ModuleCouplerBlock;
import cn.howxu.mmcr.internal.block.ParallelControllerBlock;
import cn.howxu.mmcr.internal.block.SmartInterfaceBlock;
import cn.howxu.mmcr.internal.block.UpgradeBusBlock;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.event.RegisterBlockStateModels;
import net.neoforged.neoforge.client.event.RegisterItemModelsEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

/**
 * Runtime model definitions for machine controller and I/O port blocks.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RuntimeMachineModelRegistry {
    private static volatile @Nullable Map<Block, RuntimeBlockModelDefinition> definitions;

    private RuntimeMachineModelRegistry() {
    }

    public static synchronized void invalidate() {
        definitions = null;
    }

    public static void registerBlockStateModels(RegisterBlockStateModels event) {
        event.registerModel(DynamicOverlayModelLoader.CONTROLLER_ID, DynamicOverlayModelLoader.CONTROLLER_CODEC);
        event.registerModel(DynamicOverlayModelLoader.PORT_ID, DynamicOverlayModelLoader.PORT_CODEC);
    }

    public static void registerItemModels(RegisterItemModelsEvent event) {
        event.register(DynamicOverlayItemModel.ID, DynamicOverlayItemModel.CODEC);
    }

    static Stream<Block> dynamicBlocks() {
        return definitions().map(RuntimeBlockModelDefinition::block);
    }

    static boolean isDynamicBlock(Block block) {
        return definition(block) != null;
    }

    static Map<String, Block> dynamicBlockEntries() {
        Map<String, Block> blocks = new LinkedHashMap<>();
        definitions().forEach(definition -> blocks.put(definition.blockName(), definition.block()));
        return blocks;
    }

    static @Nullable RuntimeBlockModelDefinition definition(Block block) {
        return definitionMap().get(block);
    }

    static Stream<RuntimeBlockModelDefinition> definitions() {
        return definitionMap().values().stream();
    }

    private static Map<Block, RuntimeBlockModelDefinition> definitionMap() {
        Map<Block, RuntimeBlockModelDefinition> cached = definitions;
        if (cached != null) {
            return cached;
        }
        synchronized (RuntimeMachineModelRegistry.class) {
            cached = definitions;
            if (cached != null) {
                return cached;
            }
            return buildDefinitionMap();
        }
    }

    private static Map<Block, RuntimeBlockModelDefinition> buildDefinitionMap() {
        boolean allBound = ModBlocks.BLOCKS.values().stream().allMatch(holder -> holder.isBound());
        Map<Block, RuntimeBlockModelDefinition> resolved = new LinkedHashMap<>();
        ModBlocks.BLOCKS.forEach((name, holder) -> {
            if (!holder.isBound()) {
                return;
            }
            RuntimeBlockModelDefinition definition = definition(name, holder.get());
            if (definition != null) {
                resolved.put(definition.block(), definition);
            }
        });
        if (!allBound) {
            return resolved;
        }
        Map<Block, RuntimeBlockModelDefinition> cached = Collections.unmodifiableMap(resolved);
        definitions = cached;
        return cached;
    }

    public static RuntimeBlockStateDefinition dynamicBlockState(Block block) {
        RuntimeBlockModelDefinition definition = definition(block);
        if (definition != null) return definition.blockStateDefinition();
        throw new IllegalArgumentException("Unsupported dynamic machine block: " + block);
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

    private static @Nullable RuntimeBlockModelDefinition definition(String blockName, Block block) {
        if (block instanceof MachineControllerBlock controller) {
            return new RuntimeBlockModelDefinition(
                    block,
                    blockName,
                    DynamicOverlayBakedModel.Kind.CONTROLLER,
                    controllerDefinition(controller),
                    DynamicOverlayItemModel.Description.controller(controller.machineId()));
        }
        if (block instanceof IOPortBlock port) {
            return new RuntimeBlockModelDefinition(
                    block,
                    blockName,
                    DynamicOverlayBakedModel.Kind.PORT,
                    portDefinition(port),
                    DynamicOverlayItemModel.Description.port(port.kind()));
        }
        if (block instanceof DataStorageBlock) {
            return new RuntimeBlockModelDefinition(
                    block,
                    blockName,
                    DynamicOverlayBakedModel.Kind.PORT,
                    portStyleDefinition(block),
                    DynamicOverlayItemModel.Description.portOverlay(MMCR.id("block/overlay_data_storage")));
        }
        if (block instanceof UpgradeBusBlock) {
            return new RuntimeBlockModelDefinition(
                    block,
                    blockName,
                    DynamicOverlayBakedModel.Kind.PORT,
                    portStyleDefinition(block),
                    DynamicOverlayItemModel.Description.portOverlay(MMCR.id("block/overlay_data_storage")));
        }
        if (block instanceof ParallelControllerBlock || block instanceof FactorySchedulerBlock
                || block instanceof SmartInterfaceBlock || block instanceof ModuleCouplerBlock) {
            Identifier overlay = block instanceof ParallelControllerBlock parallel
                    ? parallelControllerOverlayTexture(parallel.tier())
                    : block instanceof SmartInterfaceBlock
                            ? MMCR.id("block/overlay_smartinterface_number")
                            : block instanceof ModuleCouplerBlock
                                    ? MMCR.id("block/overlay_module_bridge")
                                    : MMCR.id("block/overlay_factory_controller");
            return new RuntimeBlockModelDefinition(
                    block,
                    blockName,
                    DynamicOverlayBakedModel.Kind.PORT,
                    portStyleDefinition(block),
                    DynamicOverlayItemModel.Description.portOverlay(overlay));
        }
        return null;
    }

    private static Identifier parallelControllerOverlayTexture(ParallelTier tier) {
        return switch (tier) {
            case NORMAL -> MMCR.id("block/overlay_parallel_controller_normal");
            case PLUS -> MMCR.id("block/overlay_parallel_controller_plus");
            case REINFORCED -> MMCR.id("block/overlay_parallel_controller_reinforced");
            case PRO -> MMCR.id("block/overlay_parallel_controller_pro");
            case ELITE -> MMCR.id("block/overlay_parallel_controller_elite");
            case FANTASY -> MMCR.id("block/overlay_parallel_controller_fantasy");
            case MAX -> MMCR.id("block/overlay_parallel_controller_max");
            case ULTIMATE -> MMCR.id("block/overlay_parallel_controller_ultimate");
        };
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

    public record RuntimeBlockStateDefinition(Identifier id, List<RuntimeVariant> variants) {
        public RuntimeBlockStateDefinition {
            variants = List.copyOf(variants);
        }
    }

    public record RuntimeVariant(String state, Identifier modelId) {}
}
