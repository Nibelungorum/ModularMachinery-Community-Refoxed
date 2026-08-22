ServerEvents.recipes( event => {
    event.custom({
        type: 'mmcr:machine_recipe',
        machine: 'mmcr_kubejs:kubejs_purpur_furnace',
        tick_time: 100,
        parallelized: true,
        requirements: [
            {
                type: 'item',
                io: 'input',
                item: 'minecraft:stick', // magic uhh
                count: 4
            },
            {
                type: 'item',
                io: 'output',
                stack: {
                    id: 'minecraft:iron_ingot',
                    count: 10
                }
            },
            {
                type: 'energy',
                io: 'input',
                fe_per_tick: 10
            },
            {
                type: 'smart_interface', // we add smart interface here
                io: 'input', // when set to output, will change the value of smart interface
                interface_type: 'kubejs_mode',
                min_value: 1,
                max_value: 1 // set to one value just make min == max
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
                type: 'item',
                io: 'input',
                item: 'minecraft:stick',
                count: 4
            },
            {
                type: 'item',
                io: 'output',
                stack: {
                    id: 'minecraft:diamond',
                    count: 10
                }
            },
            {
                type: 'energy',
                io: 'input',
                fe_per_tick: 1
            },
            {
                type: 'smart_interface',
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
                type: 'item',
                io: 'input',
                item: 'minecraft:stick',
                count: 4
            },
            {
                type: 'item',
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
                type: 'energy',
                io: 'input',
                fe_per_tick: 10
            },
            {
                type: 'smart_interface',
                io: 'input',
                interface_type: 'kubejs_conversation',
                min_value: 0,
                max_value: 0.5
            }
        ]
    })

    // you have finished your first machine again ! just launch game and see what happen
})