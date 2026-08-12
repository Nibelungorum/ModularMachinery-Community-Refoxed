package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.MachineComponentTile;
import cn.howxu.mmcr.client.model.MachineModelDataKeys;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.multiblock.ComponentClaimPolicy;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.model.data.ModelData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MachinePortAppearanceTest {

    @BeforeAll
    static void setup() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void io_port_defaults_to_basic_casing_base_texture() {
        IOPortBlockEntity port = itemInputBus();

        assertThat(port.appearanceBaseTexture()).isEqualTo(MMCR.id("block/basic_casing"));
    }

    @Test
    void multiple_linked_owners_fall_back_to_basic_casing_for_shared_appearance_components() {
        LinkedAppearanceBlockEntity component = appearanceComponent();
        component.linkControllerAppearance(new BlockPos(0, 64, 0), MMCR.id("block/first"));
        component.linkControllerAppearance(new BlockPos(4, 64, 0), MMCR.id("block/second"));

        assertThat(component.appearanceBaseTexture()).isEqualTo(MMCR.id("block/basic_casing"));
    }

    @Test
    void reset_restores_basic_casing_base_texture() {
        IOPortBlockEntity port = itemInputBus();
        port.setAppearanceBaseTexture(Identifier.parse("kubejs:block/steel_casing"));

        port.resetAppearanceBaseTexture();

        assertThat(port.appearanceBaseTexture()).isEqualTo(MMCR.id("block/basic_casing"));
    }

    @Test
    void io_port_saves_formed_base_texture_to_update_tag() {
        IOPortBlockEntity port = itemInputBus();
        Identifier texture = Identifier.parse("kubejs:block/steel_casing");

        port.bindControllerAppearance(new BlockPos(12, 4, 12), texture);

        var tag = port.getUpdateTag(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        assertThat(tag.getString("AppearanceBaseTexture")).contains(texture.toString());
        var linkedControllers = tag.getListOrEmpty("LinkedControllers");
        assertThat(linkedControllers).hasSize(1);
        var linkedController = linkedControllers.getCompound(0).orElseThrow();
        assertThat(linkedController.getIntOr("X", 0)).isEqualTo(12);
        assertThat(linkedController.getIntOr("Y", 0)).isEqualTo(4);
        assertThat(linkedController.getIntOr("Z", 0)).isEqualTo(12);
        assertThat(linkedController.getStringOr("Texture", "")).isEqualTo(texture.toString());
    }

    @Test
    void io_port_restores_every_linked_controller_from_nbt_with_deterministic_first_owner_appearance() {
        IOPortBlockEntity source = itemInputBus();
        BlockPos first = new BlockPos(0, 64, 0);
        BlockPos second = new BlockPos(4, 64, 0);
        Identifier firstTexture = Identifier.parse("kubejs:block/first_casing");
        Identifier secondTexture = Identifier.parse("kubejs:block/second_casing");
        source.linkControllerAppearance(second, secondTexture);
        source.linkControllerAppearance(first, firstTexture);

        TagValueOutput output = TagValueOutput.createWithContext(
                ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(java.util.stream.Stream.empty()));
        source.saveAdditional(output);
        IOPortBlockEntity restored = itemInputBus();

        restored.loadAdditional(TagValueInput.create(
                ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(java.util.stream.Stream.empty()),
                output.buildResult()));

        assertThat(restored.linkedControllerPositions()).containsExactlyInAnyOrder(first, second);
        assertThat(restored.linkedControllerPos()).isEqualTo(first);
        assertThat(restored.appearanceBaseTexture()).isEqualTo(firstTexture);
    }

    @Test
    void removingOneSharedOwnerKeepsTheOtherOwnersAppearance() {
        IOPortBlockEntity port = itemInputBus();
        BlockPos first = new BlockPos(0, 64, 0);
        BlockPos second = new BlockPos(4, 64, 0);
        Identifier firstTexture = MMCR.id("block/first");
        Identifier secondTexture = MMCR.id("block/second");

        port.linkControllerAppearance(first, firstTexture);
        port.linkControllerAppearance(second, secondTexture);
        port.unlinkControllerAppearance(first);

        assertThat(port.linkedControllerPositions()).containsExactly(second);
        assertThat(port.linkedControllerPos()).isEqualTo(second);
        assertThat(port.appearanceBaseTexture()).isEqualTo(secondTexture);
    }

    @Test
    void ioPortsAreSharedSerializedButSchedulersAreExclusiveByDefault() {
        assertThat(itemInputBus().claimPolicy()).isEqualTo(ComponentClaimPolicy.SHARED_SERIALIZED);
        assertThat(new MachineComponentTile() {
            @Override
            public MachineComponent provideComponent() {
                return null;
            }
        }.claimPolicy()).isEqualTo(ComponentClaimPolicy.EXCLUSIVE);
    }

    @Test
    void controller_link_maintenance_removes_only_invalid_owner_and_keeps_valid_owner_appearance() throws Exception {
        IOPortBlockEntity port = itemInputBus();
        BlockPos invalid = new BlockPos(0, 64, 0);
        BlockPos valid = new BlockPos(4, 64, 0);
        Identifier invalidTexture = MMCR.id("block/invalid");
        Identifier validTexture = MMCR.id("block/valid");
        MachineControllerBlockEntity invalidController = controller(invalid, false, Set.of(port.getBlockPos()));
        MachineControllerBlockEntity validController = controller(valid, true, Set.of(port.getBlockPos()));
        Level level = LevelStub.create(Map.of(
                invalid, invalidController.getBlockState().getBlock(),
                valid, validController.getBlockState().getBlock()),
                List.of(port, invalidController, validController));
        port.setLevel(level);
        invalidController.setLevel(level);
        validController.setLevel(level);
        port.linkControllerAppearance(invalid, invalidTexture);
        port.linkControllerAppearance(valid, validTexture);

        port.serverTick();

        assertThat(port.linkedControllerPositions()).containsExactly(valid);
        assertThat(port.linkedControllerPos()).isEqualTo(valid);
        assertThat(port.appearanceBaseTexture()).isEqualTo(validTexture);
    }

    @Test
    void model_data_exposes_formed_base_texture() {
        IOPortBlockEntity port = itemInputBus();
        Identifier texture = Identifier.parse("kubejs:block/steel_casing");

        port.setAppearanceBaseTexture(texture);
        ModelData data = port.getModelData();

        assertThat(data.get(MachineModelDataKeys.PORT_BASE_TEXTURE)).isEqualTo(texture);
    }

    private static IOPortBlockEntity itemInputBus() {
        return (IOPortBlockEntity) ModBlockEntities.BES.get("item_input_bus").get().create(
                BlockPos.ZERO,
                ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
    }

    private static LinkedAppearanceBlockEntity appearanceComponent() {
        return new LinkedAppearanceBlockEntity(
                ModBlockEntities.BES.get("item_input_bus").get(),
                BlockPos.ZERO,
                ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState()) {
        };
    }

    private static MachineControllerBlockEntity controller(BlockPos pos, boolean formed, Set<BlockPos> linkedPorts) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        MachineControllerBlockEntity controller = (MachineControllerBlockEntity) ((sun.misc.Unsafe) unsafeField.get(null))
                .allocateInstance(MachineControllerBlockEntity.class);
        setField(BlockEntity.class, controller, "worldPosition", pos);
        setField(BlockEntity.class, controller, "blockState", ModBlocks.controllerFor(MMCR.id("blast_furnace")).get().defaultBlockState()
                .setValue(MachineControllerBlock.FORMED, formed));
        setField(MachineControllerBlockEntity.class, controller, "linkedPortPositions", linkedPorts);
        return controller;
    }

    private static void setField(Class<?> type, Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

}
