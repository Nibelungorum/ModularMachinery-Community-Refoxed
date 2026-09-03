MMCREvents.server(event => {
    const api = event.getAPI()
    const structure_1 = event.createStructure("mmcr_kubejs:kubejs_network_producer_machine")

    structure_1
        .pattern("XXXX", "XAAX", "XXXX")
        .pattern("XXXX", "A  A", "XXXX")
        .pattern("XXXX", "A  A", "XXXX")
        .pattern("XXXX", "XCAX", "XXXX")
        .set('X', api.block('minecraft:white_wool'))
        .set('A', api.anyOf(
            api.anyOfFluidInput(),
            api.anyOfEnergyInput(),
            api.networkInterface(),
            api.block('minecraft:red_terracotta')
        ))
        .controller('C')
        .build()
    
    const structure_2 = event.createStructure("mmcr_kubejs:kubejs_network_center_machine")

    structure_2
        .pattern("XXXX", "XAAX", "XXXX")
        .pattern("XXXX", "A  A", "XXXX")
        .pattern("XXXX", "A  A", "XXXX")
        .pattern("XXXX", "XCAX", "XXXX")
        .set('X', api.block('minecraft:black_wool'))
        .set('A', api.anyOf(
            api.anyOfEnergyInput(),
            api.networkInterface(),
            api.block('minecraft:red_terracotta')
        ))
        .controller('C')
        .build()
})