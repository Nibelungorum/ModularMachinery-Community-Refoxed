ServerEvents.recipes(event => {
    event.custom({
        type: 'mmcr:machine_recipe',
        recipe_pool: 'mmcr_kubejs:larger',
        tick_time: 200,
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
                stack: {
                    id: 'pneumaticcraft:ingot_iron_compressed',
                    count: 1
                }
            },
            // 每个成功推进的 tick 消耗 100 mL 空气，输入接口需保持至少 3 bar。
            // min_pressure 是单个接口的压力门槛，多个接口的压力不会相加。
            {
                type: 'pneumaticcraft:air',
                io: 'input',
                air_per_tick: 100,
                min_pressure: 3
            },
            // 每个成功推进的 tick 向输出接口产生 25 mL 空气。
            // 输出不支持 min_pressure；需要在结构中放置气压输出接口。
            {
                type: 'pneumaticcraft:air',
                io: 'output',
                air_per_tick: 25
            }
        ]
    }).id('mmcr_kubejs:larger_compressed_iron')
})
