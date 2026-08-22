MMCREvents.server(event => {
    const api = event.getAPI()
    const structure = event.createStructure("mmcr_kubejs:kubejs_reactor")

    // Is it complex? do not worry, I just use the export tool uhh
    // what you need care is just building it once in the game
    structure
        .pattern(['  AAAAA  ','         ','         ','         ','         ','         ','         ','         '])
        .pattern([' AAXXXAA ','   DDD   ','         ','         ','         ','         ','         ','         '])
        .pattern(['AAXXXXXAA','  EFFFE  ','  EFFFE  ','  EFFFE  ','  JJJJJ  ','         ','         ','         '])
        .pattern(['AXXXXXXXA',' DFGHGFD ','  FGHGF  ','  FGHGF  ','  JXXXJ  ','   KKK   ','         ','         '])
        .pattern(['AXXXXXXXA',' DFHXHFD ','  FHXHF  ','  FHXHF  ','  JXXXJ  ','   KLK   ','    L    ','    M    '])
        .pattern(['AXXXXXXXA',' DFGHGFD ','  FGHGF  ','  FGHGF  ','  JXXXJ  ','   KKK   ','         ','         '])
        .pattern(['AAXXXXXAA','  EFFFE  ','  EFFFE  ','  EFFFE  ','  JJJJJ  ','         ','         ','         '])
        .pattern([' AAXXXAA ','   DID   ','         ','         ','         ','         ','         ','         '])
        .pattern(['  AAAAA  ','         ','         ','         ','         ','         ','         ','         '])

        // wowowo, this is export too
        .set('X', api.block('minecraft:blue_ice'))
        .set('A', api.block('minecraft:deepslate_brick_stairs'))
        .set('D', api.anyOf(
            structure.anyOfItemInput(),
            structure.anyOfItemOutput(),
            structure.anyOfFluidOutput(),
            structure.anyOfFluidInput(),
            structure.anyOfEnergyOutput(), // hey, reactor may not need energy input
            api.block('minecraft:blue_ice')
        ))
        .set('E', api.block('minecraft:polished_deepslate'))
        .set('F', api.block('minecraft:black_stained_glass'))
        .set('G', api.block('minecraft:emerald_block'))
        .set('H', api.block('minecraft:lapis_block'))
        .set('J', api.block('minecraft:polished_deepslate_stairs'))
        .set('K', api.block('minecraft:deepslate_brick_slab'))
        .set('L', api.block('minecraft:deepslate_tiles'))
        .set('M', api.block('minecraft:oxidized_lightning_rod'))
        .controller('I')

        // Do not forget this
        .build()

    // Move to server_scripts/recipe/A_BlockState_Machine.js see how to create some special recipes
})
