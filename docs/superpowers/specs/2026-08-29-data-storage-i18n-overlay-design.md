# 数据存储器 i18n 与 Overlay 贴图

## 范围

为数据存储器补齐方块物品的中英文显示名，并让它使用已提供的
`assets/mmcr/textures/block/overlay_data_storage.png`。不新增测试，不改变数据存储器的功能、注册方式或模型架构。

## 设计

- 在 `en_us.json` 增加 `item.mmcr.data_storage`，值为 `Data Storage`。
- 在 `zh_cn.json` 将 `block.mmcr.data_storage` 改为“数据存储器”，并增加同值的 `item.mmcr.data_storage`。
- 在 `RuntimeMachineModelRegistry` 中将数据存储器的 overlay 标识改为
  `mmcr:block/overlay_data_storage`。
- 保留 `DynamicOverlayModelLoader`、`DynamicOverlayItemModel` 及现有 `PORT` 动态模型流程；世界方块和物品都通过该流程加载 base 与 overlay 材质。
- 不新增静态模型 JSON、专用 loader、配置项或测试。

## 验收

- 数据存储器方块和物品在英文语言下显示为 `Data Storage`。
- 数据存储器方块和物品在中文语言下显示为“数据存储器”。
- 数据存储器世界模型和物品模型引用 `overlay_data_storage`。
- 既有测试文件中的用户改动保持不变。
