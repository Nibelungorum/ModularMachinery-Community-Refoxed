MMCREvents.server(event => {
    const api = event.getAPI()
    const structure_1 = event.createStructure("kubejs:server")

    structure_1
        .pattern("XX", "XA", "XA", "XA", "XX")
        .pattern("XA", "BD", "BD", "BD", "XE")
        .pattern("XA", "BD", "BD", "BD", "XE")
        .pattern("XA", "BD", "BD", "BD", "XE")
        .pattern("XX", "XC", "XE", "XE", "XX")
        .set('X', api.block('mmcr:basic_casing'))
        .set('A', api.anyOf(
            api.block('mekanism:block_tin'),
            api.anyOfFluidInput(),
            api.anyOfEnergyInput(),
            api.networkInterface(),
            api.dataStorage()
            )
        )
        .set('B', api.block('mekanism:superheating_element'))
        .set('D', api.block('ae2:16k_crafting_storage'))
        .set('E', api.block('mekanism:block_tin'))
        .controller('C')
        .build()

    const structure_2 = event.createStructure("kubejs:center")

    structure_2
        .pattern("XXXXX", "XAAAX", "XAAAX", "XXXXX")
        .pattern("XXXXX", "A   A", "A   A", "XBBBX")
        .pattern("XXXXX", "A D A", "A E A", "XBBBX")
        .pattern("XXXXX", "A   A", "A   A", "XBBBX")
        .pattern("XFFFX", "FGCGF", "FGGGF", "XFFFX")
        .set('X', api.block('mekanism:induction_casing'))
        .set('A', api.block('ae2:sky_stone_brick'))
        .set('B', api.block('mekanism:superheating_element'))
        .set('D', api.block('mmcr:data_storage'))
        .set('E', api.block('ae2:controller'))
        .set('F', api.anyOf(
            api.anyOfEnergyInput(),
            api.networkInterface(),
            api.block('mmcr:basic_casing')
            )
        )
        .set('G', api.block('ae2:quartz_vibrant_glass'))
        .controller('C')
        .build()
})