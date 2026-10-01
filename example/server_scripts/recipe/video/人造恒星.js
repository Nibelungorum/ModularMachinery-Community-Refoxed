ServerEvents.recipes(event => {
    event.custom({
        type: 'mmcr:machine_recipe',
        recipe_pool: 'mmcr:artificial_star_video',
        tick_time: 300,
        requirements: [
            {
                type: 'minecraft:item',
                io: 'input',
                item: { item: 'minecraft:apple' },
                count: 1,
                consume_chance: 0 // no cosume
            },
            {
                type: 'neoforge:energy',
                io: 'output',
                fe_per_tick: 200
            }
        ]
    })
})
