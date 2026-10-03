ServerEvents.recipes(event => {
    event.custom({
        type: 'mmcr:machine_recipe',
        recipe_pool: 'mmcr_kubejs:big_presser',
        tick_time: 200,
        requirements: [
            {
                type: 'minecraft:item',
                io: 'input',
                item: { item: 'minecraft:apple' },
                count: 1
            },
            {
                type: 'minecraft:item',
                io: 'output',
                stack: {
                    id: 'minecraft:golden_apple',
                    count: 1
                }
            },
            // 运行期间持续占用输入应力，要求输入转速绝对值至少为 64 RPM。
            // stress 是基础负载：实际负载 = 8 × |输入 RPM|，64 RPM 时为 512 SU。
            {
                type: 'create:stress',
                io: 'input',
                stress: 8,
                min_rpm: 64
            },
            // 运行期间输出动力，实际容量 = 16 × |32| = 512 SU。
            // rpm 为生成转速，使用 -32 可以反转输出，不能为 0。
            // 我不建议你使用应力输出，因为 MMCR 的机器具有上下文准备阶段，虽然可以lastRecipe跳过配方搜索，但是始终有1-2tick的延迟
            // 或者你应该准备一个超长时间的每tick消耗配方
            {
                type: 'create:stress',
                io: 'output',
                stress: 16,
                rpm: 32
            }
        ]
    })
})
