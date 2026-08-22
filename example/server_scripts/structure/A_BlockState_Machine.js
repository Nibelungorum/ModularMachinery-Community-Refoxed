MMCREvents.server(event => {
    const api = event.getAPI()
    const structure = event.createStructure("mmcr_kubejs:kubejs_reactor")

    // Is it complex? do not worry, I just use the export tool uhh
    // what you need care is just building it once in the game
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
            structure.anyOfItemInput(),
            structure.anyOfItemOutput(),
            structure.anyOfFluidOutput(),
            structure.anyOfFluidInput(),
            structure.anyOfEnergyOutput(), // hey, reactor may not need energy input
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
        // here I must tell you something you must pay attention
        // the export tool didn't know which block has exact block state
        // for example, if minecraft:deepslate_brick_stairs is placed with no state, it will be an api.block,
        // so it will not been adjusted, and cause some problems
        // you have to adjust these block but block state by hand
        .set('c', api.state('minecraft:deepslate_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]'))
        .controller('C')

        // Do not forget this
        .build()

    // Move to server_scripts/recipe/A_BlockState_Machine.js see how to create some special recipes
})
