MMCREvents.server(event => {
    const api = event.getAPI()
    const structure = event.createStructure("mmcr_kubejs:kubejs_reactor")

    // The structure is complex, but its layout can be copied from the export tool.
    // After exporting it, build the structure once in-game.
    structure
        .pattern("  ABBBD  ", "         ", "         ", "         ", "         ", "         ", "         ", "         ")
        .pattern(" AEXXXFD ", "   XXX   ", "         ", "         ", "         ", "         ", "         ", "         ")
        .pattern("AEXXXXXFD", "  GHHHG  ", "  GHHHG  ", "  GHHHG  ", "  IJJJK  ", "         ", "         ", "         ")
        .pattern("LXXXXXXXM", " XHNONHX ", "  HNONH  ", "  HNONH  ", "  PXXXQ  ", "   RRR   ", "         ", "         ")
        .pattern("LXXXXXXXM", " XHOXOHX ", "  HOXOH  ", "  HOXOH  ", "  PXXXQ  ", "   RSR   ", "    S    ", "    T    ")
        .pattern("LXXXXXXXM", " XHNONHX ", "  HNONH  ", "  HNONH  ", "  PXXXQ  ", "   RRR   ", "         ", "         ")
        .pattern("UVXXXXXWY", "  GHHHG  ", "  GHHHG  ", "  GHHHG  ", "  Zaaab  ", "         ", "         ", "         ")
        .pattern(" UVXXXWY ", "   XCX   ", "         ", "         ", "         ", "         ", "         ", "         ")
        .pattern("  UcccY  ", "         ", "         ", "         ", "         ", "         ", "         ", "         ")
        .set('X', api.anyOf(
            api.anyOfItemInput(),
            api.anyOfItemOutput(),
            api.anyOfFluidOutput(),
            api.anyOfFluidInput(),
            api.anyOfEnergyOutput(), // This reactor can output energy without requiring an energy input port.
            api.block('minecraft:blue_ice')
        ))
        .set('A', api.state('minecraft:deepslate_brick_stairs[facing=east,half=bottom,shape=outer_right,waterlogged=false]'))
        .set('B', api.state('minecraft:deepslate_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]'))
        .set('D', api.state('minecraft:deepslate_brick_stairs[facing=south,half=bottom,shape=outer_right,waterlogged=false]'))
        .set('E', api.state('minecraft:deepslate_brick_stairs[facing=south,half=bottom,shape=inner_left,waterlogged=false]'))
        .set('F', api.state('minecraft:deepslate_brick_stairs[facing=west,half=bottom,shape=inner_left,waterlogged=false]'))
        .set('G', api.block('minecraft:polished_deepslate'))
        .set('H', api.block('minecraft:black_stained_glass'))
        .set('I', api.state('minecraft:polished_deepslate_stairs[facing=east,half=bottom,shape=outer_right,waterlogged=false]'))
        .set('J', api.state('minecraft:polished_deepslate_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]'))
        .set('K', api.state('minecraft:polished_deepslate_stairs[facing=west,half=bottom,shape=outer_left,waterlogged=false]'))
        .set('L', api.state('minecraft:deepslate_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]'))
        .set('M', api.state('minecraft:deepslate_brick_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]'))
        .set('N', api.block('minecraft:emerald_block'))
        .set('O', api.block('minecraft:lapis_block'))
        .set('P', api.state('minecraft:polished_deepslate_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]'))
        .set('Q', api.state('minecraft:polished_deepslate_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]'))
        .set('R', api.block('minecraft:deepslate_brick_slab'))
        .set('S', api.block('minecraft:deepslate_tiles'))
        .set('T', api.block('minecraft:oxidized_lightning_rod'))
        .set('U', api.state('minecraft:deepslate_brick_stairs[facing=east,half=bottom,shape=outer_left,waterlogged=false]'))
        .set('V', api.state('minecraft:deepslate_brick_stairs[facing=north,half=bottom,shape=inner_right,waterlogged=false]'))
        .set('W', api.state('minecraft:deepslate_brick_stairs[facing=north,half=bottom,shape=inner_left,waterlogged=false]'))
        .set('Y', api.state('minecraft:deepslate_brick_stairs[facing=west,half=bottom,shape=outer_right,waterlogged=false]'))
        .set('Z', api.state('minecraft:polished_deepslate_stairs[facing=east,half=bottom,shape=outer_left,waterlogged=false]'))
        .set('a', api.block('minecraft:polished_deepslate_stairs'))
        .set('b', api.state('minecraft:polished_deepslate_stairs[facing=north,half=bottom,shape=outer_left,waterlogged=false]'))
        // Note: the export tool cannot determine the exact block state required by every block.
        // For example, a stair without recorded state is exported as api.block(), which can cause mismatches.
        // Replace it with the correct api.state() manually when necessary.
        .set('c', api.state('minecraft:deepslate_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]'))
        .controller('C')

        // Build the structure.
        .build()

    // See server_scripts/recipe/A_BlockState_Machine.js for the recipe definitions.
})
