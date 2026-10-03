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
            {
                type: 'create:stress',
                io: 'output',
                stress: 16,
                rpm: 32
            }
        ]
    })
})
