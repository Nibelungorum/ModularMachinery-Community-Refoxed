ServerEvents.recipes( event => {
    event.custom({
        type: 'mmcr:machine_recipe',
        machine: 'mmcr_kubejs:kubejs_thermal_smelting_furnace',
        tick_time: 300,
        // Use level_requirements to require a specific machine level.
        level_requirements: [
            {
                type: 'mmcr_kubejs:thermal_smelting_coil',
                level: 'mmcr_kubejs:thermal_smelting_coil_iron'
            }
        ],
        // Define the item and energy requirements.
        requirements: [
            {
                type: 'minecraft:item',
                io: 'input',
                item: 'minecraft:coal',
                count: 10000
            },
            {
                type: 'minecraft:item',
                io: 'input',
                item: 'minecraft:raw_iron',
                count: 8
            },
            {
                type: 'minecraft:item',
                io: 'output',
                stack: {
                    id: 'minecraft:iron_ingot',
                    count: 9
                }
            },
            {
                type: 'neoforge:energy',
                io: 'input',
                fe_per_tick: 10
            }
        ]
    })

    event.custom({
        type: 'mmcr:machine_recipe',
        machine: 'mmcr_kubejs:kubejs_thermal_smelting_furnace',
        tick_time: 300,
        level_requirements: [
            {
                type: 'mmcr_kubejs:thermal_smelting_coil', // Registered level type.
                level: 'mmcr_kubejs:thermal_smelting_coil_gold' // Required level.
            }
        ],
        // Define the item and energy requirements.
        requirements: [
            {
                type: 'minecraft:item',
                io: 'input',
                item: 'minecraft:coal',
                count: 1
            },
            {
                type: 'minecraft:item',
                io: 'input',
                item: 'minecraft:raw_gold',
                count: 8
            },
            {
                type: 'minecraft:item',
                io: 'output',
                stack: {
                    id: 'minecraft:gold_ingot',
                    count: 9
                }
            },
            {
                type: 'neoforge:energy',
                io: 'input',
                fe_per_tick: 12
            }
        ]
    })

    event.custom({
        type: 'mmcr:machine_recipe',
        machine: 'mmcr_kubejs:kubejs_thermal_smelting_furnace',
        tick_time: 300,
        max_threads: 4,
        level_requirements: [
            {
                type: 'mmcr_kubejs:thermal_smelting_coil', // Registered level type.
                level: 'mmcr_kubejs:thermal_smelting_coil_diamond' // Required level.
            }
        ],
        // magic uhh
        requirements: [
            {
                type: 'minecraft:item',
                io: 'input',
                item: 'minecraft:coal',
                count: 1
            },
            {
                type: 'minecraft:item',
                io: 'input',
                item: 'minecraft:raw_copper',
                count: 8
            },
            {
                type: 'minecraft:item',
                io: 'output',
                stack: {
                    id: 'minecraft:copper_ingot',
                    count: 9
                }
            },
            {
                type: 'neoforge:energy',
                io: 'input',
                fe_per_tick: 10
            }
        ]
    })

})
