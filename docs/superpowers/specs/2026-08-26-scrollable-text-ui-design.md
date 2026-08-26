# 纯文本 UI 逐行滚动设计

## 状态

设计已确认，待编写实现计划。

## 目标

为可变长度的纯文本 UI 增加垂直逐行滚动能力。界面先根据字体缩放、字体行高、行间距和预设的最大详情区域计算可视行数；内容超出区域后，鼠标位于该区域内滚轮每次移动一行。

## 适用范围

适用界面：

- `MachineControllerScreen` 的普通控制器右侧详情。
- `FactoryControllerScreen` 的工厂控制器右侧详情。
- `ExtendedItemScreen`、`ExtendedFluidScreen` 和 `ExtendedCombinedScreen` 的 `guicontroller_large.png` 文本库存页。

不纳入本次改动：

- `SmartInterfaceScreen`，当前内容是固定的少量文本，不是可变长度列表。
- `FactorySchedulerScreen`，当前只有固定标题文本。
- 菜单、网络同步、资源纹理和服务端逻辑。

工作区中已有的 `CombinedPortScreen` 未提交改动不属于本功能范围，不应被覆盖或回滚。

## 架构

新增包内抽象基类 `AbstractScrollableTextScreen<M>`，继承 `AbstractContainerScreen<M>`。界面层级调整为：

```text
AbstractScrollableTextScreen<M>
├── MachineControllerScreen
├── FactoryControllerScreen
└── AbstractPortScreen<M>
    ├── ExtendedItemScreen
    ├── ExtendedFluidScreen
    └── ExtendedCombinedScreen
```

公共基类只处理滚动相关的通用机制：

- 详情滚动偏移量及其范围限制。
- 文本视口的本地坐标、宽度、高度、字体缩放和行间距。
- 使用字体行高、字体缩放和行间距计算可视行数。
- 根据滚动偏移量计算可见行范围。
- 判断鼠标是否位于详情视口内。
- 在详情视口内按滚轮方向逐行移动。
- 内容更新后将偏移量限制在合法范围。

各子类继续负责文本行的业务内容、颜色、标题、背景和特殊 tooltip。公共层不负责拼接控制器状态、线程信息或库存数据，避免把不同界面的业务结构强行统一。

工厂控制器通过公共基类的额外滚动处理钩子继续维护左侧线程列表滚动。左侧线程列表和右侧详情使用各自的偏移量，互不影响。

## 布局与绘制

每个适用界面声明自己的详情视口配置：

```text
x、y、width、height
字体缩放比例
行间距
```

标题保持固定，不参与详情滚动。详情绘制流程如下：

1. 子类按照当前菜单数据生成完整的文本行列表。
2. 基类根据视口高度和行布局参数计算可视行数。
3. 基类提供当前可见行的起止索引。
4. 子类只绘制这个范围内的文本行。
5. 接口库存页面按照同一个可见行范围注册 tooltip，保证文本和 tooltip 对齐。

只处理垂直滚动。超长单行文本沿用现有绘制行为，不新增水平滚动、自动换行或额外滚动条。

详情内容没有溢出时，详情滚动偏移量保持为零，滚轮不会产生无效滚动。滚动范围为：

```text
0 .. max(0, totalLineCount - visibleLineCount)
```

鼠标不在详情视口内时，详情偏移量不改变。工厂控制器鼠标位于左侧线程区域时，仍执行现有线程列表滚动逻辑。

## 各界面内容保持

普通控制器保留现有状态、等级、失败原因、模块状态、并行信息、红石暂停和进度行的顺序及颜色。

工厂控制器保留固定的机器标题和选中线程详情；左侧线程列表继续显示线程状态、进度、选中和锁定覆盖层。

扩展接口保留标题、库存分区、库存行和现有 tooltip。Auto IO 页面切换逻辑不变，只有文本库存页使用详情滚动。

## 测试

增加或扩展客户端 GUI 单元测试，覆盖：

- 根据最大高度、字体缩放、字体行高和行间距计算可视行数。
- 空列表、未溢出列表和超出一行的列表的滚动范围。
- 滚动偏移量的上下限限制。
- 鼠标在详情区域内滚动时偏移量逐行变化。
- 鼠标在详情区域外滚动时详情偏移量不变化。
- 工厂控制器左右两套滚动状态互不干扰。
- 扩展接口文本顺序和可见行 tooltip 位置保持正确。

实现 Java 修改后，按项目要求串行执行：

```bash
./gradlew test --no-daemon
./gradlew runGameTestServer --no-daemon
```
