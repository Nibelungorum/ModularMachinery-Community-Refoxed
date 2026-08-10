# 配方 API 映射

## 物品输入 JSON

物品输入使用 `type: "item"`，其中 `item` 是 Minecraft `Ingredient`，因此可以是物品或标签条件；`count` 是所需数量。`components` 和 `consume_chance` 都是可选字段，省略时分别表示不限制 Data Components 和必定消耗。

```json
{
  "type": "item",
  "item": { "tag": "c:tools" },
  "count": 1,
  "components": {
    "minecraft:custom_name": {
      "type": "text",
      "value": { "text": "烈焰之剑" },
      "mode": "plain"
    },
    "minecraft:enchantments": {
      "type": "map",
      "values": {
        "levels": {
          "type": "map",
          "values": {
            "minecraft:sharpness": {
              "type": "range",
              "min": 3,
              "max": 255
            }
          }
        }
      }
    }
  },
  "consume_chance": 0.35
}
```

组件谓词可使用以下形式：

| `type` | 字段 | 语义 |
| --- | --- | --- |
| `exact` | `value` | 要求编码后的组件值完全相等。 |
| `map` | `values` | 仅要求列出的键匹配；候选对象可以有额外键。 |
| `list` | `values` | 仅要求列出的条目匹配；候选列表可以有额外条目。每个候选条目在一次匹配中最多满足一个请求条目。 |
| `range` | `min`、`max` | 要求数值处于闭区间内。 |
| `text` | `value`、`mode` | `plain` 比较显示文本，`full` 比较完整文本组件。 |

`components` 的键是已注册的 Data Component ID。每个列出的组件都必须存在于候选 `ItemStack` 并满足其谓词。组件条件只筛选输入，不会把组件写入实际消耗的物品。

## 物品输出

机器配方的 `outputs` 字段使用完整的 `ItemStack` 编码，而不是只保存物品 ID 与数量。因此输出中提供的 Data Components 会随 `ItemStack` 一起解码、保存和产出。例如，以下输出会生成带自定义名称和锋利 IV 附魔的钻石剑：

```json
{
  "id": "minecraft:diamond_sword",
  "count": 1,
  "components": {
    "minecraft:custom_name": { "text": "Better钻石剑" },
    "minecraft:enchantments": { "levels": { "minecraft:sharpness": 4 } }
  }
}
```

`outputs` 由 `ItemStack.CODEC` 解码，并产生声明的精确组件值。它可以与要求普通锋利 III 钻石剑的输入谓词配对；输出组件是值，绝不是输入谓词中的 `range` 或 `text` 模式。JEI 展示和输出插入都会保留生成物品的原生 tooltip 及组件数据。

## KubeJS 对应关系

| JSON 概念 | KubeJS 方法 |
| --- | --- |
| `item` 为物品、`count` | `itemInput(itemId, count)` |
| `item` 为标签、`count` | `tagInput(tagId, count)` |
| `item`、`count`、`components` | `itemInputWithComponents(itemId, count, components)` |
| `consume_chance: 0` | `notConsumableItemInput(itemId, count)` |
| `consume_chance` | `chancedItemInput(itemId, count, consumeChance)` |
| 完整 `ItemStack` 输出及 `components` | `itemOutputWithComponents(itemId, count, components)` |

KubeJS 的 `itemOutput(itemId, count)` 创建普通 `ItemStack` 输出；`itemOutputWithComponents(itemId, count, components)` 使用 registry-aware JSON ops 将原生组件值解码为完整 `ItemStack`，语义与配方 JSON 的 `outputs` 字段一致。
