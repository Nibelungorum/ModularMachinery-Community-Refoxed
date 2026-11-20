package cn.howxu.mmcr.compat.athena;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelDispatcher;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelDispatcher.SimpleModelSelectors;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.neoforged.neoforge.client.model.block.CustomBlockModelDefinition;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Reads only the installed renderer's variants, so optional model types never need to resolve on the vanilla path.
 *
 * @author howxu <dev@howxu.cn>
 */
public record AthenaVariantsDefinition(SimpleModelSelectors variants) implements CustomBlockModelDefinition {
    public static final MapCodec<AthenaVariantsDefinition> CODEC = codecFor(AthenaModelBridgeBootstrap.isAthenaLoaded());

    static MapCodec<AthenaVariantsDefinition> codecFor(boolean athenaLoaded) {
        return SimpleModelSelectors.CODEC.fieldOf(athenaLoaded ? "athena_variants" : "variants")
                .xmap(AthenaVariantsDefinition::new, AthenaVariantsDefinition::variants);
    }

    @Override
    public Map<BlockState, BlockStateModel.UnbakedRoot> instantiate(StateDefinition<Block, BlockState> states, Supplier<String> source) {
        return new BlockStateModelDispatcher(Optional.of(variants), Optional.empty()).instantiateVanilla(states, source);
    }

    @Override
    public MapCodec<AthenaVariantsDefinition> codec() {
        return CODEC;
    }
}
