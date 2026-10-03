package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.compat.fluxnetworks.FluxNetworksTestBootstrap;
import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkInterfaceBlockEntity;
import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkInterfaceKind;
import cn.howxu.mmcr.internal.tile.NetworkInterfaceBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies dynamic model registration for network and native Flux interfaces.
 * @author howxu <dev@howxu.cn>
 */
class RuntimeMachineModelRegistryTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        FluxNetworksTestBootstrap.bootstrap();
        for (var kind : FluxNetworkInterfaceKind.values()) {
            ResourceLocation id = fluxModelId(kind);
            MappedRegistry<Block> blocks = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
            if (!blocks.containsKey(id)) {
                blocks.unfreeze();
                try {
                    Registry.register(blocks, id, kind.createBlock(Blocks.IRON_BLOCK.properties(),
                            () -> ModBlockEntities.BES.get(kind.id()).get()));
                } finally {
                    blocks.freeze();
                }
            }
            Block block = blocks.get(id);
            MappedRegistry<Item> items = (MappedRegistry<Item>) BuiltInRegistries.ITEM;
            if (!items.containsKey(id)) {
                items.unfreeze();
                try {
                    Registry.register(items, id, new BlockItem(block, new Item.Properties()));
                } finally {
                    items.freeze();
                }
            }
            MappedRegistry<BlockEntityType<?>> types = (MappedRegistry<BlockEntityType<?>>) BuiltInRegistries.BLOCK_ENTITY_TYPE;
            if (!types.containsKey(id)) {
                types.unfreeze();
                try {
                    Registry.register(types, id, BlockEntityType.Builder.of(kind.entityFactory(), block).build(null));
                } finally {
                    types.freeze();
                }
            }
        }
    }

    private static ResourceLocation fluxModelId(FluxNetworkInterfaceKind kind) {
        return ResourceLocation.fromNamespaceAndPath("mmcr_test", "flux_model_" + kind.id());
    }

    @Test
    void network_interface_is_a_dynamic_port_style_block() {
        Block networkInterface = ModBlocks.NETWORK_INTERFACE.get();

        RuntimeBlockModelDefinition definition = RuntimeMachineModelRegistry.definition(networkInterface);

        assertThat(definition).isNotNull();
        assertThat(definition.blockStateDefinition().variants())
                .singleElement()
                .extracting(RuntimeMachineModelRegistry.RuntimeVariant::modelId)
                .isEqualTo(DynamicOverlayModelLoader.PORT_ID);
    }

    @Test
    void network_interface_item_uses_the_dynamic_port_base_and_overlay_chain() {
        DynamicOverlayItemModel.Description description = DynamicOverlayItemModel.describeItem(
                ModItems.ITEMS.get("network_interface").get());

        assertThat(description.kind()).isEqualTo(DynamicOverlayBakedModel.Kind.PORT);
        assertThat(description.baseModel()).isEqualTo(MMCR.id("block/dynamic_io_port"));
        assertThat(description.baseTextureSource()).isEqualTo(MachineAppearanceSpec.defaults().formedPortTextureSource());
        assertThat(description.overlayTextures()).containsExactly(MMCR.id("block/overlay_network_interface"));
    }

    @Test
    void network_interface_model_data_starts_with_the_basic_casing_texture() {
        NetworkInterfaceBlockEntity entity = (NetworkInterfaceBlockEntity) ModBlockEntities.NETWORK_INTERFACE.get()
                .create(BlockPos.ZERO, ModBlocks.NETWORK_INTERFACE.get().defaultBlockState());

        ModelData data = entity.getModelData();

        assertThat(data.get(MachineModelDataKeys.PORT_TEXTURE_SOURCE))
                .isEqualTo(MachineAppearanceSpec.defaults().formedPortTextureSource());
    }

    @Test
    void native_flux_interfaces_use_dynamic_block_and_item_entries_and_publish_model_data() {
        var previousBlocks = new LinkedHashMap<>(ModBlocks.BLOCKS);
        var previousTypes = new LinkedHashMap<>(ModBlockEntities.BES);
        try {
            for (var kind : FluxNetworkInterfaceKind.values()) {
                ResourceLocation id = fluxModelId(kind);
                Block block = BuiltInRegistries.BLOCK.get(id);
                ModBlocks.BLOCKS.put(kind.id(), DeferredHolder.create(Registries.BLOCK, id));
                ModBlockEntities.BES.put(kind.id(), DeferredHolder.create(Registries.BLOCK_ENTITY_TYPE, id));
                RuntimeMachineModelRegistry.invalidate();

                RuntimeBlockModelDefinition definition = RuntimeMachineModelRegistry.definition(block);
                assertThat(definition).isNotNull();
                assertThat(RuntimeMachineModelRegistry.dynamicBlockEntries()).containsEntry(kind.id(), block);
                assertThat(definition.blockStateDefinition().id()).isEqualTo(MMCR.id(kind.id()));
                assertThat(definition.blockStateDefinition().variants()).singleElement()
                        .extracting(RuntimeMachineModelRegistry.RuntimeVariant::modelId).isEqualTo(DynamicOverlayModelLoader.PORT_ID);
                DynamicOverlayItemModel.Description description = DynamicOverlayItemModel.describeItem(BuiltInRegistries.ITEM.get(id));
                assertThat(description.kind()).isEqualTo(DynamicOverlayBakedModel.Kind.PORT);
                assertThat(description.portKind()).isSameAs(kind);
                assertThat(description.baseModel()).isEqualTo(MMCR.id("block/dynamic_io_port"));
                assertThat(description.baseTextureSource()).isEqualTo(MachineAppearanceSpec.defaults().formedPortTextureSource());
                assertThat(description.overlayTextures()).containsExactly(MMCR.id("block/overlay/base/energy"),
                        MMCR.id("block/overlay/direction/" + kind.ioType().getSerializedName()), MMCR.id("block/overlay/type/energy"));

                FluxNetworkInterfaceBlockEntity entity = (FluxNetworkInterfaceBlockEntity) BuiltInRegistries.BLOCK_ENTITY_TYPE.get(id)
                        .create(BlockPos.ZERO, block.defaultBlockState());
                assertThat(entity).isNotNull();
                ModelData data = entity.getModelData();
                assertThat(data.get(MachineModelDataKeys.PORT_TEXTURE_SOURCE)).isEqualTo(description.baseTextureSource());
                assertThat(data.get(MachineModelDataKeys.PORT_LINKED)).isFalse();
                entity.onMachineFormed(BlockPos.ZERO.above());
                assertThat(entity.getModelData().get(MachineModelDataKeys.PORT_LINKED)).isTrue();
                entity.onMachineUnformed(BlockPos.ZERO.above());
                assertThat(entity.getModelData().get(MachineModelDataKeys.PORT_LINKED)).isFalse();
                assertThat(entity.getModelData().get(MachineModelDataKeys.PORT_TEXTURE_SOURCE)).isEqualTo(description.baseTextureSource());
            }
        } finally {
            ModBlocks.BLOCKS.clear();
            ModBlocks.BLOCKS.putAll(previousBlocks);
            ModBlockEntities.BES.clear();
            ModBlockEntities.BES.putAll(previousTypes);
            RuntimeMachineModelRegistry.invalidate();
        }
    }
}
