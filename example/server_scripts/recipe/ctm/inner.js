ServerEvents.recipes(event => {
    event.custom({
        type: 'mmcr:machine_recipe',
        recipe_pool: 'mmcr_kubejs:inner_ctm',
        tick_time: 300,
        parallelized: true,
        requirements: [
            {
                type: 'minecraft:item',
                io: 'input',
                item: { item: 'minecraft:coal' },
                count: 1
            },
            {
                type: 'minecraft:item',
                io: 'input',
                item: { item: 'minecraft:raw_iron' },
                count: 8
            },
            {
                type: 'minecraft:item',
                io: 'output',
                stack: {
                    id: 'minecraft:iron_ingot',
                    count: 9
                }
            }
        ]
    })

})
