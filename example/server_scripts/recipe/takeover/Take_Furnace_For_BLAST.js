ServerEvents.recipes(event => {
    // get mc smelting recipes
    const smeltingRecipes = event.findRecipes({ type: 'minecraft:smelting' })
    // for each
    smeltingRecipes.forEach(recipe => {
        // this is mmcr recipe registry
        event.custom({
            type: 'mmcr:machine_recipe',
            recipe_pool: 'mmcr_kubejs:kubejs_blast_furnace',
            tick_time: recipe.get('cookingtime').intTicks(),
            requirements: [
                {
                    type: 'minecraft:item',
                    io: 'input',
                    item: recipe.get('ingredient'),
                    count: 1
                },
                {
                    type: 'minecraft:item',
                    io: 'output',
                    stack: recipe.get('result')
                }
            ]
        }).id(`mmcr_kubejs:alloy_furnace/from_smelting/${recipe.path}`)
    })
})