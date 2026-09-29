package org.nibelungorum.builtin;

import cn.howxu.mmcr.api.publicapi.data.DataStorage;
import cn.howxu.mmcr.api.publicapi.data.DataValue;
import cn.howxu.mmcr.api.publicapi.network.NetworkApi;
import cn.howxu.mmcr.api.publicapi.network.RequestProcess;
import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextScope;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import cn.howxu.mmcr.api.publicapi.machine.InterfacePredicates;
import cn.howxu.mmcr.api.publicapi.machine.MachineBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineStructureBuilder;
import cn.howxu.mmcr.api.publicapi.recipe.EnergyRequirement;
import cn.howxu.mmcr.api.publicapi.recipe.RecipeIo;
import java.util.ArrayList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static cn.howxu.mmcr.api.publicapi.machine.BlockPredicate.any;
import static cn.howxu.mmcr.api.publicapi.machine.BlockPredicate.block;
import static cn.howxu.mmcr.api.publicapi.ApiIds.id;
import static org.nibelungorum.builtin.NETWORK_PRODUCER_MACHINE.NETWORK_CENTER_MACHINE;
import static org.nibelungorum.builtin.NETWORK_PRODUCER_MACHINE.REPORT_POWER;

/**
 * @author howxu <dev@howxu.cn>
 */
@EventBusSubscriber
public class NETWORK_CENTER_MACHINE {

    private static final ResourceLocation CENTER_POWER = id("center_power");
    private static final ResourceLocation CENTER_COUNT = id("center_count");
    private static final ResourceLocation CENTER_FE = id("center_fe");

    public static void registerDefinitions(MMCRMachineDefinationsEvent event) {
        if (!event.definitions().containsKey(NETWORK_CENTER_MACHINE)) {
            var machine = MachineBuilder
                    .machine(NETWORK_CENTER_MACHINE)
                    .recipePool(NETWORK_CENTER_MACHINE)
                    .displayNameKey("machine.mmcr.network_center_machine")
                    .appearance(a -> a.machineBasicBlock(ResourceLocation.parse("minecraft:black_wool")))
                    .networkInterface(1, 16)
                    .allowNetworkMachine(NETWORK_PRODUCER_MACHINE.NETWORK_PRODUCER_MACHINE)
                    .requestProcess(REPORT_POWER, (body, request, senderStorage, receiverStorage) -> {
                        if (receiverStorage == null) return;
                        double reported = body.get("power").flatMap(DataValue::asDouble).orElse(0.0);
                        long hash = request.peer().hash();
                        receiverStorage.set("power_" + hash, DataValue.of(reported));
                    })
                    .tickBehavior(behavior -> behavior.serverTick(context -> {
                        DataStorage storage = context.dataStorage();
                        if (storage == null) return;

                        var energyPlan = context.ioPlan();
                        energyPlan.addInput(new EnergyRequirement(RecipeIo.INPUT, 200));
                        var energySim = energyPlan.simulate();
                        boolean energyOk = energySim.energySatisfied() && energyPlan.commit().successful();

                        int liveCount = 0;
                        Set<String> connectedHashes = new HashSet<>();
                        var interfaces = NetworkApi.interfaces(context);
                        var iface = interfaces != null && !interfaces.isEmpty() ? interfaces.get(0) : null;
                        if (iface != null) {
                            for (var target : iface.connections()) {
                                liveCount = liveCount + 1;
                                connectedHashes.add(String.valueOf(target.hash()));
                            }
                        }

                        var staleKeys = new ArrayList<String>();
                        if (iface != null) {
                            for (var entry : storage.values().entrySet()) {
                                String keyString = entry.getKey();
                                if (keyString.startsWith("power_")
                                        && !connectedHashes.contains(keyString.substring("power_".length()))) {
                                    staleKeys.add(keyString);
                                }
                            }
                        }
                        for (var key : staleKeys) storage.remove(key);

                        double total = 0;
                        for (var entry : storage.values().entrySet()) {
                            if (entry.getKey().startsWith("power_")) {
                                total = total + entry.getValue().asDouble().orElse(0.0);
                            }
                        }

                        int count = liveCount;

                        context.screenText().append(ControllerScreenTextScope.OPERATION, CENTER_POWER,
                                Component.literal("Total Power: " + total + " tfps"));
                        context.screenText().append(ControllerScreenTextScope.OPERATION, CENTER_COUNT,
                                Component.literal("Connected Devices: " + count));
                        context.screenText().append(ControllerScreenTextScope.OPERATION, CENTER_FE,
                                Component.literal(energyOk ? "Energy: OK" : "Energy: LOW"));

                        context.jadeText().append(CENTER_POWER,
                                Component.literal("Total Power: " + total + " tfps"));
                        context.jadeText().append(CENTER_COUNT,
                                Component.literal("Connected Devices: " + count + " producers"));
                    }))
                    .build();
            event.registerMachine(machine);
        }
    }

    @SubscribeEvent
    public static void registerStructures(MMCRMachineStructuresEvent event) {
        if (!event.structures().containsKey(NETWORK_CENTER_MACHINE)) {
            var structure = MachineStructureBuilder
                    .structure()
                    .fullStructure(s -> s
                            .pattern(p -> p
                                    .layer("XXXX", "XAAX", "XXXX")
                                    .layer("XXXX", "A  A", "XXXX")
                                    .layer("XXXX", "A  A", "XXXX")
                                    .layer("XXXX", "XCAX", "XXXX")
                                    .where('X', block(Blocks.BLACK_WOOL))
                                    .where('A', any(
                                            InterfacePredicates.anyOfEnergyInput(),
                                            InterfacePredicates.networkInterface(),
                                            InterfacePredicates.dataStorage(),
                                            block(Blocks.RED_TERRACOTTA)
                                    ))
                                    .controller('C')
                            )
                    )
                    .build(NETWORK_CENTER_MACHINE);
            event.registerStructure(structure);
        }
    }
}
