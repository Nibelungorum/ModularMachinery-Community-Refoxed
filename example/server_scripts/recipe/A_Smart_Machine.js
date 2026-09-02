ServerEvents.recipes( event => {
    event.custom({
        type: 'mmcr:machine_recipe',
        machine: 'mmcr_kubejs:kubejs_purpur_furnace',
        tick_time: 100,
        parallelized: true,
        requirements: [
            {
                type: 'minecraft:item',
                io: 'input',
                item: 'minecraft:stick',
                count: 4
            },
            {
                type: 'minecraft:item',
                io: 'output',
                stack: {
                    id: 'minecraft:iron_ingot',
                    count: 10
                }
            },
            {
                type: 'neoforge:energy',
                io: 'input',
                fe_per_tick: 10
            },
            {
                type: 'mmcr:smart_interface', // Add a smart interface requirement.
                io: 'input', // Use output to change the interface value instead.
                interface_type: 'kubejs_mode',
                min_value: 1,
                max_value: 1 // Equal bounds require exactly one value.
            }
        ]
    })

    event.custom({
        type: 'mmcr:machine_recipe',
        machine: 'mmcr_kubejs:kubejs_purpur_furnace',
        tick_time: 100,
        parallelized: true,
        requirements: [
            {
                type: 'minecraft:item',
                io: 'input',
                item: 'minecraft:stick',
                count: 4
            },
            {
                type: 'minecraft:item',
                io: 'output',
                stack: {
                    id: 'minecraft:diamond',
                    count: 10
                }
            },
            {
                type: 'neoforge:energy',
                io: 'input',
                fe_per_tick: 1
            },
            {
                type: 'mmcr:smart_interface',
                io: 'input',
                interface_type: 'kubejs_mode',
                min_value: 2,
                max_value: 3
            }
        ]
    })

    event.custom({
        type: 'mmcr:machine_recipe',
        machine: 'mmcr_kubejs:kubejs_purpur_furnace',
        tick_time: 100,
        parallelized: true,
        requirements: [
            {
                type: 'minecraft:item',
                io: 'input',
                item: 'minecraft:stick',
                count: 4
            },
            {
                type: 'minecraft:item',
                io: 'output',
                stack: {
                    id: 'minecraft:gold_ingot',
                    count: 10,
                    components: {
                        'minecraft:custom_name': {
                            text: 'Too low Conversation!'
                        },
                        'minecraft:enchantments': {
                            'minecraft:sharpness': 2
                        }
                    }
                }
            },
            {
                type: 'neoforge:energy',
                io: 'input',
                fe_per_tick: 10
            },
            {
                type: 'mmcr:smart_interface',
                io: 'input',
                interface_type: 'kubejs_conversation',
                min_value: 0,
                max_value: 0.5
            }
        ]
    })

    // The smart interface recipes are complete.
})
