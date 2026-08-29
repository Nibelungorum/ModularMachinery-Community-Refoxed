MMCREvents.server(event => {
    const api = event.getAPI()
    const structure = event.createStructure("mmcr_kubejs:kubejs_recipe_ticker")

    structure
        .pattern("XXX", "AAA", "XXX")
        .pattern("XXX", "A A", "X X")
        .pattern("XXX", "ACA", "XXX")
        .set('X', api.block('minecraft:green_terracotta')) // Use a level slot to match the corresponding level block.
        .set('A', api.anyOf(
            api.anyOfItemInput(),
            api.anyOfItemOutput(),
            api.anyOfEnergyInput(),
            api.parallelControllers(), // this is all parallel controller
            api.block('minecraft:green_wool')
        ))
        .set('D', api.block('minecraft:reinforced_deepslate'))
        .controller('C')
        // Build the structure.
        .build()

    // See server_scripts/recipe/A_Level_Machine.js for the recipe definitions.
})