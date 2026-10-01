package org.nibelungorum.builtin;

import cn.howxu.mmcr.publicapi.Machines;
import cn.howxu.mmcr.publicapi.Structures;
import cn.howxu.mmcr.publicapi.presentation.ControllerTexts;
import cn.howxu.mmcr.publicapi.presentation.ControllerTextContext;
import cn.howxu.mmcr.publicapi.presentation.TextScope;
import cn.howxu.mmcr.publicapi.behavior.TickContext;
import cn.howxu.mmcr.publicapi.event.RegisterMachineDefinitionsEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineStructuresEvent;
import cn.howxu.mmcr.publicapi.machine.MachineSpec;
import cn.howxu.mmcr.publicapi.structure.BlockConditions;
import cn.howxu.mmcr.publicapi.structure.StructureSpec;
import cn.howxu.mmcr.publicapi.recipe.IoValues;
import cn.howxu.mmcr.publicapi.recipe.IoDirection;
import cn.howxu.mmcr.publicapi.recipe.requirement.Requirements;
import cn.howxu.mmcr.publicapi.runtime.IoTransaction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
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
public class PURE_TICK_MACHINE {

    private static final ResourceLocation PURE_TICK_MACHINE = id("pure_tick_machine");
    private static final ResourceLocation FE_STATUS = id("fe_status");
    private static final ResourceLocation PURE_TICK_STATUS = id("pure_tick_status");

    public static void registerDefinitions(RegisterMachineDefinitionsEvent event) {
        ControllerTexts.register(PURE_TICK_MACHINE, (ControllerTextContext context) -> {
            context.screenText().append(
                    TextScope.CONTROLLER,
                    FE_STATUS,
                    Component.literal("FE is needed!"));
            context.screenText().append(
                    TextScope.CONTROLLER,
                    PURE_TICK_STATUS,
                    Component.literal("No Ingot input"));
        });

        if (!event.definitions().containsKey(PURE_TICK_MACHINE)) {
            MachineSpec machine = Machines
                    .machine(PURE_TICK_MACHINE)
                    .recipePool(PURE_TICK_MACHINE)
                    .displayNameKey("machine.mmcr.pure_tick_machine")
                    .appearance(a -> a.machineBasicBlock(ResourceLocation.parse("minecraft:green_terracotta")))
                    .allowMultithreading()
                    .maxParallelism(Integer.MAX_VALUE)
                    .tickBehavior(behavior -> behavior.serverTick((TickContext context) -> {
                        if (!context.isDue(40)) return;

                        IoTransaction planFe = context.ioPlan();
                        planFe.addInput(Requirements.energy(IoDirection.INPUT, 10));
                        var feSimulation = planFe.simulate();

                        if (!feSimulation.energySatisfied()) {
                            context.screenText().replace(FE_STATUS,
                                    Component.literal("FE is needed!"));
                            return;
                        }

                        if (!planFe.commit().successful()) {
                            context.screenText().replace(FE_STATUS,
                                    Component.literal("FE consume error!"));
                            return;
                        }

                        context.screenText().replace(FE_STATUS,
                                Component.literal("Machine do a run!"));

                        var level = context.level();
                        var pos = context.controllerPos();
                        var area = new AABB(
                                pos.getX() - 1,
                                level.getMinBuildHeight(),
                                pos.getZ() - 1,
                                pos.getX() + 2,
                                level.getMaxBuildHeight() + 1,
                                pos.getZ() + 2);
                        var players = level.getEntitiesOfClass(Player.class, area);
                        for (var player : players) {
                            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                            if (bolt != null) {
                                bolt.setPos(player.getX(), player.getY(), player.getZ());
                                bolt.setVisualOnly(false);
                                level.addFreshEntity(bolt);
                            }
                        }

                        IoTransaction plan = context.ioPlan();
                        plan.addInput(Requirements.itemInput(IoValues.itemInput(Ingredient.of(Items.IRON_INGOT), 1)));
                        plan.add(Requirements.itemOutput(IoValues.itemOutput(
                                new ItemStack(Items.GOLD_NUGGET, 1))));

                        var simulation = plan.simulate();

                        if (!simulation.inputsSatisfied()) return;
                        boolean outputAvailable = true;

                        for (var output : simulation.outputs()) {
                            if (output.accepted() < output.requested()) {
                                outputAvailable = false;
                                break;
                            }
                        }

                        if (!outputAvailable) return;

                        context.screenText().replace(PURE_TICK_STATUS,
                                Component.literal("Iron Ingot inputed"));

                        plan.commit();
                    }))
                    .build();
            event.registerMachine(machine);
        }
    }

    @SubscribeEvent
    public static void registerStructures(RegisterMachineStructuresEvent event) {
        if (!event.structures().containsKey(PURE_TICK_MACHINE)) {
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
                                            BlockConditions.factoryController(),
                                            block(Blocks.GREEN_WOOL)
                                    ))
                                    .controller('C')
                            )
                    )
                    .build(PURE_TICK_MACHINE);
            event.registerStructure(structure);
        }
    }
}
