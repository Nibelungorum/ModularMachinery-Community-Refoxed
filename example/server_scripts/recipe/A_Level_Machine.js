ServerEvents.recipes( event => {
    event.custom({
        type: 'mmcr:machine_recipe',
        recipe_pool: 'mmcr_kubejs:kubejs_thermal_smelting_furnace',
        tick_time: 300,
        parallelized: true,
        requirements: [
            {
                type: 'mmcr:level',
                io: 'input',
                level_type: 'mmcr_kubejs:thermal_smelting_coil',
                level: 'mmcr_kubejs:thermal_smelting_coil_iron'
            },
            {
                type: 'minecraft:item',
                io: 'input',
                item: { item: 'minecraft:coal' },
                count: 10000
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
        recipe_pool: 'mmcr_kubejs:kubejs_thermal_smelting_furnace',
        tick_time: 300,
        parallelized: true,
        requirements: [
            {
                type: 'mmcr:level',
                io: 'input',
                level_type: 'mmcr_kubejs:thermal_smelting_coil',
                level: 'mmcr_kubejs:thermal_smelting_coil_gold'
            },
            {
                type: 'minecraft:item',
                io: 'input',
                item: { item: 'minecraft:coal' },
                count: 1
            },
            {
                type: 'minecraft:item',
                io: 'input',
                item: { item: 'minecraft:raw_gold' },
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
        recipe_pool: 'mmcr_kubejs:kubejs_thermal_smelting_furnace',
        tick_time: 300,
        parallelized: true,
        max_threads: 4,
        requirements: [
            {
                type: 'mmcr:level',
                io: 'input',
                level_type: 'mmcr_kubejs:thermal_smelting_coil',
                level: 'mmcr_kubejs:thermal_smelting_coil_diamond'
            },
            {
                type: 'minecraft:item',
                io: 'input',
                item: { item: 'minecraft:coal' },
                count: 1
            },
            {
                type: 'minecraft:item',
                io: 'input',
                item: { item: 'minecraft:raw_copper' },
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
