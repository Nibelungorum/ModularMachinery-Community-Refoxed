package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.compat.jei.MachineRecipeDisplay;
import cn.howxu.mmcr.compat.mekanism.MekanismBridge;
import cn.howxu.mmcr.compat.mekanism.MekanismBridgeBootstrap;
import cn.howxu.mmcr.internal.runtime.ControllerRecipePresentation;
import cn.howxu.mmcr.test.TestBootstrap;
import com.electronwill.nightconfig.core.CommentedConfig;
import mekanism.common.config.MekanismConfig;
import mekanism.common.util.UnitDisplayUtils.TemperatureUnit;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.fml.config.IConfigSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Verifies heat output presentation separately from absolute temperature.
 *
 * @author howxu <dev@howxu.cn>
 */
class MekanismHeatDisplayTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void loadMekanismConfig() throws Exception {
        var spec = MekanismConfig.common.getConfigSpec();
        CommentedConfig config = CommentedConfig.inMemory();
        spec.correct(config);
        var constructor = Class.forName("net.neoforged.fml.config.LoadedConfig").getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        spec.acceptConfig((IConfigSpec.ILoadedConfig) constructor.newInstance(config, null, null));
        MekanismConfig.common.clearCache(true);
        MekanismBridgeBootstrap.installForTesting(MekanismBridgeBootstrap.selectForTesting(true));
    }

    @AfterEach
    void resetMekanismConfigAndBridge() {
        MekanismConfig.common.getConfigSpec().acceptConfig(null);
        MekanismConfig.common.clearCache(true);
        MekanismBridgeBootstrap.resetForTesting();
    }

    @ParameterizedTest
    @CsvSource({
            "KELVIN, K, 45, 45",
            "CELSIUS, °C, 45, -228.15",
            "RANKINE, R, 81, 81",
            "FAHRENHEIT, °F, 81, -378.67",
            "AMBIENT, +STP, 45, -255"
    })
    void heatOutputUsesUnitScaleWithoutAbsoluteTemperatureOffset(TemperatureUnit unit, String symbol,
                                                               String heatText, double temperature) {
        MekanismConfig.common.tempUnit.set(unit);

        List<ControllerTextLine> lines = ControllerRecipeTextLines.create(
                new ControllerRecipePresentation(List.of(), 0L, 0L, 45D));

        assertThat(lines).singleElement().satisfies(line -> {
            assertThat(line.text()).isEqualTo(Component.translatable(
                    "gui.mmcr.controller.recipe.heat_output", heatText, symbol));
            assertThat(line.tooltip()).containsExactly(Component.translatable(
                    "gui.mmcr.controller.recipe.heat_output_exact", heatText, symbol));
        });
        assertThat(MachineRecipeDisplay.outputHeatLabel(45D)).isEqualTo(Component.translatable(
                "jei.mmcr.machine_recipe.heat_output", Double.parseDouble(heatText), symbol));
        assertThat(MekanismBridge.get().heatDisplayData(0D).value()).isZero();
        assertThat(ControllerRecipeTextLines.create(ControllerRecipePresentation.empty())).isEmpty();

        var minimum = (TranslatableContents) MachineRecipeDisplay.minimumTemperatureLabel(45D).getContents();
        assertThat((double) minimum.getArgs()[0]).isCloseTo(temperature, within(0.000001D));
        assertThat(minimum.getArgs()[1]).isEqualTo(symbol);
    }

    @Test
    void unavailableBridgeKeepsHeatInKelvinEvenWithCelsiusConfigured() {
        MekanismConfig.common.tempUnit.set(TemperatureUnit.CELSIUS);
        MekanismBridgeBootstrap.installForTesting(MekanismBridgeBootstrap.selectForTesting(false));

        assertThat(MekanismBridge.get().heatDisplayData(45D))
                .isEqualTo(new MekanismBridge.HeatDisplayData(45D, "K"));
    }
}
