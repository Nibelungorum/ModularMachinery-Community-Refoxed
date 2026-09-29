package org.nibelungorum.builtin;

import cn.howxu.mmcr.api.publicapi.data.DataStorage;
import cn.howxu.mmcr.api.publicapi.data.DataValue;
import cn.howxu.mmcr.api.publicapi.ReadableNumber;
import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextScope;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import cn.howxu.mmcr.api.publicapi.machine.InterfacePredicates;
import cn.howxu.mmcr.api.publicapi.machine.MachineBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineStructureBuilder;
import cn.howxu.mmcr.api.publicapi.machine.OutputPolicy;
import cn.howxu.mmcr.api.publicapi.machine.TickBehaviorContext;
import cn.howxu.mmcr.api.publicapi.recipe.RecipeIo;
import cn.howxu.mmcr.api.publicapi.recipe.EnergyRequirement;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.math.BigInteger;

import static cn.howxu.mmcr.api.publicapi.machine.BlockPredicate.any;
import static cn.howxu.mmcr.api.publicapi.machine.BlockPredicate.block;
import static cn.howxu.mmcr.api.publicapi.ApiIds.id;

/**
 * @author howxu <dev@howxu.cn>
 */
@EventBusSubscriber
public class DATA_STORAGE_MACHINE {

    private static final ResourceLocation DATA_STORAGE_MACHINE = id("data_storage_machine");
    private static final ResourceLocation FE_STATUS = id("fe_storage_status");

    public static void registerDefinitions(MMCRMachineDefinationsEvent event) {
        if (!event.definitions().containsKey(DATA_STORAGE_MACHINE)) {
            var machine = MachineBuilder
                    .machine(DATA_STORAGE_MACHINE)
                    .recipePool(DATA_STORAGE_MACHINE)
                    .displayNameKey("machine.mmcr.data_storage_machine")
                    .appearance(a -> a.machineBasicBlock(ResourceLocation.parse("minecraft:crying_obsidian")))
                    .tickBehavior(behavior -> behavior.serverTick(context -> {
                        DataStorage storage = context.dataStorage();
                        if (storage == null) return;

                        BigInteger stored = BigInteger.ZERO;
                        var saved = storage.get("energy");
                        if (saved.isPresent()) {
                            stored = saved.get().asBigInteger().orElse(BigInteger.ZERO);
                        }

                        if (context.isDue(5)) {
                            int available = (int) Math.min(context.ioView().energyInput(), Integer.MAX_VALUE);
                            int low = 0;
                            int high = available;

                            while (low < high) {
                                int candidate = low + (int) Math.ceil((high - low) / 2.0);

                                var probe = context.ioPlan();
                                probe.addInput(new EnergyRequirement(RecipeIo.INPUT, candidate));

                                if (probe.simulate().energySatisfied()) {
                                    low = candidate;
                                } else {
                                    high = candidate - 1;
                                }
                            }

                            if (low > 0) {
                                var inputPlan = context.ioPlan();
                                inputPlan.addInput(new EnergyRequirement(RecipeIo.INPUT, low));

                                BigInteger next = stored.add(BigInteger.valueOf(low));
                                var inputSimulation = inputPlan.simulate();

                                if (inputSimulation.energySatisfied() && inputPlan.commitData(transaction ->
                                        storage.set("energy", DataValue.of(next), transaction)).successful()) {
                                    stored = next;
                                }
                            }
                        }

                        if (context.isDue(5)) {
                            long outputCapacity = context.ioView().energyOutputCapacity();

                            if (outputCapacity > 0 && stored.signum() > 0) {
                                BigInteger requestedBig = stored.min(
                                        BigInteger.valueOf(Math.min(outputCapacity, Integer.MAX_VALUE)));
                                int requested = requestedBig.intValue();

                                if (requested > 0) {
                                    var outputPlan = context.ioPlan();
                                    outputPlan.addOutput(
                                            new EnergyRequirement(RecipeIo.OUTPUT, requested),
                                            OutputPolicy.ALLOW_PARTIAL);

                                    var simulation = outputPlan.simulate();
                                    var outputs = simulation.outputs();

                                    if (!outputs.isEmpty()) {
                                        long accepted = outputs.get(0).accepted();

                                        if (accepted > 0) {
                                            BigInteger finalStored = stored.subtract(BigInteger.valueOf(accepted));

                                        if (outputPlan.commitData(transaction ->
                                                storage.set("energy", DataValue.of(finalStored), transaction)).successful()) {
                                                stored = finalStored;
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (stored.signum() == 0) {
                            context.screenText().append(
                                    ControllerScreenTextScope.OPERATION,
                                    FE_STATUS,
                                    Component.literal("No FE stored."));
                            return;
                        }
                        context.screenText().append(
                                ControllerScreenTextScope.OPERATION,
                                FE_STATUS,
                                Component.literal("FE stored: " + ReadableNumber.formatCompact(stored)));
                    }))
                    .build();
            event.registerMachine(machine);
        }
    }

    @SubscribeEvent
    public static void registerStructures(MMCRMachineStructuresEvent event) {
        if (!event.structures().containsKey(DATA_STORAGE_MACHINE)) {
            var structure = MachineStructureBuilder
                    .structure()
                    .fullStructure(s -> s
                            .pattern(p -> p
                                    .layer("         ", "         ", "         ", "   AAA   ", "   ABA   ", "   AAA   ", "         ", "         ", "         ")
                                    .layer("         ", "         ", "  AAAAA  ", "  AXXXA  ", "  AXXXA  ", "  AXXXA  ", "  AAAAA  ", "         ", "         ")
                                    .layer("         ", "  AAAAA  ", " AXXXXXA ", " AXXXXXA ", " AXXXXXA ", " AXXXXXA ", " AXXXXXA ", "  AAAAA  ", "         ")
                                    .layer("   AAA   ", "  AXXXA  ", " AXXXXXA ", "AXXXXXXXA", "AXXXXXXXA", "AXXXXXXXA", " AXXXXXA ", "  AXXXA  ", "   AAA   ")
                                    .layer("   ABA   ", "  AXXXA  ", " AXXXXXA ", "AXXXXXXXA", "BXXXDXXXB", "AXXXXXXXA", " AXXXXXA ", "  AXXXA  ", "   ABA   ")
                                    .layer("   AAA   ", "  AXXXA  ", " AXXXXXA ", "AXXXXXXXA", "AXXXXXXXA", "AXXXXXXXA", " AXXXXXA ", "  AXXXA  ", "   AAA   ")
                                    .layer("         ", "  AAAAA  ", " AXXXXXA ", " AXXXXXA ", " AXXXXXA ", " AXXXXXA ", " AXXXXXA ", "  AAAAA  ", "         ")
                                    .layer("         ", "         ", "  AAAAA  ", "  AXXXA  ", "  AXXXA  ", "  AXXXA  ", "  AAAAA  ", "         ", "         ")
                                    .layer("         ", "         ", "         ", "   AAA   ", "   ACA   ", "   AAA   ", "         ", "         ", "         ")
                                    .where('X', block(Blocks.REDSTONE_BLOCK))
                                    .where('A', block(Blocks.CRYING_OBSIDIAN))
                                    .where('B', any(
                                            InterfacePredicates.anyOfEnergyInput(),
                                            InterfacePredicates.anyOfEnergyOutput()
                                    ))
                                    .where('D', InterfacePredicates.dataStorage())
                                    .controller('C')
                            )
                    )
                    .build(DATA_STORAGE_MACHINE);
            event.registerStructure(structure);
        }
    }
}
