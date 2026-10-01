ServerEvents.recipes(event => {
    event.custom({
        type: 'mmcr:machine_recipe',
        recipe_pool: 'mmcr_kubejs:bench_1',
        tick_time: 600,
        max_threads: 8,
        parallelized: true,
        requirements: [
            {
                type: 'minecraft:item',
                io: 'input',
                item: { item: 'minecraft:iron_ingot' },
                count: 1
            },
            {
                type: 'minecraft:item',
                io: 'output',
                stack:{
                    id: 'minecraft:diamond',
                    count: 9
                } 
            }
        ]
    })
})
