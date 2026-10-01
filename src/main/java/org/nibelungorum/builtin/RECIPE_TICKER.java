package org.nibelungorum.builtin;

import cn.howxu.mmcr.publicapi.Machines;
import cn.howxu.mmcr.publicapi.Structures;
import cn.howxu.mmcr.publicapi.Recipes;
import cn.howxu.mmcr.publicapi.presentation.ControllerTexts;
import cn.howxu.mmcr.publicapi.presentation.ControllerTextContext;
import cn.howxu.mmcr.publicapi.presentation.TextScope;
import cn.howxu.mmcr.publicapi.behavior.MachineContext;
import cn.howxu.mmcr.publicapi.behavior.RecipeStartContext;
import cn.howxu.mmcr.publicapi.behavior.RecipeTickContext;
import cn.howxu.mmcr.publicapi.behavior.RecipeFinishContext;
import cn.howxu.mmcr.publicapi.event.RegisterMachineDefinitionsEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineRecipesEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineStructuresEvent;
import cn.howxu.mmcr.publicapi.machine.MachineSpec;
import cn.howxu.mmcr.publicapi.structure.BlockConditions;
import cn.howxu.mmcr.publicapi.structure.StructureSpec;
import cn.howxu.mmcr.publicapi.recipe.RecipeSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;


import static cn.howxu.mmcr.publicapi.structure.BlockConditions.any;
import static cn.howxu.mmcr.publicapi.structure.BlockConditions.block;
import static cn.howxu.mmcr.publicapi.ApiIds.id;

/**
 * @author howxu <dev@howxu.cn>
 */
@EventBusSubscriber
public class RECIPE_TICKER {

    private static final ResourceLocation RECIPE_TICKER = id("recipe_ticker");
    private static final ResourceLocation BEFORE_LINE = id("before_line");
    private static final ResourceLocation IN_LINE = id("in_line");
    private static final ResourceLocation AFTER_LINE = id("after_line");
    private static final ResourceLocation DISPLAY_WHEN_IDLE = id("display_when_idle");
    private static final ResourceLocation DISPLAY_WHEN_IDLE_EMPTY_LINE = id("display_when_idle_empty_line");
    private static final ResourceLocation DISPLAY_WHEN_START_RECIPE = id("display_when_start_recipe");

    public static void registerDefinitions(RegisterMachineDefinitionsEvent event) {
        ControllerTexts.register(RECIPE_TICKER, (ControllerTextContext context) -> {
            context.screenText().append(
                    TextScope.CONTROLLER,
                    BEFORE_LINE,
                    Component.translatable("gui.mmcr.before_line"));
            context.screenText().appendAfter(
                    TextScope.CONTROLLER,
                    IN_LINE,
                    id("sp_line_1"),
                    Component.translatable("gui.mmcr.in_line"));
            context.screenText().append(
                    TextScope.CONTROLLER,
                    AFTER_LINE,
                    Component.translatable("gui.mmcr.after_line"));
        });

        if (!event.definitions().containsKey(RECIPE_TICKER)) {
            MachineSpec machine = Machines
                    .machine(RECIPE_TICKER)
                    .recipePool(RECIPE_TICKER)
                    .displayNameKey("machine.mmcr.recipe_ticker")
                    .appearance(a -> a.machineBasicBlock(ResourceLocation.parse("minecraft:green_terracotta")))
                    .recipeBehavior(behavior -> behavior
                            .idleStart((MachineContext ctx) -> {
                                var screen = ctx.screenText();
                                screen.append(
                                        TextScope.OPERATION,
                                        DISPLAY_WHEN_IDLE_EMPTY_LINE,
                                        Component.literal(" "));
                                screen.append(
                                        TextScope.OPERATION,
                                        DISPLAY_WHEN_IDLE,
                                        Component.translatable("gui.mmcr.display_when_idle"));
                            })
                            .idleEnd(ctx -> {
                            })
                            .beforeStart((RecipeStartContext ctx) -> {
                                var screen = ctx.machineContext().screenText();
                                screen.remove(TextScope.OPERATION, DISPLAY_WHEN_IDLE_EMPTY_LINE);
                                screen.remove(TextScope.OPERATION, DISPLAY_WHEN_IDLE);

                                MachineContext machineContext = ctx.machineContext();
                                var level = machineContext.level();
                                var controllerPos = machineContext.controllerPos();
                                var area = new AABB(
                                        controllerPos.getX() - 2,
                                        level.getMinBuildHeight(),
                                        controllerPos.getZ() - 2,
                                        controllerPos.getX() + 3,
                                        level.getMaxBuildHeight() + 1,
                                        controllerPos.getZ() + 3);

                                for (var entity : level.getEntitiesOfClass(LivingEntity.class, area)) {
                                    entity.addEffect(new MobEffectInstance(
                                            MobEffects.DAMAGE_BOOST, 10000, 1));
                                }

                                ctx.replaceExactItemInputCount(Items.GOLD_INGOT, 32, 1);
                            })
                            .recipeTick((RecipeTickContext ctx) -> {
                                var screen = ctx.machineContext().screenText();
                                screen.appendAfter(
                                        TextScope.OPERATION,
                                        DISPLAY_WHEN_START_RECIPE,
                                        id("in_line"),
                                        Component.literal("正在使用雷霆大猪咪暴力执行配方"));
                            })
                            .beforeFinish((RecipeFinishContext ctx) -> {
                                MachineContext machineContext = ctx.machineContext();
                                var level = machineContext.level();
                                var controllerPos = machineContext.controllerPos();
                                var area = new AABB(
                                        controllerPos.getX() - 2,
                                        level.getMinBuildHeight(),
                                        controllerPos.getZ() - 2,
                                        controllerPos.getX() + 3,
                                        level.getMaxBuildHeight() + 1,
                                        controllerPos.getZ() + 3);

                                for (var entity : level.getEntitiesOfClass(LivingEntity.class, area)) {
                                    entity.addEffect(new MobEffectInstance(
                                            MobEffects.NIGHT_VISION, 10000, 1));
                                }
                            })
                    )
                    .build();
            event.registerMachine(machine);
        }
    }

