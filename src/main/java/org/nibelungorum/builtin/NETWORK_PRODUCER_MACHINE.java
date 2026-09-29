package org.nibelungorum.builtin;

import cn.howxu.mmcr.api.publicapi.data.DataStorage;
import cn.howxu.mmcr.api.publicapi.data.DataValue;
import cn.howxu.mmcr.api.publicapi.network.NetworkApi;
import cn.howxu.mmcr.api.publicapi.network.RequestBody;
import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextScope;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import cn.howxu.mmcr.api.publicapi.machine.InterfacePredicates;
import cn.howxu.mmcr.api.publicapi.machine.MachineBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineStructureBuilder;
import cn.howxu.mmcr.api.publicapi.recipe.EnergyRequirement;
import cn.howxu.mmcr.api.publicapi.recipe.FluidRequirement;
import cn.howxu.mmcr.api.publicapi.recipe.RecipeIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

import java.util.Map;

import static cn.howxu.mmcr.api.publicapi.machine.BlockPredicate.any;
import static cn.howxu.mmcr.api.publicapi.machine.BlockPredicate.block;
import static cn.howxu.mmcr.api.publicapi.ApiIds.id;

/**
 * @author howxu <dev@howxu.cn>
 */
@EventBusSubscriber
public class NETWORK_PRODUCER_MACHINE {

    public static final ResourceLocation NETWORK_PRODUCER_MACHINE = id("network_producer_machine");
    public static final ResourceLocation NETWORK_CENTER_MACHINE = id("network_center_machine");
    public static final ResourceLocation REPORT_POWER = id("report_power");

    public static final ResourceLocation PRODUCER_POWER = id("producer_power");
    public static final ResourceLocation PRODUCER_WATER = id("producer_water");
    public static final ResourceLocation PRODUCER_FE = id("producer_fe");

    public static void registerDefinitions(MMCRMachineDefinationsEvent event) {
        if (!event.definitions().containsKey(NETWORK_PRODUCER_MACHINE)) {
            var machine = MachineBuilder
                    .machine(NETWORK_PRODUCER_MACHINE)
                    .recipePool(NETWORK_PRODUCER_MACHINE)
                    .displayNameKey("machine.mmcr.network_producer_machine")
                    .appearance(a -> a.machineBasicBlock(ResourceLocation.parse("minecraft:white_wool")))
                    .networkInterface(1, 1)
                    .allowNetworkMachine(NETWORK_CENTER_MACHINE)
                    .tickBehavior(behavior -> behavior.serverTick(context -> {
                        DataStorage storage = context.dataStorage();
                        if (storage == null) return;

                        double power = storage.get("power")
                                .flatMap(DataValue::asDouble)
                                .orElse(0.0);
                        double drySec = storage.get("dry_sec")
                                .flatMap(DataValue::asDouble)
                                .orElse(0.0);
                        boolean feOk = true;

                        var energyPlan = context.ioPlan();
                        energyPlan.addInput(new EnergyRequirement(RecipeIo.INPUT, 100));
                        var energySim = energyPlan.simulate();
                        if (!energySim.energySatisfied() || !energyPlan.commit().successful()) {
                            feOk = false;
                        }

                        double powerPublished = power;
                        double dryPublished = drySec;
                        boolean shouldReport = false;

                        if (context.isDue(20)) {
                            var waterPlan = context.ioPlan();
                            waterPlan.addInput(new FluidRequirement(
                                    RecipeIo.INPUT,
                                    FluidIngredient.of(Fluids.WATER),
                                    100,
                                    FluidStack.EMPTY,
                                    1F,
                                    1F));
                            var waterSim = waterPlan.simulate();
                            boolean hasWater = waterSim.inputsSatisfied();

                            if (feOk && hasWater && waterPlan.commit().successful()) {
                                power = 20;
                                drySec = 0;
                            } else {
                                power = 10;
                                if (feOk) {
                                    drySec = drySec + 1;
                                    hasWater = false;
                                }
                            }

                            storage.set("has_water", DataValue.of(hasWater));
                            storage.set("power", DataValue.of(power));
                            storage.set("dry_sec", DataValue.of(drySec));
                            powerPublished = power;
                            dryPublished = drySec;

                            if (drySec >= 30) {
                                var level = context.level();
                                var pos = context.controllerPos();
                                level.explode(
                                        null,
                                        pos.getX() + 0.5,
                                        pos.getY() + 0.5,
                                        pos.getZ() + 0.5,
                                        4.0F,
                                        false,
                                        Level.ExplosionInteraction.BLOCK);
                                return;
                            }

                            shouldReport = feOk;
                        }

                        if (shouldReport) {
                            var interfaces = NetworkApi.interfaces(context);
                            var iface = interfaces != null && !interfaces.isEmpty() ? interfaces.get(0) : null;
                            if (iface != null) {
                                var connections = iface.connections();
                                var target = connections != null && !connections.isEmpty() ? connections.get(0) : null;
                                if (target != null) {
                                    NetworkApi.sendRequest(iface, target, REPORT_POWER,
                                            RequestBody.of(Map.of("power", DataValue.of(powerPublished))));
                                }
                            }
                        }

                        boolean hasWater = storage.get("has_water")
                                .flatMap(DataValue::asBoolean)
                                .orElse(false);

                        context.screenText().append(ControllerScreenTextScope.OPERATION, PRODUCER_POWER,
                                Component.literal("Computing Power: " + powerPublished + " tfps"));
                        context.screenText().append(ControllerScreenTextScope.OPERATION, PRODUCER_WATER,
                                Component.literal(hasWater
                                        ? "Water: OK"
                                        : "Water: DRY (overflow in " + Math.max(0, 30 - dryPublished) + " sec)"));
                        context.screenText().append(ControllerScreenTextScope.OPERATION, PRODUCER_FE,
                                Component.literal(feOk ? "Energy: OK" : "Energy: LOW"));

                        context.jadeText().append(PRODUCER_POWER,
                                Component.literal(powerPublished + " tfps"));
                        context.jadeText().append(PRODUCER_WATER,
                                Component.literal(hasWater ? "Water OK" : "Water DRY"));
                    }))
                    .build();
            event.registerMachine(machine);
        }
    }

    @SubscribeEvent
    public static void registerStructures(MMCRMachineStructuresEvent event) {
        if (!event.structures().containsKey(NETWORK_PRODUCER_MACHINE)) {
            var structure = MachineStructureBuilder
                    .structure()
                    .fullStructure(s -> s
                            .pattern(p -> p
                                    .layer("XXXX", "XAAX", "XXXX")
                                    .layer("XXXX", "A  A", "XXXX")
                                    .layer("XXXX", "A  A", "XXXX")
                                    .layer("XXXX", "XCAX", "XXXX")
                                    .where('X', block(Blocks.WHITE_WOOL))
                                    .where('A', any(
                                            InterfacePredicates.anyOfFluidInput(),
                                            InterfacePredicates.anyOfEnergyInput(),
                                            InterfacePredicates.networkInterface(),
                                            InterfacePredicates.dataStorage(),
                                            block(Blocks.RED_TERRACOTTA)
                                    ))
                                    .controller('C')
                            )
                    )
                    .build(NETWORK_PRODUCER_MACHINE);
            event.registerStructure(structure);
        }
    }
}
