package org.nibelungorum.builtin;

import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextRegistry;
import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextScope;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import cn.howxu.mmcr.api.publicapi.machine.InterfacePredicates;
import cn.howxu.mmcr.api.publicapi.machine.MachineBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineStructureBuilder;
import cn.howxu.mmcr.api.publicapi.recipe.EnergyRequirement;
import cn.howxu.mmcr.api.publicapi.recipe.ItemInput;
import cn.howxu.mmcr.api.publicapi.recipe.ItemOutput;
import cn.howxu.mmcr.api.publicapi.recipe.ItemRequirement;
import cn.howxu.mmcr.api.publicapi.recipe.RecipeIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntitySpawnReason;
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

import static cn.howxu.mmcr.api.publicapi.machine.BlockPredicate.any;
import static cn.howxu.mmcr.api.publicapi.machine.BlockPredicate.block;
import static cn.howxu.mmcr.api.publicapi.ApiIds.id;

/**
 * @author howxu <dev@howxu.cn>
 */
@EventBusSubscriber
public class PURE_TICK_MACHINE {

    private static final ResourceLocation PURE_TICK_MACHINE = id("pure_tick_machine");
    private static final ResourceLocation FE_STATUS = id("fe_status");
    private static final ResourceLocation PURE_TICK_STATUS = id("pure_tick_status");

    public static void registerDefinitions(MMCRMachineDefinationsEvent event) {
        ControllerScreenTextRegistry.register(PURE_TICK_MACHINE, context -> {
            context.screenText().append(
                    ControllerScreenTextScope.CONTROLLER,
                    FE_STATUS,
                    Component.literal("FE is needed!"));
            context.screenText().append(
                    ControllerScreenTextScope.CONTROLLER,
                    PURE_TICK_STATUS,
                    Component.literal("No Ingot input"));
        });

        if (!event.definitions().containsKey(PURE_TICK_MACHINE)) {
            var machine = MachineBuilder
                    .machine(PURE_TICK_MACHINE)
                    .recipePool(PURE_TICK_MACHINE)
                    .displayNameKey("machine.mmcr.pure_tick_machine")
                    .appearance(a -> a.machineBasicBlock(ResourceLocation.parse("minecraft:green_terracotta")))
                    .allowMultithreading()
                    .maxParallelism(Integer.MAX_VALUE)
                    .tickBehavior(behavior -> behavior.serverTick(context -> {
                        if (!context.isDue(40)) return;

                        var planFe = context.ioPlan();
                        planFe.addInput(new EnergyRequirement(RecipeIo.INPUT, 10));
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
                                level.getMinY(),
                                pos.getZ() - 1,
                                pos.getX() + 2,
                                level.getMaxY() + 1,
                                pos.getZ() + 2);
                        var players = level.getEntitiesOfClass(Player.class, area);
                        for (var player : players) {
                            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
                            if (bolt != null) {
                                bolt.setPos(player.getX(), player.getY(), player.getZ());
                                bolt.setVisualOnly(false);
                                level.addFreshEntity(bolt);
                            }
                        }

                        var plan = context.ioPlan();
                        plan.addInput(ItemRequirement.input(new ItemInput(Ingredient.of(Items.IRON_INGOT), 1)));
                        plan.add(ItemRequirement.output(new ItemOutput(
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
    public static void registerStructures(MMCRMachineStructuresEvent event) {
        if (!event.structures().containsKey(PURE_TICK_MACHINE)) {
            var structure = MachineStructureBuilder
                    .structure()
                    .fullStructure(s -> s
                            .pattern(p -> p
                                    .layer("XXX", "AAA", "XXX")
                                    .layer("XXX", "A A", "X X")
                                    .layer("XXX", "ACA", "XXX")
                                    .where('X', block(Blocks.GREEN_TERRACOTTA))
                                    .where('A', any(
                                            InterfacePredicates.anyOfItemInput(),
                                            InterfacePredicates.anyOfItemOutput(),
                                            InterfacePredicates.anyOfEnergyInput(),
                                            InterfacePredicates.parallelControllers(),
                                            InterfacePredicates.factoryController(),
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
