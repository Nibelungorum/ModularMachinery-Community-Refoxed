# KubeJS 集成

`MachineRecipeBuilderJS` 的物品输入方法会创建 `type: "item"` 的机器配方输入。普通物品、标签、组件条件和消耗概率分别使用下列方法：

```js
event.recipes.mmcr.machineRecipe('example:blazing_tool')
    .machine('mmcr:alloy_furnace')
    .itemInput('minecraft:iron_ingot', 2)
    .tagInput('c:tools', 1)
    .itemInputWithComponents('minecraft:diamond_sword', 1, {
        'minecraft:custom_name': {
            type: 'text',
            value: { text: '烈焰之剑' },
            mode: 'plain'
        }
    })
    .notConsumableItemInput('minecraft:blaze_powder', 1)
    .chancedItemInput('minecraft:coal', 1, 0.35)
    .itemOutput('minecraft:blaze_rod', 1)
    .build()
```

输入组件和输出组件使用不同的 JSON 语法。输入对象是用于匹配已有物品的组件谓词 grammar，输出对象则是要写入生成物品的原生 `ItemStack` 精确组件值 map：

```js
event.recipes.mmcr.machineRecipe('example:sharp_sword_upgrade')
    .machine('mmcr:alloy_furnace')
    .itemInputWithComponents('minecraft:diamond_sword', 1, {
        'minecraft:enchantments': {
            type: 'map',
            values: { levels: { type: 'map', values: { 'minecraft:sharpness': { type: 'range', min: 3, max: 3 } } } }
        }
    })
    .itemOutputWithComponents('minecraft:diamond_sword', 1, {
        'minecraft:custom_name': { text: 'Better钻石剑' },
        'minecraft:enchantments': { levels: { 'minecraft:sharpness': 4 } }
    })
    .build()
```

可用方法如下：

| 方法 | 作用 |
| --- | --- |
| `itemInput(itemId, count)` | 添加完全消耗的物品输入。 |
| `tagInput(tagId, count)` | 添加完全消耗的物品标签输入。 |
| `itemInputWithComponents(itemId, count, components)` | 添加带 Data Component 条件的完全消耗物品输入。`components` 使用与 JSON 配方相同的组件谓词对象。 |
| `notConsumableItemInput(itemId, count)` | 添加不消耗的物品输入。 |
| `chancedItemInput(itemId, count, consumeChance)` | 添加按 `consumeChance` 消耗的物品输入。 |
| `itemOutput(itemId, count)` | 添加普通物品输出。 |
| `itemOutputWithComponents(itemId, count, components)` | 添加带原生 Data Component 值的物品输出。`components` 是精确的 `ItemStack` 组件值 map，不使用输入谓词的 `range` 或 `text` 模式。 |

`consumeChance` 的值会限制在 `0` 到 `1`。`notConsumableItemInput` 等价于消耗概率为 `0`，普通 `itemInput`、`tagInput` 和 `itemInputWithComponents` 的消耗概率为 `1`。

KubeJS 配方 schema 的 `inputs` 保留原始 JSON 元素，因此也可直接提供下文 JSON 配方格式的输入对象。
