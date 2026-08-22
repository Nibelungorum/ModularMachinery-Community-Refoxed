ServerEvents.recipes( event => {
    event.custom({
        type: 'mmcr:machine_recipe',
        machine: 'mmcr_kubejs:kubejs_distillation_tower',
        tick_time: 300,
        allow_partial_outputs: true, // this make a special recipe, which means if there is not enough ports, some products will be trashed
        requirements: [
            {
                type: 'item',
                io: 'input',
                item: '#minecraft:logs',
                count: 1
            },
            // Here are 3 products, but if you do not build one 3-levels distillation tower
            // 2 of the output will trash directly
            {
                type: 'item',
                io: 'output',
                stack: {
                    id: 'minecraft:coal',
                    count: 3
                }
            },
            {
                type: 'item',
                io: 'output',
                stack: {
                    id: 'minecraft:charcoal',
                    count: 4
                }
            },
            {
                type: 'item',
                io: 'output',
                stack: {
                    id: 'minecraft:gunpowder',
                    count: 3
                },
                chance: 0.2
            },
            {
                type: 'energy',
                io: 'input',
                fe_per_tick: 20
            }
        ]
    })
})