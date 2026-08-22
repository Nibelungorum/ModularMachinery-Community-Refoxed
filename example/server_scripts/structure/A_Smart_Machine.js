MMCREvents.server(event => {
    const api = event.getAPI()
    const structure = event.createStructure("mmcr_kubejs:kubejs_purpur_furnace")

    structure
        .pattern(" ABBBD ", "       ", "       ", "       ", "  EEE  ", "       ", "       ", "       ")
        .pattern("AFXXXGD", "  HHH  ", "  III  ", "  JJJ  ", " EHHHE ", " KLLLM ", "       ", "       ")
        .pattern("NXXXXXO", " H   H ", " I   I ", " J   J ", "EH   HE", " PXXXQ ", "  EHE  ", "   R   ")
        .pattern("NXXXXXO", " H   H ", " I   I ", " J   J ", "EH   HE", " PX XQ ", "  H H  ", "  R R  ")
        .pattern("NXXXXXO", " H   H ", " I   I ", " J   J ", "EH   HE", " PXXXQ ", "  EHE  ", "   R   ")
        .pattern("STXXXUV", "  HCH  ", "  III  ", "  JJJ  ", " EHHHE ", " WYYYZ ", "       ", "       ")
        .pattern(" abbbV ", "       ", "       ", "       ", "  EEE  ", "       ", "       ", "       ")
        .set('X', api.block('minecraft:end_stone_bricks'))
        .set('A', api.state('minecraft:end_stone_brick_stairs[facing=south,half=bottom,shape=outer_left,waterlogged=false]'))
        .set('B', api.state('minecraft:end_stone_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]'))
        .set('D', api.state('minecraft:end_stone_brick_stairs[facing=west,half=bottom,shape=outer_left,waterlogged=false]'))
        .set('E', api.block('minecraft:end_stone_brick_slab'))
        .set('F', api.state('minecraft:end_stone_brick_stairs[facing=east,half=bottom,shape=inner_right,waterlogged=false]'))
        .set('G', api.state('minecraft:end_stone_brick_stairs[facing=south,half=bottom,shape=inner_right,waterlogged=false]'))
        .set('H', api.anyOf(
            api.block('minecraft:purpur_pillar'),
            structure.anyOfItemInput(),
            structure.anyOfItemOutput(),
            structure.anyOfEnergyInput(),
            structure.parallelControllers(),
            structure.smartInterface() // do not forget the smart interface
        ))
        .set('I', api.block('minecraft:purple_terracotta'))
        .set('J', api.block('minecraft:purpur_block'))
        .set('K', api.state('minecraft:purpur_stairs[facing=south,half=bottom,shape=outer_left,waterlogged=false]'))
        .set('L', api.state('minecraft:purpur_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]'))
        .set('M', api.state('minecraft:purpur_stairs[facing=west,half=bottom,shape=outer_left,waterlogged=false]'))
        .set('N', api.state('minecraft:end_stone_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]'))
        .set('O', api.state('minecraft:end_stone_brick_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]'))
        .set('P', api.state('minecraft:purpur_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]'))
        .set('Q', api.state('minecraft:purpur_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]'))
        .set('R', api.block('minecraft:purpur_slab'))
        .set('S', api.state('minecraft:end_stone_brick_stairs[facing=north,half=bottom,shape=outer_right,waterlogged=false]'))
        .set('T', api.state('minecraft:end_stone_brick_stairs[facing=north,half=bottom,shape=inner_right,waterlogged=false]'))
        .set('U', api.state('minecraft:end_stone_brick_stairs[facing=north,half=bottom,shape=inner_left,waterlogged=false]'))
        .set('V', api.state('minecraft:end_stone_brick_stairs[facing=west,half=bottom,shape=outer_right,waterlogged=false]'))
        .set('W', api.state('minecraft:purpur_stairs[facing=east,half=bottom,shape=outer_left,waterlogged=false]'))
        .set('Y', api.state('minecraft:purpur_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]'))
        .set('Z', api.state('minecraft:purpur_stairs[facing=north,half=bottom,shape=outer_left,waterlogged=false]'))
        .set('a', api.state('minecraft:end_stone_brick_stairs[facing=east,half=bottom,shape=outer_left,waterlogged=false]'))
        .set('b', api.state('minecraft:end_stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]'))

        // Do not forget this
        .build()

    // Move to server_scripts/recipe/A_Smart_Machine.js see how to create some special recipes
})


