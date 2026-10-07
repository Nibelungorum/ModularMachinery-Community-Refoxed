package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.compat.pneumaticcraft.loaded.AirInterfaceKind;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
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
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks real air port model definitions and texture resolution without native handler initialization.
 *
 * @author howxu <dev@howxu.cn>
 */
class PneumaticAirModelTest {
    private RegistryFixture fixture;

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrapCapabilities();
    }

    @BeforeEach
    void registerFixtures() throws Exception {
        fixture = new RegistryFixture();
        for (var kind : AirInterfaceKind.values()) {
            Block block = fixtureBlock(kind);
            MappedRegistry<Item> items = (MappedRegistry<Item>) BuiltInRegistries.ITEM;
            items.unfreeze();
            try {
                Registry.register(items, fixtureId(kind), new BlockItem(block, new Item.Properties()));
            } finally {
                items.freeze();
            }
            MappedRegistry<BlockEntityType<?>> types = (MappedRegistry<BlockEntityType<?>>) BuiltInRegistries.BLOCK_ENTITY_TYPE;
            types.unfreeze();
            try {
                Registry.register(types, fixtureId(kind), BlockEntityType.Builder.of(
                        (pos, state) -> new AppearancePort(pos, state, kind), block).build(null));
            } finally {
                types.freeze();
            }
        }
    }

    @AfterEach
    void restoreRegistries() throws Exception {
        if (fixture != null) fixture.close();
        RuntimeMachineModelRegistry.invalidate();
    }

    @Test
    void bothAirDirectionsUseDynamicPortBlockAndItemDefinitions() throws Exception {
        var previousBlocks = new LinkedHashMap<>(ModBlocks.BLOCKS);
        try {
            for (var kind : AirInterfaceKind.values()) {
                Block block = fixtureBlock(kind);
                ModBlocks.BLOCKS.put(kind.id(), DeferredHolder.create(Registries.BLOCK, fixtureId(kind)));
                RuntimeMachineModelRegistry.invalidate();
                RuntimeBlockModelDefinition definition = RuntimeMachineModelRegistry.definition(block);
                assertThat(definition).isNotNull();
                assertThat(definition.modelKind()).isEqualTo(DynamicOverlayBakedModel.Kind.PORT);
                assertThat(definition.blockStateDefinition().id()).isEqualTo(MMCR.id(kind.id()));
                assertThat(definition.blockStateDefinition().variants()).singleElement()
                        .extracting(RuntimeMachineModelRegistry.RuntimeVariant::modelId).isEqualTo(DynamicOverlayModelLoader.PORT_ID);
                var description = DynamicOverlayItemModel.describeItem(BuiltInRegistries.ITEM.get(fixtureId(kind)));
                assertThat(description.kind()).isEqualTo(DynamicOverlayBakedModel.Kind.PORT);
                assertThat(description.portKind()).isSameAs(kind);
                assertThat(description.baseModel()).isEqualTo(MMCR.id("block/dynamic_io_port"));
                assertThat(description.baseTextureSource()).isEqualTo(MachineAppearanceSpec.defaults().formedPortTextureSource());
                assertThat(description.overlayFaces()).isEqualTo(EnumSet.allOf(Direction.class));
                assertThat(description.overlayTextures()).containsExactly(MMCR.id("block/overlay/base/pnc/air"),
                        MMCR.id("block/overlay/direction/" + kind.ioType().getSerializedName()),
                        MMCR.id("block/overlay/tier/normal"));
                for (var texture : description.overlayTextures()) {
                    try (var resource = getClass().getResourceAsStream("/assets/" + texture.getNamespace()
                            + "/textures/" + texture.getPath() + ".png")) {
                        assertThat(resource).as("existing overlay %s", texture).isNotNull();
                    }
                }
            }
        } finally {
            ModBlocks.BLOCKS.clear();
            ModBlocks.BLOCKS.putAll(previousBlocks);
            RuntimeMachineModelRegistry.invalidate();
        }
    }

    @Test
    void formedAppearanceChangesTheCasingButKeepsEveryDirectionalOverlay() {
        var defaultSource = MachineAppearanceSpec.defaults().formedPortTextureSource();
        ResourceLocation casingTexture = ResourceLocation.withDefaultNamespace("block/iron_block");
        var formedSource = new MachineAppearanceSpec.TextureSource(ResourceLocation.withDefaultNamespace("iron_block"), casingTexture);
        for (var kind : AirInterfaceKind.values()) {
            var port = (AppearancePort) BuiltInRegistries.BLOCK_ENTITY_TYPE.get(fixtureId(kind))
                    .create(BlockPos.ZERO, fixtureBlock(kind).defaultBlockState());
            var overlays = DynamicOverlayTextures.portOverlayTexture(kind);
            assertThat(port.getModelData().get(MachineModelDataKeys.PORT_LINKED)).isFalse();
            assertThat(port.getModelData().get(MachineModelDataKeys.PORT_TEXTURE_SOURCE)).isEqualTo(defaultSource);
            var unformed = DynamicOverlayBakedModel.portTextures(null,
                    port.getModelData().get(MachineModelDataKeys.PORT_TEXTURE_SOURCE), overlays);
            port.linkControllerAppearanceSource(BlockPos.ZERO.above(), formedSource);
            assertThat(port.getModelData().get(MachineModelDataKeys.PORT_LINKED)).isTrue();
            assertThat(port.getModelData().get(MachineModelDataKeys.PORT_TEXTURE_SOURCE)).isEqualTo(formedSource);
            var formed = DynamicOverlayBakedModel.portTextures(null,
                    port.getModelData().get(MachineModelDataKeys.PORT_TEXTURE_SOURCE), overlays);
            assertThat(unformed.base()).isEqualTo(DynamicOverlayBakedModel.FaceTextures.uniform(MMCR.id("block/ctm/basic_casing/particle")));
            assertThat(formed.base()).isEqualTo(DynamicOverlayBakedModel.FaceTextures.uniform(casingTexture));
            assertThat(formed.overlays()).isEqualTo(unformed.overlays())
                    .contains(MMCR.id("block/overlay/direction/" + kind.ioType().getSerializedName()));
            port.unlinkControllerAppearance(BlockPos.ZERO.above());
            assertThat(port.getModelData().get(MachineModelDataKeys.PORT_LINKED)).isFalse();
            assertThat(port.getModelData().get(MachineModelDataKeys.PORT_TEXTURE_SOURCE)).isEqualTo(defaultSource);
            var reset = DynamicOverlayBakedModel.portTextures(null,
                    port.getModelData().get(MachineModelDataKeys.PORT_TEXTURE_SOURCE), overlays);
            assertThat(reset).isEqualTo(unformed);
        }
        assertThat(DynamicOverlayTextures.portOverlayTexture(AirInterfaceKind.INPUT))
                .isNotEqualTo(DynamicOverlayTextures.portOverlayTexture(AirInterfaceKind.OUTPUT));
    }

    private static ResourceLocation fixtureId(AirInterfaceKind kind) {
        return ResourceLocation.fromNamespaceAndPath("mmcr_test", "air_model_" + kind.id());
    }

    private static Block fixtureBlock(AirInterfaceKind kind) {
        MappedRegistry<Block> blocks = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        ResourceLocation id = fixtureId(kind);
        if (blocks.containsKey(id)) return blocks.get(id);
        blocks.unfreeze();
        try {
            return Registry.register(blocks, id, kind.createBlock(Blocks.IRON_BLOCK.properties(),
                    () -> BuiltInRegistries.BLOCK_ENTITY_TYPE.get(id)));
        } finally {
            blocks.freeze();
        }
    }

    /** Uses the same inherited appearance path as native ports without creating a PNC handler.
     * @author howxu <dev@howxu.cn>
     */
    private static final class AppearancePort extends IOPortBlockEntity {
        private final AirInterfaceKind kind;

        private AppearancePort(BlockPos pos, BlockState state, AirInterfaceKind kind) {
            super(BuiltInRegistries.BLOCK_ENTITY_TYPE.get(fixtureId(kind)), pos, state);
            this.kind = kind;
        }

        @Override
        public AirInterfaceKind kind() {
            return kind;
        }

        @Override
        public IOType ioType() {
            return kind.ioType();
        }

        @Override
        public CapabilitySnapshot capabilitySnapshot() {
            return new CapabilitySnapshot(List.of());
        }
    }

    /** Restores temporary block, item and BE registrations, including intrusive holders.
     * @author howxu <dev@howxu.cn>
     */
    private static final class RegistryFixture implements AutoCloseable {
        private final Map<Object, Map<Field, Object>> fields = new IdentityHashMap<>();
        private final Map<Map<Object, Object>, Map<Object, Object>> maps = new IdentityHashMap<>();
        private final Map<List<Object>, List<Object>> lists = new IdentityHashMap<>();
        private final Map<Block, Item> blockItems = new IdentityHashMap<>(Item.BY_BLOCK);

        @SuppressWarnings("unchecked")
        private RegistryFixture() throws IllegalAccessException {
            for (var registry : List.of(BuiltInRegistries.BLOCK, BuiltInRegistries.ITEM, BuiltInRegistries.BLOCK_ENTITY_TYPE)) {
                Map<Field, Object> values = new LinkedHashMap<>();
                fields.put(registry, values);
                for (Field field : MappedRegistry.class.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    field.setAccessible(true);
                    Object value = field.get(registry);
                    values.put(field, value);
                    if (value instanceof Map<?, ?> map) {
                        maps.put((Map<Object, Object>) map, new LinkedHashMap<>((Map<Object, Object>) map));
                    } else if (value instanceof List<?> list) {
                        lists.put((List<Object>) list, new ArrayList<>((List<Object>) list));
                    }
                }
            }
        }

        @Override
        public void close() throws IllegalAccessException {
            maps.forEach((map, original) -> {
                if (!map.equals(original)) {
                    map.clear();
                    map.putAll(original);
                }
            });
            lists.forEach((list, original) -> {
                if (!list.equals(original)) {
                    list.clear();
                    list.addAll(original);
                }
            });
            for (var registry : fields.entrySet()) {
                for (var entry : registry.getValue().entrySet()) {
                    if (entry.getKey().get(registry.getKey()) != entry.getValue()) {
                        entry.getKey().set(registry.getKey(), entry.getValue());
                    }
                }
            }
            Item.BY_BLOCK.clear();
            Item.BY_BLOCK.putAll(blockItems);
        }
    }
}
