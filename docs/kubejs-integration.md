# KubeJS 集成

## 机器等级

机器等级分为等级类型和具体等级。它们必须在 `startup_scripts` 中注册；启动注册阶段结束后，`MMCR.levelTypes.create` 和 `MMCR.levels.create` 会被拒绝。结构和配方则在 `server_scripts` 中声明，并会随服务器资源重载重新加载。

```js
// kubejs/startup_scripts/levels.js
MMCR.levelTypes.create("mmcr:heating_coil").displayName("加热线圈")

MMCR.levels.create("mmcr:kanthal")
  .type("mmcr:heating_coil")
  .priority(2)
  .state("mmcr:kanthal_coil")
  .modifier({
    durationMultiplier: 0.8,
    energyMultiplier: 0.9,
    outputMultiplier: 1.0,
    parallelismBonus: 1,
    factoryThreadBonus: 0
  })
```

`MMCR.levelTypes.create(id)` 返回类型构建器，`.displayName(name)` 设置显示名称。`MMCR.levels.create(id)` 返回等级构建器：

- `.type(typeId)` 关联一个已注册的等级类型。
- `.priority(number)` 设置该类型中的等级顺序；同一类型的 priority 不能重复。
- `.state(blockId | BlockState)` 设置对应的精确方块状态；一个状态不能重复用于不同等级。
- `.modifier(object)` 可设置 `durationMultiplier`、`energyMultiplier`、`outputMultiplier`、`parallelismBonus` 和 `factoryThreadBonus`。未填写的字段使用默认值：三个 multiplier 为 `1.0`，两个 bonus 为 `0`；三个 multiplier 必须大于零。

## 在结构中使用等级槽位

`MMCR.levelSlot(typeId)` 仅可引用已注册的等级类型。将其放入 `pattern()` 键表即可声明该位置接受该类型的任意已注册等级。

```js
// kubejs/server_scripts/machines.js
event.create("mmcr:blast_furnace").pattern("CXC", {
  C: MMCR.levelSlot("mmcr:heating_coil"),
  X: "mmcr:blast_furnace_controller"
})
```

同一类型可以出现在多个等级槽位中，但所有这些槽位必须放置**同一个具体等级**。例如一个槽位为 Kanthal、另一个为更高等级的线圈时，结构形成失败，而不会自动选择较高等级。不同类型可各自解析为不同等级。等级槽位只参与服务器端的结构匹配；当前没有结构预览渲染，预览不会展示或着色等级槽位。

## 配方等级要求

在机器配方上使用 `.requiresLevel(typeId, levelId)`：`levelId` 必须存在，且必须属于 `typeId`。

```js
// kubejs/server_scripts/recipes.js
MMCR.recipes.create("example:hot_recipe")
  .machine("mmcr:blast_furnace")
  .requiresLevel("mmcr:heating_coil", "mmcr:kanthal")
  .build()
```

要求按 priority 比较，而不是按 ID 完全相等：只要机器实际形成的该类型等级满足 `actual.priority >= required.priority`，即可运行配方。缺少该类型的等级，或实际 priority 较低，都会使该配方不可运行。

配方搜索优先检查最高 priority 的候选配方。在输入兼容的候选配方中，该配方即使因等级不足而不能开始，也会阻断较低 priority 配方，避免机器在相同输入下静默降级到低等级配方。

## 等级 modifier 的运行顺序

同一机器上不同类型的等级按类型 ID 的稳定顺序合并：`durationMultiplier`、`energyMultiplier` 和 `outputMultiplier` 相乘，`parallelismBonus` 与 `factoryThreadBonus` 相加。

等级效果先应用，普通配方 modifier 和结构 modifier 随后应用。持续时间、能耗和等级调整后的物品/流体产出向下取整。正数产出在等级 multiplier 后至少保留一个单位，例如 `1 * 0.5` 的最终等级产出为 `1`，不会变为零；原本为零的产出仍为零。
## 配方物品输入与输出

在 `ServerEvents.recipes` 回调中使用 `new MMCR_RECIPE_BUILDER('namespace:recipe_id')` 创建 `MachineRecipeBuilderJS`。`.build()` 也必须在该回调中调用，这样带组件的输出才能使用 KubeJS 的服务器 registry context 解码。`MachineRecipeBuilderJS` 的物品输入方法会创建 `type: "item"` 的机器配方输入。普通物品、标签、组件条件和消耗概率分别使用下列方法：

```js
new MMCR_RECIPE_BUILDER('example:blazing_tool')
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
new MMCR_RECIPE_BUILDER('example:sharp_sword_upgrade')
    .machine('mmcr:alloy_furnace')
    .itemInputWithComponents('minecraft:diamond_sword', 1, {
        'minecraft:enchantments': {
            type: 'map',
            values: { levels: { type: 'map', values: { 'minecraft:sharpness': { type: 'range', min: 3, max: 3 } } } }
        }
    })
    .itemOutputWithComponents('minecraft:diamond_sword', 1, {
        'minecraft:custom_name': { text: 'Better钻石剑' },
        'minecraft:enchantments': { 'minecraft:sharpness': 4 }
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
