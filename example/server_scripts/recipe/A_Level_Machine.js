ServerEvents.recipes( event => {
    event.custom({
        type: 'mmcr:machine_recipe',
        machine: 'mmcr_kubejs:kubejs_thermal_smelting_furnace',
        tick_time: 300,
        // use the object below to set level_requirement
        level_requirements: [
            {
                type: 'mmcr_kubejs:thermal_smelting_coil',
                level: 'mmcr_kubejs:thermal_smelting_coil_iron'
            }
        ],
        // magic uhh
        requirements: [
            {
                type: 'item',
                io: 'input',
                item: 'minecraft:coal',
                count: 1
            },
            {
                type: 'item',
                io: 'input',
                item: 'minecraft:raw_iron',
                count: 8
            },
            {
                type: 'item',
                io: 'output',
                stack: {
                    id: 'minecraft:iron_ingot',
                    count: 9
                }
            },
            {
                type: 'energy',
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
                type: 'mmcr_kubejs:thermal_smelting_coil', // the type register name
                level: 'mmcr_kubejs:thermal_smelting_coil_gold' // the level name
            }
        ],
        // magic uhh
        requirements: [
            {
                type: 'item',
                io: 'input',
                item: 'minecraft:coal',
                count: 1
            },
            {
                type: 'item',
                io: 'input',
                item: 'minecraft:raw_gold',
                count: 8
            },
            {
                type: 'item',
                io: 'output',
                stack: {
                    id: 'minecraft:gold_ingot',
                    count: 9
                }
            },
            {
                type: 'energy',
                io: 'input',
                fe_per_tick: 12
            }
        ]
    })

    event.custom({
        type: 'mmcr:machine_recipe',
        machine: 'mmcr_kubejs:kubejs_thermal_smelting_furnace',
        tick_time: 300,
        level_requirements: [
            {
                type: 'mmcr_kubejs:thermal_smelting_coil', // the type register name
                level: 'mmcr_kubejs:thermal_smelting_coil_diamond' // the level name
            }
        ],
        // magic uhh
        requirements: [
            {
                type: 'item',
                io: 'input',
                item: 'minecraft:coal',
                count: 1
            },
            {
                type: 'item',
                io: 'input',
                item: 'minecraft:raw_copper',
                count: 8
            },
            {
                type: 'item',
                io: 'output',
                stack: {
                    id: 'minecraft:copper_ingot',
                    count: 9
                }
            },
            {
                type: 'energy',
                io: 'input',
                fe_per_tick: 10
            }
        ]
    })

})