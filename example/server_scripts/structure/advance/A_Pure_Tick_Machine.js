MMCREvents.server(event => {
    const api = event.getAPI()
    const structure = event.createStructure("mmcr_kubejs:kubejs_pure_tick_machine")

    // superise! it's the same to recipe ticker...yeah I do not have any structure avalaible...
    structure
        .pattern("XXX", "AAA", "XXX")
        .pattern("XXX", "A A", "X X")
        .pattern("XXX", "ACA", "XXX")
        .set('X', api.block('minecraft:green_terracotta'))
        .set('A', api.anyOf(
            api.anyOfItemInput(),
            api.anyOfItemOutput(),
            api.anyOfEnergyInput(),
            api.parallelControllers(),
            api.factoryController(),
            api.block('minecraft:green_wool')
        ))
        .set('D', api.block('minecraft:reinforced_deepslate'))
        .controller('C')
        .build()
})