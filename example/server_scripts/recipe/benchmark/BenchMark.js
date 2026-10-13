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

    event.custom({
        type: 'mmcr:machine_recipe',
        recipe_pool: 'mmcr_kubejs:bench_1',
        tick_time: 200,
        max_threads: 1,
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
                io: 'input',
                item: { item: 'kubejs:plate_mold' },
                count: 1,
                consume_chance: 0
            },
            {
                type: 'minecraft:item',
                io: 'output',
                stack: {
                    id: 'kubejs:iron_plate',
                    count: 2
                }
            }
        ]
    })

    event.custom({
        type: 'mmcr:machine_recipe',
        recipe_pool: 'mmcr_kubejs:bench_1',
        tick_time: 200,
        max_threads: 1,
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
                io: 'input',
                item: { item: 'kubejs:gear_mold' },
                count: 1,
                consume_chance: 0
            },
            {
                type: 'minecraft:item',
                io: 'output',
                stack: {
                    id: 'kubejs:iron_gear',
                    count: 2
                }
            }
        ]
    })
})
