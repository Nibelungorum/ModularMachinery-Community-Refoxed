package cn.howxu.mmcr.publicapi.structure;

import cn.howxu.mmcr.internal.api.facade.structure.BlockAdapters;
import java.util.Collection;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Condition factories, including registered port families; deferred blocks stay lazy.
 * @author howxu <dev@howxu.cn>
 */
public final class BlockConditions {
    private BlockConditions() {}
    public static BlockCondition block(Block block) { return BlockAdapters.block(block); }
    public static BlockCondition block(String id) { return BlockAdapters.block(id); }
    public static BlockCondition block(ResourceLocation id) { return BlockAdapters.block(id); }
    public static BlockCondition state(String state) { return BlockAdapters.state(state); }
    public static BlockCondition state(ResourceLocation id) { return BlockAdapters.state(id); }
    public static BlockCondition blockState(BlockState state) { return BlockAdapters.blockState(state); }
    public static BlockCondition deferredBlock(Supplier<? extends Block> supplier) { return BlockAdapters.deferredBlock(supplier); }
    public static BlockCondition tag(TagKey<Block> tag) { return BlockAdapters.tag(tag); }
    public static BlockCondition coupler() { return BlockAdapters.coupler(); }
    public static BlockCondition any(BlockCondition... conditions) { return BlockAdapters.any(conditions); }
    public static BlockCondition anyOf(Collection<BlockCondition> conditions) { return BlockAdapters.anyOf(conditions); }
    public static BlockCondition itemInput() { return BlockAdapters.itemInput(); }
    public static BlockCondition itemOutput() { return BlockAdapters.itemOutput(); }
    public static BlockCondition fluidInput() { return BlockAdapters.fluidInput(); }
    public static BlockCondition fluidOutput() { return BlockAdapters.fluidOutput(); }
    public static BlockCondition energyInput() { return BlockAdapters.energyInput(); }
    public static BlockCondition energyOutput() { return BlockAdapters.energyOutput(); }
    public static BlockCondition sourceInput() { return BlockAdapters.sourceInput(); }
    public static BlockCondition sourceOutput() { return BlockAdapters.sourceOutput(); }
    public static BlockCondition sourcePorts() { return BlockAdapters.sourcePorts(); }
    public static BlockCondition chemicalInput() { return BlockAdapters.chemicalInput(); }
    public static BlockCondition chemicalOutput() { return BlockAdapters.chemicalOutput(); }
    public static BlockCondition radioactiveChemicalInput() { return BlockAdapters.radioactiveChemicalInput(); }
    public static BlockCondition radioactiveChemicalOutput() { return BlockAdapters.radioactiveChemicalOutput(); }
    public static BlockCondition heatInput() { return BlockAdapters.heatInput(); }
    public static BlockCondition heatOutput() { return BlockAdapters.heatOutput(); }
    public static BlockCondition itemPorts() { return BlockAdapters.itemPorts(); }
    public static BlockCondition fluidPorts() { return BlockAdapters.fluidPorts(); }
    public static BlockCondition energyPorts() { return BlockAdapters.energyPorts(); }
    public static BlockCondition chemicalPorts() { return BlockAdapters.chemicalPorts(); }
    public static BlockCondition radioactiveChemicalPorts() { return BlockAdapters.radioactiveChemicalPorts(); }
    public static BlockCondition heatPorts() { return BlockAdapters.heatPorts(); }
    public static BlockCondition upgradeBus() { return BlockAdapters.upgradeBus(); }
    public static BlockCondition ports() { return BlockAdapters.ports(); }
    public static BlockCondition ports(String... ids) { return BlockAdapters.ports(ids); }
    public static BlockCondition ports(ResourceLocation... ids) { return BlockAdapters.ports(ids); }
    public static BlockCondition port(String id) { return BlockAdapters.port(id); }
    public static BlockCondition port(ResourceLocation id) { return BlockAdapters.port(id); }
    public static BlockCondition parallelControllers() { return BlockAdapters.parallelControllers(); }
    public static BlockCondition factoryController() { return BlockAdapters.factoryController(); }
    public static BlockCondition smartInterface() { return BlockAdapters.smartInterface(); }
    public static BlockCondition dataStorage() { return BlockAdapters.dataStorage(); }
    public static BlockCondition networkInterface() { return BlockAdapters.networkInterface(); }
}