    @SubscribeEvent
    public static void registerStructures(RegisterMachineStructuresEvent event) {
        if (!event.structures().containsKey(RECIPE_TICKER)) {
            StructureSpec structure = Structures
                    .structure()
                    .fullStructure(s -> s
                            .pattern(p -> p
                                    .layer("XXX", "AAA", "XXX")
                                    .layer("XXX", "A A", "X X")
                                    .layer("XXX", "ACA", "XXX")
                                    .where('X', block(Blocks.GREEN_TERRACOTTA))
                                    .where('A', any(
                                            BlockConditions.itemInput(),
                                            BlockConditions.itemOutput(),
                                            BlockConditions.energyInput(),
                                            BlockConditions.parallelControllers(),
                                            block(Blocks.GREEN_WOOL)
                                    ))
                                    .controller('C')
                            )
                    )
                    .build(RECIPE_TICKER);
            event.registerStructure(structure);
        }
    }

    @SubscribeEvent
    public static void register(RegisterMachineRecipesEvent event) {
        RecipeSpec recipe = Recipes
                .recipe(RECIPE_TICKER.withSuffix("_recipe_1"))
                .recipePool(RECIPE_TICKER)
                .inputItem(Items.COAL, 10000)
                .inputItem(Items.DIAMOND, 8)
                .outputItem(Items.GOLD_INGOT, 9)
                .inputEnergy(20)
                .duration(500)
                .build();
        event.registerRecipe(recipe);

        recipe = Recipes
                .recipe(RECIPE_TICKER.withSuffix("_recipe_2"))
                .recipePool(RECIPE_TICKER)
                .inputItem(Items.DIAMOND, 114514)
                .inputItem(Items.IRON_INGOT, 8)
                .outputItem(Items.COAL, 18)
                .inputEnergy(20)
                .duration(300)
                .build();
        event.registerRecipe(recipe);

        recipe = Recipes
                .recipe(RECIPE_TICKER.withSuffix("_recipe_3"))
                .recipePool(RECIPE_TICKER)
                .inputItem(Items.GOLD_INGOT, 32)
                .inputItem(Items.STICK, 8)
                .outputItem(Items.DIAMOND, 3)
                .inputEnergy(20)
                .duration(300)
                .build();
        event.registerRecipe(recipe);
    }
}
