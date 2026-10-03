ServerEvents.recipes( event => {
    event.custom({
        type: 'mmcr:machine_recipe',
        recipe_pool: 'mmcr_kubejs:magic',
        tick_time: 200,
        requirements: [
            {
                type: 'minecraft:item',
                io: 'input',
                item: { item: 'minecraft:apple' },
                count: 1
            },
            {
                type: 'minecraft:item',
                io: 'output',
                stack: {
                    id: 'minecraft:golden_apple',
                    count: 1
                }
            },
            // one time
            {
                type: 'ars_nouveau:source',
                io: 'input',
                amount: 300
            },
            {
                type: 'ars_nouveau:source',
                io: 'output',
                amount: 50
            }
        ]
    })
})
