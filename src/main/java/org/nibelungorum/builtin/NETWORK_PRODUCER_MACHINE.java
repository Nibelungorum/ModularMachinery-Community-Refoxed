package org.nibelungorum.builtin;

import cn.howxu.mmcr.publicapi.Machines;
import cn.howxu.mmcr.publicapi.Structures;
import cn.howxu.mmcr.publicapi.data.DataKey;
import cn.howxu.mmcr.publicapi.data.DataStore;
import cn.howxu.mmcr.publicapi.network.Networks;
import cn.howxu.mmcr.publicapi.network.NetworkPortView;
import cn.howxu.mmcr.publicapi.network.NodeView;
import cn.howxu.mmcr.publicapi.network.RequestPayload;
import cn.howxu.mmcr.publicapi.presentation.TextScope;
import cn.howxu.mmcr.publicapi.behavior.TickContext;
import cn.howxu.mmcr.publicapi.event.RegisterMachineDefinitionsEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineStructuresEvent;
import cn.howxu.mmcr.publicapi.machine.MachineSpec;
import cn.howxu.mmcr.publicapi.structure.BlockConditions;
import cn.howxu.mmcr.publicapi.structure.StructureSpec;
import cn.howxu.mmcr.publicapi.recipe.IoDirection;
import cn.howxu.mmcr.publicapi.recipe.requirement.Requirements;
import cn.howxu.mmcr.publicapi.runtime.IoTransaction;
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

import static cn.howxu.mmcr.publicapi.structure.BlockConditions.any;
import static cn.howxu.mmcr.publicapi.structure.BlockConditions.block;
import static cn.howxu.mmcr.publicapi.ApiIds.id;

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

    public static void registerDefinitions(RegisterMachineDefinitionsEvent event) {
        if (!event.definitions().containsKey(NETWORK_PRODUCER_MACHINE)) {
            MachineSpec machine = Machines
                    .machine(NETWORK_PRODUCER_MACHINE)
                    .recipePool(NETWORK_PRODUCER_MACHINE)
                    .displayNameKey("machine.mmcr.network_producer_machine")
                    .appearance(a -> a.machineBasicBlock(ResourceLocation.parse("minecraft:white_wool")))
                    .networkInterface(1, 1)
                    .allowNetworkMachine(NETWORK_CENTER_MACHINE)
                    .tickBehavior(behavior -> behavior.serverTick((TickContext context) -> {
                        DataStore storage = context.dataStorage();
                        if (storage == null) return;

                        double power = storage.get("power")
                                .flatMap(value -> value.asDouble())
                                .orElse(0.0);
                        double drySec = storage.get("dry_sec")
                                .flatMap(value -> value.asDouble())
                                .orElse(0.0);
                        boolean feOk = true;

                        IoTransaction energyPlan = context.ioPlan();
                        energyPlan.addInput(Requirements.energy(IoDirection.INPUT, 100));
                        var energySim = energyPlan.simulate();
                        if (!energySim.energySatisfied() || !energyPlan.commit().successful()) {
                            feOk = false;
                        }

                        double powerPublished = power;
                        double dryPublished = drySec;
                        boolean shouldReport = false;

                        if (context.isDue(20)) {
                            IoTransaction waterPlan = context.ioPlan();
                            waterPlan.addInput(Requirements.fluid(
                                    IoDirection.INPUT,
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

                            storage.set("has_water", DataKey.of(hasWater));
                            storage.set("power", DataKey.of(power));
                            storage.set("dry_sec", DataKey.of(drySec));
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
                            var interfaces = Networks.interfaces(context);
                            NetworkPortView iface = interfaces != null && !interfaces.isEmpty() ? interfaces.get(0) : null;
                            if (iface != null) {
                                var connections = iface.connections();
                                NodeView target = connections != null && !connections.isEmpty() ? connections.get(0) : null;
                                if (target != null) {
                                    Networks.sendRequest(iface, target, REPORT_POWER,
                                            RequestPayload.of(Map.of("power", DataKey.of(powerPublished))));
                                }
                            }
                        }

                        boolean hasWater = storage.get("has_water")
                                .flatMap(value -> value.asBoolean())
                                .orElse(false);

                        context.screenText().append(TextScope.OPERATION, PRODUCER_POWER,
                                Component.literal("Computing Power: " + powerPublished + " tfps"));
                        context.screenText().append(TextScope.OPERATION, PRODUCER_WATER,
                                Component.literal(hasWater
                                        ? "Water: OK"
                                        : "Water: DRY (overflow in " + Math.max(0, 30 - dryPublished) + " sec)"));
                        context.screenText().append(TextScope.OPERATION, PRODUCER_FE,
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
    public static void registerStructures(RegisterMachineStructuresEvent event) {
        if (!event.structures().containsKey(NETWORK_PRODUCER_MACHINE)) {
            StructureSpec structure = Structures
                    .structure()
                    .fullStructure(s -> s
                            .pattern(p -> p
                                    .layer("XXXX", "XAAX", "XXXX")
                                    .layer("XXXX", "A  A", "XXXX")
                                    .layer("XXXX", "A  A", "XXXX")
                                    .layer("XXXX", "XCAX", "XXXX")
                                    .where('X', block(Blocks.WHITE_WOOL))
                                    .where('A', any(
                                            BlockConditions.fluidInput(),
                                            BlockConditions.energyInput(),
                                            BlockConditions.networkInterface(),
                                            BlockConditions.dataStorage(),
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
