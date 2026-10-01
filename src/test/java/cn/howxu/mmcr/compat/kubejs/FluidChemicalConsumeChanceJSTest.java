package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalIngredient;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.mekanism.MekanismBridgeBootstrap;
import cn.howxu.mmcr.compat.mekanism.MekanismRecipeTypes;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the KubeJS sugar for fluid and chemical inputs surfaces the consumeChance
 * introduced on the public builder, both via the fluent builder and the underlying payload.
 *
 * @author howxu <dev@howxu.cn>
 */
class FluidChemicalConsumeChanceJSTest {
    private static final ResourceLocation MACHINE = MMCR.id("kubejs_consume_chance_machine");

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        MekanismBridgeBootstrap.installForTesting(MekanismBridgeBootstrap.selectForTesting(false));
        MekanismRecipeTypes.register();
        MachineRegistry.register(new DynamicMachine(MACHINE, "KubeJS Consume Chance Machine", new BlockArray(Map.of())));
    }

    @AfterEach
    void resetBridge() {
        MekanismBridgeBootstrap.resetForTesting();
        MekanismBridgeBootstrap.installForTesting(MekanismBridgeBootstrap.selectForTesting(false));
        MekanismRecipeTypes.register();
    }

    @Test
    void kubejs_fluid_input_with_consume_chance_emits_matching_payload() {
        MachineRecipeBuilderJS builder = new MachineRecipeBuilderJS("mmcr:fk");
        builder.recipePool(MACHINE.toString()).fluidInput("minecraft:water", 1000, 0.25D);

        var req = (FluidRequirement) builder.createObject().requirements().get(0);
        assertThat(req.consumeChance()).isEqualTo(0.25F);
    }

    @Test
    void kubejs_chemical_input_with_consume_chance_emits_payload() {
        MachineRecipeBuilderJS builder = new MachineRecipeBuilderJS("mmcr:ck");
        builder.recipePool(MACHINE.toString()).chemicalInput("mekanism:oxygen", 1_000L, 0.5D);

        var expected = MachineRequirement.CODEC.parse(JsonOps.INSTANCE,
                MachineRecipeBuilder.chemicalInputPayload(
                        ChemicalIngredient.chemical(ResourceLocation.parse("mekanism:oxygen"), 1_000L), 0.5F)).getOrThrow();

        assertThat(builder.requirements).singleElement().isEqualTo(expected);
    }
}
