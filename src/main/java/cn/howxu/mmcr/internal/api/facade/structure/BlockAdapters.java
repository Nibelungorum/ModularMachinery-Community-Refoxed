package cn.howxu.mmcr.internal.api.facade.structure;

import cn.howxu.mmcr.api.machine.definition.BlockPredicate;
import cn.howxu.mmcr.api.machine.definition.InterfacePredicates;
import cn.howxu.mmcr.publicapi.structure.BlockCondition;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Internal condition factory delegation.
 * @author howxu <dev@howxu.cn>
 */
public final class BlockAdapters {
    private BlockAdapters() {}
    public static BlockCondition block(Block block) { return StructureAdapters.wrap(BlockPredicate.block(block)); }
    public static BlockCondition block(String id) { return StructureAdapters.wrap(BlockPredicate.block(id)); }
    public static BlockCondition block(ResourceLocation id) { return StructureAdapters.wrap(BlockPredicate.block(id)); }
    public static BlockCondition state(String state) { return StructureAdapters.wrap(BlockPredicate.state(state)); }
    public static BlockCondition state(ResourceLocation id) { return StructureAdapters.wrap(BlockPredicate.state(id)); }
    public static BlockCondition blockState(BlockState state) { return StructureAdapters.wrap(BlockPredicate.blockState(state)); }
    public static BlockCondition deferredBlock(Supplier<? extends Block> supplier) {
        return StructureAdapters.wrap(BlockPredicate.deferredBlock(supplier));
    }
    public static BlockCondition tag(TagKey<Block> tag) { return StructureAdapters.wrap(BlockPredicate.tag(tag)); }
    public static BlockCondition coupler() { return StructureAdapters.wrap(BlockPredicate.coupler()); }
    public static BlockCondition any(BlockCondition... conditions) { return anyOf(Arrays.asList(conditions)); }
    public static BlockCondition anyOf(Collection<BlockCondition> conditions) {
        return StructureAdapters.wrap(BlockPredicate.anyOf(conditions.stream().map(StructureAdapters::unwrap).toList()));
    }
    public static BlockCondition itemInput() { return StructureAdapters.wrap(InterfacePredicates.anyItemInput()); }
    public static BlockCondition itemOutput() { return StructureAdapters.wrap(InterfacePredicates.anyItemOutput()); }
    public static BlockCondition fluidInput() { return StructureAdapters.wrap(InterfacePredicates.anyFluidInput()); }
    public static BlockCondition fluidOutput() { return StructureAdapters.wrap(InterfacePredicates.anyFluidOutput()); }
    public static BlockCondition energyInput() { return StructureAdapters.wrap(InterfacePredicates.anyEnergyInput()); }
    public static BlockCondition energyOutput() { return StructureAdapters.wrap(InterfacePredicates.anyEnergyOutput()); }
    public static BlockCondition chemicalInput() { return StructureAdapters.wrap(InterfacePredicates.anyChemicalInput()); }
    public static BlockCondition chemicalOutput() { return StructureAdapters.wrap(InterfacePredicates.anyChemicalOutput()); }
    public static BlockCondition radioactiveChemicalInput() { return StructureAdapters.wrap(InterfacePredicates.anyRadioactiveChemicalInput()); }
    public static BlockCondition radioactiveChemicalOutput() { return StructureAdapters.wrap(InterfacePredicates.anyRadioactiveChemicalOutput()); }
    public static BlockCondition heatInput() { return StructureAdapters.wrap(InterfacePredicates.anyHeatInput()); }
    public static BlockCondition heatOutput() { return StructureAdapters.wrap(InterfacePredicates.anyHeatOutput()); }
    public static BlockCondition stressInput() { return StructureAdapters.wrap(InterfacePredicates.anyStressInput()); }
    public static BlockCondition stressOutput() { return StructureAdapters.wrap(InterfacePredicates.anyStressOutput()); }
    public static BlockCondition stressPorts() { return StructureAdapters.wrap(InterfacePredicates.anyStressPorts()); }
    public static BlockCondition itemPorts() { return StructureAdapters.wrap(InterfacePredicates.anyItemPorts()); }
    public static BlockCondition fluidPorts() { return StructureAdapters.wrap(InterfacePredicates.anyFluidPorts()); }
    public static BlockCondition energyPorts() { return StructureAdapters.wrap(InterfacePredicates.anyEnergyPorts()); }
    public static BlockCondition chemicalPorts() { return StructureAdapters.wrap(InterfacePredicates.anyChemicalPorts()); }
    public static BlockCondition radioactiveChemicalPorts() { return StructureAdapters.wrap(InterfacePredicates.anyRadioactiveChemicalPorts()); }
    public static BlockCondition heatPorts() { return StructureAdapters.wrap(InterfacePredicates.anyHeatPorts()); }
    public static BlockCondition upgradeBus() { return StructureAdapters.wrap(InterfacePredicates.anyUpgradeBus()); }
    public static BlockCondition ports() { return StructureAdapters.wrap(InterfacePredicates.ports()); }
    public static BlockCondition ports(String... ids) { return StructureAdapters.wrap(InterfacePredicates.anyOfPort(ids)); }
    public static BlockCondition ports(ResourceLocation... ids) { return StructureAdapters.wrap(InterfacePredicates.anyOfPort(ids)); }
    public static BlockCondition port(String id) { return StructureAdapters.wrap(InterfacePredicates.port(id)); }
    public static BlockCondition port(ResourceLocation id) { return StructureAdapters.wrap(InterfacePredicates.port(id)); }
    public static BlockCondition parallelControllers() { return StructureAdapters.wrap(InterfacePredicates.parallelControllers()); }
    public static BlockCondition factoryController() { return StructureAdapters.wrap(InterfacePredicates.factoryController()); }
    public static BlockCondition smartInterface() { return StructureAdapters.wrap(InterfacePredicates.smartInterface()); }
    public static BlockCondition dataStorage() { return StructureAdapters.wrap(InterfacePredicates.dataStorage()); }
    public static BlockCondition networkInterface() { return StructureAdapters.wrap(InterfacePredicates.networkInterface()); }
}
