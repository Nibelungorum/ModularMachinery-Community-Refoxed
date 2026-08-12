package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SmartInterfaceBlockEntityTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void bindings_round_trip_and_reject_non_finite_updates() {
        var owner = createSmartInterface();
        assertThat(owner.bind(new BlockPos(1, 2, 3), MMCR.id("test"), "mode", 4F)).isTrue();
        assertThat(owner.bind(new BlockPos(1, 2, 3), MMCR.id("other"), "mode", 2F)).isFalse();
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(java.util.stream.Stream.empty()));
        owner.saveAdditional(output);
        var restored = createSmartInterface();
        restored.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(java.util.stream.Stream.empty()), output.buildResult()));

        assertThat(restored.binding(0)).contains(new SmartInterfaceBlockEntity.Binding(
                new BlockPos(1, 2, 3), MMCR.id("test"), "mode", 4F));
        assertThat(restored.setValue(0, Float.NaN)).isFalse();
    }

    @Test
    void bind_with_server_level_stub_does_not_post_without_kubejs_listeners() {
        var smartInterface = createSmartInterface();
        smartInterface.setLevel(LevelStub.createWithBlockEntities(List.of(smartInterface)));

        assertThatCode(() -> smartInterface.bind(BlockPos.ZERO, MMCR.id("test"), "mode", 4F))
                .doesNotThrowAnyException();
    }

    @Test
    void one_formed_binding_uses_its_machine_casing() throws Exception {
        var smartInterface = createSmartInterface();
        BlockPos controllerPos = new BlockPos(2, 64, 2);
        smartInterface.bind(controllerPos, MMCR.id("test"), "mode", 1F);
        smartInterface.setLevel(LevelStub.createWithBlockEntities(List.of(
                smartInterface, formedController(controllerPos, MMCR.id("block/steel_casing")))));

        smartInterface.refreshLinkedAppearance();

        assertThat(smartInterface.appearanceBaseTexture()).isEqualTo(MMCR.id("block/steel_casing"));
    }

    @Test
    void multiple_formed_bindings_fall_back_to_basic_casing() throws Exception {
        var smartInterface = createSmartInterface();
        BlockPos first = new BlockPos(2, 64, 2);
        BlockPos second = new BlockPos(4, 64, 2);
        smartInterface.bind(first, MMCR.id("first"), "first_mode", 1F);
        smartInterface.bind(second, MMCR.id("second"), "second_mode", 1F);
        smartInterface.setLevel(LevelStub.createWithBlockEntities(List.of(smartInterface,
                formedController(first, MMCR.id("block/steel_casing")),
                formedController(second, MMCR.id("block/brass_casing")))));

        smartInterface.refreshLinkedAppearance();

        assertThat(smartInterface.appearanceBaseTexture()).isEqualTo(MMCR.id("block/basic_casing"));
    }

    @Test
    void unformed_or_missing_binding_falls_back_to_basic_casing() throws Exception {
        var smartInterface = createSmartInterface();
        BlockPos controllerPos = new BlockPos(2, 64, 2);
        smartInterface.bind(controllerPos, MMCR.id("test"), "mode", 1F);
        smartInterface.setLevel(LevelStub.createWithBlockEntities(List.of(
                smartInterface, unformedController(controllerPos))));

        smartInterface.refreshLinkedAppearance();

        assertThat(smartInterface.appearanceBaseTexture()).isEqualTo(MMCR.id("block/basic_casing"));
    }

    private static SmartInterfaceBlockEntity createSmartInterface() {
        return (SmartInterfaceBlockEntity) ModBlockEntities.SMART_INTERFACE.get().create(
                BlockPos.ZERO, ModBlocks.SMART_INTERFACE.get().defaultBlockState());
    }

    private static MachineControllerBlockEntity formedController(BlockPos pos, Identifier texture) throws Exception {
        MachineControllerBlockEntity controller = unformedController(pos);
        setField(BlockEntity.class, controller, "blockState", ModBlocks.controllerFor(MMCR.id("blast_furnace")).get()
                .defaultBlockState().setValue(MachineControllerBlock.FORMED, true));
        setField(MachineControllerBlockEntity.class, controller, "foundMachine", machine(texture));
        return controller;
    }

    private static MachineControllerBlockEntity unformedController(BlockPos pos) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        MachineControllerBlockEntity controller = (MachineControllerBlockEntity) ((sun.misc.Unsafe) unsafeField.get(null))
                .allocateInstance(MachineControllerBlockEntity.class);
        setField(BlockEntity.class, controller, "worldPosition", pos);
        setField(BlockEntity.class, controller, "blockState", ModBlocks.controllerFor(MMCR.id("blast_furnace")).get()
                .defaultBlockState().setValue(MachineControllerBlock.FORMED, false));
        return controller;
    }

    private static Machine machine(Identifier texture) {
        return new Machine() {
            @Override public Identifier registryName() { return MMCR.id("test"); }
            @Override public String localizedName() { return "Test"; }
            @Override public BlockArray pattern() { return new BlockArray(java.util.Map.of()); }
            @Override public MachineControllerSpec controller() { return MachineControllerSpec.defaultsFor(MMCR.id("test")); }
            @Override public MachineAppearanceSpec appearance() {
                return new MachineAppearanceSpec(MMCR.id("test_casing"), texture, texture);
            }
        };
    }

    private static void setField(Class<?> type, Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
