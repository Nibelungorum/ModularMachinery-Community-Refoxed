ServerEvents.recipes( event => {
    event.custom({
        type: 'mmcr:machine_recipe',
        machine: 'mmcr_kubejs:kubejs_distillation_tower',
        tick_time: 300,
        allow_partial_outputs: true, // Discard outputs when the structure lacks enough output ports.
        requirements: [
            {
                type: 'minecraft:item',
                io: 'input',
                item: '#minecraft:logs',
                count: 1
            },
            // This recipe has three outputs. Without a three-level distillation tower,
            // outputs without an available port are discarded.
            {
                type: 'minecraft:item',
                io: 'output',
                stack: {
                    id: 'minecraft:coal',
                    count: 3
                }
            },
            {
                type: 'minecraft:item',
                io: 'output',
                stack: {
                    id: 'minecraft:charcoal',
                    count: 4
                }
            },
            {
                type: 'minecraft:item',
                io: 'output',
                stack: {
                    id: 'minecraft:gunpowder',
                    count: 3
                },
                chance: 0.2
            },
            {
                type: 'neoforge:energy',
                io: 'input',
                fe_per_tick: 20
            }
        ]
    })
})
