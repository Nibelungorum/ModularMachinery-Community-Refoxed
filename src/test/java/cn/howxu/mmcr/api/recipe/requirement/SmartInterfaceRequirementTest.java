package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.SmartInterfaceType;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContext;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.SmartInterfaceBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author howxu <dev@howxu.cn>
 */
class SmartInterfaceRequirementTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void input_accepts_closed_range_and_output_commits_only_after_simulation() throws Exception {
        var input = SmartInterfaceRequirement.input("mode", 1F, 2F);
        var output = SmartInterfaceRequirement.output("mode", 9F);
        var smartInterface = smartInterface(new BlockPos(1, 0, 0));
        var controller = controllerWith(smartInterface);
        assertThat(smartInterface.bind(controller.getBlockPos(), MMCR.id("test_machine"), "mode", 1.5F)).isTrue();
        var context = new RecipeCraftingContext(controller);

        assertThat(input.simulate(context, 0)).isTrue();
        assertThat(output.simulate(context, 1)).isTrue();
        assertThat(output.commit(context, 1)).isTrue();
        assertThat(smartInterface.binding(0).orElseThrow().value()).isEqualTo(9F);
    }

    @Test
    void output_missing_target_fails_before_input_commit() throws Exception {
        var controller = controllerWith();
        var context = new RecipeCraftingContext(controller);

        assertThat(SmartInterfaceRequirement.output("mode", 9F).simulate(context, 0)).isFalse();
    }

    @Test
    void input_failure_uses_machine_not_equal_message() throws Exception {
        MachineDefinitions.clearForTesting();
        MachineDefinitions.register(MachineRegistration.builder(MMCR.id("test_machine"))
                .smartInterfaceType(new SmartInterfaceType("temperature", 20F, 0, "", "", "",
                        "mmcr.failure.temperature", "", 0))
                .build());
        MachineDefinitions.freezeRegistryPhase();
        SmartInterfaceBlockEntity smart = smartInterface(new BlockPos(1, 0, 0));
        assertThat(smart.claimController(BlockPos.ZERO, MMCR.id("test_machine"), Map.of(
                "temperature", new SmartInterfaceType("temperature", 20F, 0, "", "", "",
                        "mmcr.failure.temperature", "", 0)
        ), false)).isTrue();
        RecipeCraftingContext context = new RecipeCraftingContext(controllerWith(smart));

        boolean result = SmartInterfaceRequirement.input("temperature", 30F).simulate(context, 0);

        assertThat(result).isFalse();
        assertThat(context.getLastFailureUnloc()).isEqualTo("mmcr.failure.temperature");
    }

    @Test
    void codec_round_trips_input_range_and_output_value() {
        var input = SmartInterfaceRequirement.input("mode", 1F, 2F);
        var output = SmartInterfaceRequirement.output("mode", 9F);

        var encodedInput = MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE, input).getOrThrow();
        var encodedOutput = MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE, output).getOrThrow();

        assertThat(encodedInput.toString()).contains("smart_interface", "interface_type", "min_value", "max_value");
        assertThat(MachineRequirement.CODEC.parse(JsonOps.INSTANCE, encodedInput).getOrThrow()).isEqualTo(input);
        assertThat(MachineRequirement.CODEC.parse(JsonOps.INSTANCE, encodedOutput).getOrThrow()).isEqualTo(output);
    }

    @Test
    void input_requirement_matches_only_its_named_interface_type() throws Exception {
        var smartInterface = smartInterface(new BlockPos(1, 0, 0));
        var controller = controllerWith(smartInterface);
        assertThat(smartInterface.claimController(BlockPos.ZERO, MMCR.id("test_machine"), smartTypes(), false)).isTrue();
        assertThat(smartInterface.setValue("Mode", 3F)).isTrue();
        assertThat(smartInterface.setValue("Temperature", 3200F)).isTrue();
        assertThat(smartInterface.setValue("ConversionRate", 0F)).isTrue();
        var context = new RecipeCraftingContext(controller);

        assertThat(SmartInterfaceRequirement.input("Temperature", 3200F).simulate(context, 0)).isTrue();
        assertThat(SmartInterfaceRequirement.input("Temperature", 1600F).simulate(context, 0)).isFalse();
    }

    @Test
    void parallel_input_simulation_does_not_scale_smart_interface_values() throws Exception {
        var smartInterface = smartInterface(new BlockPos(1, 0, 0));
        var controller = controllerWith(smartInterface);
        assertThat(smartInterface.claimController(BlockPos.ZERO, MMCR.id("test_machine"), smartTypes(), false)).isTrue();
        assertThat(smartInterface.setValue("Mode", 2F)).isTrue();
        var recipe = new MachineRecipe(MMCR.id("smart_parallel"), MMCR.id("test_machine"), 20,
                List.of(), List.of(), List.of(), 0, 4, true, List.of(),
                List.of(SmartInterfaceRequirement.input("Mode", 2F)), true, List.of());
        var context = new RecipeCraftingContext(controller);

        assertThat(context.simulateInputs(recipe, 4)).isTrue();
    }

    private static Map<String, SmartInterfaceType> smartTypes() {
        return Map.of(
                "Mode", new SmartInterfaceType("Mode", 1F, 0, "", "", "", "", "", 0,
                        SmartInterfaceType.ValueType.INTEGER),
                "Temperature", new SmartInterfaceType("Temperature", 400F, 1, "", "", "", "", "", 0,
                        SmartInterfaceType.ValueType.INTEGER),
                "ConversionRate", new SmartInterfaceType("ConversionRate", 0.5F, 2, "", "", "", "", "", 0,
                        SmartInterfaceType.ValueType.FLOAT));
    }

    private static SmartInterfaceBlockEntity smartInterface(BlockPos pos) {
        return (SmartInterfaceBlockEntity) ModBlockEntities.SMART_INTERFACE.get().create(pos,
                ModBlocks.SMART_INTERFACE.get().defaultBlockState());
    }

    @SuppressWarnings({"removal", "unchecked"})
    private static MachineControllerBlockEntity controllerWith(SmartInterfaceBlockEntity... smartInterfaces) throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        MachineControllerBlockEntity controller = (MachineControllerBlockEntity) unsafe.allocateInstance(MachineControllerBlockEntity.class);
        setField(BlockEntity.class, controller, "worldPosition", BlockPos.ZERO);
        var level = LevelStub.createWithBlockEntities(List.of(smartInterfaces));
        setField(BlockEntity.class, controller, "level", level);
        for (SmartInterfaceBlockEntity smartInterface : smartInterfaces) setField(BlockEntity.class, smartInterface, "level", level);
        setField(MachineControllerBlockEntity.class, controller, "components", new ArrayList<ProcessingComponent>());
        setField(MachineControllerBlockEntity.class, controller, "foundMachine",
                new DynamicMachine(MMCR.id("test_machine"), "Test Machine", new BlockArray(java.util.Map.of())));
        Field componentsField = MachineControllerBlockEntity.class.getDeclaredField("components");
        componentsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<ProcessingComponent> components = (List<ProcessingComponent>) componentsField.get(controller);
        for (SmartInterfaceBlockEntity smartInterface : smartInterfaces) {
            components.add(new ProcessingComponent((MachineComponent) null, smartInterface, smartInterface.getBlockPos(),
                    BlockPos.ZERO, (String) null));
        }
        return controller;
    }

    private static void setField(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
