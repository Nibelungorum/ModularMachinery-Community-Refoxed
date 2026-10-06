ServerEvents.recipes(event => {
    event.custom({
        type: 'mmcr:machine_recipe',
        recipe_pool: 'mmcr_kubejs:gateway',
        tick_time: 200,
        requirements: [
            {
                type: 'minecraft:item',
                io: 'input',
                item: { item: 'botania:manasteel_ingot' },
                count: 2
            },
            {
                type: 'minecraft:item',
                io: 'output',
                stack: {
                    id: 'botania:elementium_ingot',
                    count: 1
                }
            },
            // Consume the total mana once when the recipe starts, not every tick.
            {
                type: 'botania:mana',
                io: 'input',
                amount: 10000
            },
            // Example mana return: produce once when the recipe completes.
            {
                type: 'botania:mana',
                io: 'output',
                amount: 1000
            }
        ]
    }).id('mmcr_kubejs:gateway/elementium_ingot')
})
