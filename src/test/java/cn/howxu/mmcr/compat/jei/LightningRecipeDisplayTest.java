package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.LevelRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import cn.howxu.mmcr.api.recipe.requirement.StageRequirement;
import cn.howxu.mmcr.compat.ae2lt.LightningOutput;
import cn.howxu.mmcr.compat.ae2lt.LightningRecipeTypes;
import cn.howxu.mmcr.compat.ae2lt.LightningRequirement;
import cn.howxu.mmcr.compat.ae2lt.client.LightningJeiDisplay;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedHeatRequirement;
import cn.howxu.mmcr.test.TestBootstrap;
import com.moakiee.ae2lt.integration.jei.LightningJeiIngredients;
import com.moakiee.ae2lt.me.key.LightningKey;
import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author howxu <dev@howxu.cn>
 */
class LightningRecipeDisplayTest {
    private List<LevelType> previousTypes;
    private List<MachineLevel> previousLevels;

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        LightningRecipeTypes.register();
        LightningJeiDisplay.register();
        if (RequirementHandlerRegistry.canonicalType(LoadedHeatRequirement.TEMPERATURE_TYPE) == null) {
            RequirementHandlerRegistry.register(LoadedHeatRequirement.TEMPERATURE_TYPE);
        }
        if (RequirementHandlerRegistry.canonicalType(LoadedHeatRequirement.HEAT_TYPE) == null) {
            RequirementHandlerRegistry.register(LoadedHeatRequirement.HEAT_TYPE);
        }
    }

    @BeforeEach
    void saveLevels() {
        previousTypes = MachineLevelRegistry.types();
        previousLevels = previousTypes.stream().flatMap(type -> MachineLevelRegistry.levelsForType(type.id()).stream())
                .toList();
    }

    @AfterEach
    void restoreLevels() {
        MachineLevelRegistry.installSnapshot(previousTypes, previousLevels);
    }

    @Test
    void showsBothTierLabelsWithExactAmounts() {
        var high = LightningRequirement.input(LightningTier.HIGH_VOLTAGE, 2_147_483_647L);
        var extreme = LightningRequirement.output(LightningTier.EXTREME_HIGH_VOLTAGE, 1_234_567L, 1F);

        var highText = (TranslatableContents) LightningJeiDisplay.label(high).getContents();
        var extremeText = (TranslatableContents) LightningJeiDisplay.label(extreme).getContents();

        assertThat(highText.getKey()).isEqualTo("jei.mmcr.machine_recipe.high_voltage_input");
        assertThat(highText.getArgs()).containsExactly("2147483647");
        assertThat(extremeText.getKey()).isEqualTo("jei.mmcr.machine_recipe.extreme_high_voltage_output");
        assertThat(extremeText.getArgs()).containsExactly("1234567");
    }

    @Test
    void hoveringEachLightningOutputShowsItsOwnChanceWithoutChangingLabels() {
        var display = display(List.of(
                LightningRequirement.output(LightningTier.HIGH_VOLTAGE, 200L, 0.5F),
                LightningRequirement.input(LightningTier.HIGH_VOLTAGE, 1000L),
                LightningRequirement.output(LightningTier.EXTREME_HIGH_VOLTAGE, 20L, 0.25F)), Set.of());
        var layout = MachineRecipeLayout.forDisplay(display, 4);
        int x = layout.durationTextX();
        int y = layout.lightningTextY(display);

        assertThat(MachineRecipeCategory.lightningTooltip(display, layout, x, y)).isEmpty();
        var high = MachineRecipeCategory.lightningTooltip(display, layout, x, y + 10).orElseThrow();
        var extreme = MachineRecipeCategory.lightningTooltip(display, layout, x, y + 20).orElseThrow();
        assertThat(((TranslatableContents) high.getContents()).getKey()).isEqualTo("jei.mmcr.machine_recipe.output_chance");
        assertThat(((TranslatableContents) high.getContents()).getArgs()).containsExactly("50%");
        assertThat(((TranslatableContents) extreme.getContents()).getArgs()).containsExactly("25%");
        assertThat(MachineRecipeCategory.lightningTooltip(display, layout, x - 1, y + 10)).isEmpty();
        assertThat(MachineRecipeCategory.lightningTooltip(display, layout, layout.width() - x, y + 10)).isEmpty();
        assertThat(MachineRecipeCategory.lightningTooltip(display, layout, x, y - 1)).isEmpty();
        assertThat(MachineRecipeCategory.lightningTooltip(display, layout, x, y + 30)).isEmpty();
    }

    @Test
    void lightningChanceTooltipDistinguishesGuaranteedAndDisabledOutput() {
        var display = display(List.of(
                LightningRequirement.output(LightningTier.HIGH_VOLTAGE, 200L, 1F),
                LightningRequirement.output(LightningTier.EXTREME_HIGH_VOLTAGE, 20L, 0F)), Set.of());
        var layout = MachineRecipeLayout.forDisplay(display, 4);
        int x = layout.durationTextX();
        int y = layout.lightningTextY(display);

        var guaranteed = MachineRecipeCategory.lightningTooltip(display, layout, x, y).orElseThrow();
        var disabled = MachineRecipeCategory.lightningTooltip(display, layout, x, y + 10).orElseThrow();
        assertThat(((TranslatableContents) guaranteed.getContents()).getArgs()).containsExactly("100%");
        assertThat(((TranslatableContents) disabled.getContents()).getArgs()).containsExactly("0%");
    }

    @Test
    void distinguishesDirectionInCompleteLabelsForBothTiers() {
        for (var tier : LightningTier.values()) {
            String prefix = tier == LightningTier.HIGH_VOLTAGE ? "high_voltage" : "extreme_high_voltage";
            var input = (TranslatableContents) LightningJeiDisplay.label(
                    LightningRequirement.input(tier, 10L)).getContents();
            var output = (TranslatableContents) LightningJeiDisplay.label(
                    LightningRequirement.output(tier, 10L, 1F)).getContents();
            assertThat(input.getKey()).isEqualTo("jei.mmcr.machine_recipe." + prefix + "_input");
            assertThat(output.getKey()).isEqualTo("jei.mmcr.machine_recipe." + prefix + "_output");
            assertThat(input.getArgs()).containsExactly("10");
            assertThat(output.getArgs()).containsExactly("10");
        }
    }

    @Test
    void removesLightningFallbackAndOrdersInputsBeforeOutputsWithoutDuplicatingOutputs() {
        var output = new LightningOutput(LightningTier.HIGH_VOLTAGE, 32L, 1F);
        var extreme = LightningRequirement.input(LightningTier.EXTREME_HIGH_VOLTAGE, 17L);
        var high = LightningRequirement.input(LightningTier.HIGH_VOLTAGE, 23L);
        MachineRecipe recipe = MachineRecipe.fromCanonical(MMCR.id("lightning_display"), MMCR.id("lightning_pool"),
                20, List.of(extreme, high), List.of(output), List.of(), 0, 1, false, false, false, Set.of());
        MachineRecipeDisplay display = MachineRecipeDisplay.from(recipe);

        assertThat(display.entries()).isEmpty();
        assertThat(MachineRecipeLayout.forDisplay(display, 4).inputs().slots()).isEmpty();
        assertThat(MachineRecipeLayout.forDisplay(display, 4).outputs().slots()).isEmpty();
        assertThat(LightningJeiDisplay.requirements(display)).containsExactly(extreme, high,
                LightningRequirement.output(LightningTier.HIGH_VOLTAGE, 32L, 1F));
    }

    @Test
    void metadataRowsDoNotOverlapOtherRequirements() {
        MachineLevelRegistry.installSnapshot(
                List.of(new LevelType(MMCR.id("voltage"), Component.translatable("test.voltage"))),
                List.of(new MachineLevel(MMCR.id("high"), MMCR.id("voltage"), 1,
                        new BlockPredicate.OfBlockState(Blocks.IRON_BLOCK.defaultBlockState()),
                        ItemStack.EMPTY, ModifierDefinition.EMPTY)));
        List<MachineRequirement> requirements = List.of(
                new EnergyRequirement(IOType.INPUT, 40L),
                new EnergyRequirement(IOType.OUTPUT, 10L),
                LoadedHeatRequirement.minimumTemperature(300D),
                LoadedHeatRequirement.outputHeat(500D),
                LevelRequirement.input(MMCR.id("voltage"), MMCR.id("high")),
                StageRequirement.input(2),
                SmartInterfaceRequirement.input("mode", 1F),
                SmartInterfaceRequirement.output("mode", 2F));
        var lightning = new ArrayList<>(requirements);
        lightning.add(LightningRequirement.output(LightningTier.EXTREME_HIGH_VOLTAGE, 37L, 1F));
        lightning.add(LightningRequirement.input(LightningTier.HIGH_VOLTAGE, 19L));

        for (boolean hosted : List.of(false, true)) {
            Set<Identifier> hosts = hosted ? Set.of(MMCR.id("lightning_host")) : Set.of();
            MachineRecipeDisplay original = display(requirements, hosts);
            MachineRecipeDisplay expanded = display(lightning, hosts);
            for (int scale : List.of(1, 2, 3, 4)) {
                MachineRecipeLayout before = MachineRecipeLayout.forDisplay(original, scale);
                MachineRecipeLayout after = MachineRecipeLayout.forDisplay(expanded, scale);
                assertThat(after.durationTextY()).isEqualTo(before.durationTextY());
                assertThat(after.lightningTextY(expanded)).isEqualTo(after.durationTextY() + 50);
                assertThat(after.hostRequirementTextY()).isEqualTo(before.hostRequirementTextY() + 20);
                assertThat(after.levelRequirementSlotY(expanded, 0))
                        .isEqualTo(before.levelRequirementSlotY(original, 0) + 20);
                assertThat(after.stageRequirementTextY(expanded)).isEqualTo(before.stageRequirementTextY(original) + 20);
                assertThat(after.smartInterfaceTextY(expanded)).isEqualTo(before.smartInterfaceTextY(original) + 20);
                assertThat(after.informationTextY(expanded)).isEqualTo(before.informationTextY(original) + 20);
                assertThat(after.lastMetadataTextY(expanded)).isEqualTo(before.lastMetadataTextY(original) + 20);
                assertThat(after.levelRequirementSlotY(expanded, 0))
                        .isGreaterThanOrEqualTo(after.lightningTextY(expanded) + 20);
            }
        }
    }

    @Test
    void preservesInputAndOutputIngredientRoles() throws Exception {
        MachineRecipeDisplay display = display(List.of(
                LightningRequirement.output(LightningTier.EXTREME_HIGH_VOLTAGE, 41L, 1F),
                LightningRequirement.input(LightningTier.HIGH_VOLTAGE, 13L),
                LightningRequirement.output(LightningTier.HIGH_VOLTAGE, 31L, 1F),
                LightningRequirement.input(LightningTier.EXTREME_HIGH_VOLTAGE, 29L)), Set.of());
        var roles = new ArrayList<RecipeIngredientRole>();
        var keys = new ArrayList<Object>();
        IRecipeLayoutBuilder builder = (IRecipeLayoutBuilder) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{IRecipeLayoutBuilder.class}, (proxy, method, arguments) -> {
                    if (!method.getName().equals("addInvisibleIngredients")) {
                        throw new AssertionError("Lightning must not create visible or transfer slots: " + method);
                    }
                    RecipeIngredientRole role = (RecipeIngredientRole) arguments[0];
                    return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{IIngredientAcceptor.class},
                            (acceptor, add, values) -> {
                                assertThat(add.getName()).isEqualTo("add");
                                assertThat(values[0]).isSameAs(LightningJeiIngredients.TYPE);
                                roles.add(role);
                                keys.add(values[1]);
                                return acceptor;
                            });
                });

        // Exercise the real loaded branch without pretending that the JUnit process has loaded mods.
        Class<?> nativeIngredients = Class.forName(LightningJeiDisplay.class.getName() + "$NativeIngredients");
        var add = nativeIngredients.getDeclaredMethod("add", IRecipeLayoutBuilder.class, List.class);
        add.setAccessible(true);
        add.invoke(null, builder, LightningJeiDisplay.requirements(display));

        assertThat(roles).containsExactly(RecipeIngredientRole.INPUT, RecipeIngredientRole.INPUT,
                RecipeIngredientRole.OUTPUT, RecipeIngredientRole.OUTPUT);
        assertThat(keys).containsExactly(LightningKey.HIGH_VOLTAGE, LightningKey.EXTREME_HIGH_VOLTAGE,
                LightningKey.EXTREME_HIGH_VOLTAGE, LightningKey.HIGH_VOLTAGE);
    }

    @Test
    void neutralDisplayHelpersDoNotLinkOptionalNativeClasses() throws Exception {
        ClassLoader filtered = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("com.moakiee.ae2lt.") || name.startsWith("appeng.")) {
                    throw new ClassNotFoundException(name);
                }
                if (!name.startsWith(LightningJeiDisplay.class.getName())) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        String resource = name.replace('.', '/') + ".class";
                        try (var input = getParent().getResourceAsStream(resource)) {
                            if (input == null) throw new ClassNotFoundException(name);
                            byte[] bytes = input.readAllBytes();
                            loaded = defineClass(name, bytes, 0, bytes.length);
                        } catch (IOException exception) {
                            throw new ClassNotFoundException(name, exception);
                        }
                    }
                    if (resolve) resolveClass(loaded);
                    return loaded;
                }
            }
        };
        Class<?> helper = Class.forName(LightningJeiDisplay.class.getName(), true, filtered);
        var requirement = LightningRequirement.input(LightningTier.HIGH_VOLTAGE, 73L);
        var display = display(List.of(requirement), Set.of());

        assertThat(helper.getMethod("requirements", MachineRecipeDisplay.class).invoke(null, display))
                .isEqualTo(List.of(requirement));
        assertThat(helper.getMethod("label", LightningRequirement.class).invoke(null, requirement))
                .isEqualTo(LightningJeiDisplay.label(requirement));
        IRecipeLayoutBuilder noNativeCalls = (IRecipeLayoutBuilder) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{IRecipeLayoutBuilder.class}, (proxy, method, arguments) -> {
                    throw new AssertionError("Absent integration must not register native ingredients");
                });
        helper.getMethod("addInvisibleIngredients", IRecipeLayoutBuilder.class, MachineRecipeDisplay.class)
                .invoke(null, noNativeCalls, display);
    }

    private static MachineRecipeDisplay display(List<MachineRequirement> requirements,
                                                 Set<Identifier> hosts) {
        return MachineRecipeDisplay.from(MachineRecipe.fromCanonical(MMCR.id("lightning_metadata"),
                MMCR.id("lightning_pool"), 20, requirements, List.of(), List.of(), 0, 1,
                false, false, false, hosts));
    }
}
