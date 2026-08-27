# 控制器界面文本扩展设计

## 状态

已确认设计，尚未进入实现。

## 背景

项目有普通控制器界面和工厂控制器界面。两者都需要展示标准运行状态，并且未来的 recipe 生命周期函数和纯自定义 tick 机器都可能向控制器界面追加运行时文本。

当前界面文本主要由两个 Screen 根据各自的 Menu 状态直接拼装。两种界面已经共用 `AbstractScrollableTextScreen` 的滚动基础，但外部扩展没有统一入口。

现有的并行仓数量、并行数、线程数、进度等状态已经有完整的运行时快照和同步机制。这些内容不应被重新建模为 KubeJS/Public API 的通用参数，也不应与物品 tooltip 混用。

## 目标

- 普通控制器和工厂控制器使用同一套外部文本追加协议。
- `startup` 和 `server_script` 可以注册文本处理逻辑。
- recipe 前置、运行中、结束前函数和纯自定义 tick 都能更新控制器界面文本。
- 文本状态按控制器实例维护，并在运行时变化后同步给客户端。
- 客户端只负责文本组件的排版、滚动、分页和绘制。
- 文本扩展不接触 Screen 坐标、缩放、字体和滚动条。
- 同一行重复更新不会造成重复显示。

## 非目标

- 不恢复旧版客户端直接执行脚本回调的 `ControllerGUIRenderEvent` 模式。
- 不把 MMCR 内置的并行、线程和进度字段暴露给外部 API。
- 不允许外部扩展修改标准行的顺序、位置或样式区域。
- 不在初版实现客户端逐行 patch 协议。
- 不把控制器界面文本默认写入方块实体存档。

## 核心模型

### 标准文本

标准文本继续使用现有的 `MachineStateSnapshot`、`FactorySnapshot` 和 Menu 状态。实现时将标准行通过内部文本收集器统一加入，但这些内部贡献者不注册到 Public API 或 KubeJS 可见的参数表。

标准行始终先于外部文本。普通控制器和工厂控制器可以继续使用各自不同的标准行和 viewport，但最终都进入同一个逻辑文本模型。

### 控制器实例文本状态

每个控制器实例持有一个服务端 `ControllerScreenTextState`。状态包含有序的外部文本行，行使用扩展方命名空间和稳定 `lineId` 标识。

运行时 API 使用 upsert 语义：

- 第一次以 `lineId` 设置文本时，将该行追加到外部文本末尾。
- 再次设置相同 `lineId` 时，只更新原行，不改变位置。
- `remove(lineId)` 删除该行。
- 不同扩展必须使用不同命名空间，不能覆盖 MMCR 保留 ID。

文本状态提供两个作用域：

- `controller`：默认作用域，适合纯自定义 tick 的长期状态，直到控制器解绑或运行时重置时清理。
- `operation`：适合一次 recipe 或运行周期的状态，在正常结束、失败或取消时自动清理。

文本 API 的底层输入是已构造的 `Component`。提供 `appendTranslatable(key, args...)` 作为便捷方法；格式化参数由服务端回调构造，不需要把 MMCR 内部字段暴露给外部。

## API 表面

实现应提供一个服务端注册入口和一个控制器实例级文本句柄。名称以 `ControllerScreenText` 为前缀，避免与物品 tooltip API 混淆。

Public API 的概念接口包括：

- 按机器 ID 注册文本处理器。
- 处理器接收 `ControllerRuntimeContext`。
- 通过 `context.screenText().append(scope, lineId, component)` 添加或更新行。
- 通过 `context.screenText().remove(scope, lineId)` 删除行。
- 提供 `appendTranslatable(scope, lineId, key, args...)` 便捷方法。

KubeJS 的 `startup` 和 `server_script` 入口暴露同等能力。KubeJS 处理器仍在服务端脚本环境执行，接收当前控制器运行上下文，并调用同一个 `screenText` 句柄。KubeJS API 的具体包装名称可以遵循现有 `MMCREvents` 和 `MMCRServerEventJS` 的命名风格，但不能让脚本直接操作网络 payload 或客户端 Screen。

`append` 的 `lineId` 是扩展方稳定标识，不是显示文本。相同作用域和 ID 更新原行，首次出现时按注册顺序追加。scope 和 ID 共同构成行的唯一键；扩展方不得使用 MMCR 保留命名空间。

## 运行时上下文

文本句柄挂在通用的 `ControllerRuntimeContext` 上，而不是 recipe 上下文。该上下文至少提供：

- 控制器实例身份和位置。
- 机器身份。
- 控制器级 `screenText` 句柄。

未来的 recipe 前置、运行中、结束前函数和纯自定义 tick 都使用同一个上下文。纯自定义 tick 不依赖 `RecipeThread` 或 `CraftingRuntime`。

Public API 注册的服务端处理器和 KubeJS 在 `startup`/`server_script` 注册的处理器，都在服务端执行。处理器可以读取自己的运行时数据，构造任意自己的 `Component`，再通过控制器文本句柄设置、更新或删除行。

服务端 KubeJS 函数本身不序列化到网络。扩展方的自定义字段由扩展方在服务端维护和计算；MMCR 只同步最终文本组件。若未来需要让多个处理器共享自定义字段，应使用扩展方命名空间的可序列化运行时数据，不复用 MMCR 内置字段。

## 注册与生命周期

Public API 和 KubeJS 共用一个服务端 `ControllerScreenTextRegistry`。注册项按机器 ID 过滤，并保留稳定的注册顺序。

注册项可以提供默认文本，也可以绑定到控制器运行时生命周期。处理器的输出写入控制器实例的文本状态，而不是直接写入全局机器定义。

服务端脚本重载时：

1. 清理失效的外部注册项。
2. 重建受影响控制器的外部文本状态。
3. 对当前有观察者的控制器发送新的快照。

控制器运行状态变化时，文本状态只在内容实际变化后标记 dirty。一个服务端 tick 内的多次更新在发送前合并。

## 同步协议

新增统一的控制器文本 payload，包含：

- `controllerPos`。
- 控制器实例的文本 `revision`。
- 按服务端顺序排列的外部文本行。
- 每行的稳定 `lineId` 和 `Component`。

该 payload 只同步外部文本，不重复同步普通控制器和工厂控制器已有的标准状态字段。标准字段仍由现有 `PktMachineStatePayload` 和 `PktFactoryControllerStatePayload` 传输，客户端标准文本贡献者从 Menu 状态生成标准行。revision 变化时发送完整快照；没有变化时不发送。完整替换可以自然处理新增、更新、删除和脚本重载，比逐行 patch 更容易保证顺序和失效语义。

发送时机：

- 控制器界面打开时发送当前快照。
- 文本状态在服务端 tick 或生命周期回调中变化时，向当前打开该控制器界面的玩家发送。
- 控制器没有观察者时保留当前运行时状态，下一次打开界面时发送。

客户端按控制器位置和菜单会话缓存最新快照，收到旧 revision 时丢弃。缺少外部快照时仍正常显示标准文本。

payload 应限制行数、ID 长度、文本组件大小和总大小。限制沿用项目现有网络 payload 的校验风格，具体常量在实现阶段确定。

## 客户端排版

客户端文本组合顺序固定为：

1. Screen 对应的内部标准行。
2. 该控制器实例同步过来的外部行。

两种 Screen 使用同一个文本追加/逻辑行模型。外部 API 只能提交逻辑文本行；客户端根据当前字体、缩放和 viewport 进行换行，再按照视觉行计算滚动和分页。服务端不预先换行。

文本构建结果应在一次 UI 状态刷新中缓存，避免 `scrollableTextLineCount()`、绘制和滚动计算重复触发有副作用的服务端逻辑。文本变化后重新构建并对滚动偏移做统一 clamp。

## 错误处理

- 单个 Public API 或 KubeJS 处理器抛出异常时记录日志并跳过该处理器。
- 处理器错误不能中断机器 tick、recipe 执行或标准状态同步。
- 无效的 line ID、空值、过长文本和超限快照在服务端拒绝。
- 客户端 payload 解码失败不能破坏控制器 Screen 的标准文本显示。
- 服务器脚本重载造成的旧文本必须通过完整快照清理。

## 验证计划

### 单元测试

- line ID 首次追加、重复更新、删除和顺序保持。
- controller/operation 作用域的生命周期清理。
- 无变化时 revision 不变，有变化时 revision 更新。
- 多个处理器的稳定顺序和单个处理器异常隔离。
- `Component`、格式化参数和 payload 的往返编解码。
- 客户端缓存对旧 revision、替换快照和缺少快照的处理。

### 网络和运行时测试

- 自定义 tick 连续更新同一行不产生重复行。
- recipe 开始、运行、结束和取消正确维护 operation 文本。
- 文本变化只同步给相关控制器的观察者。
- 脚本重载后活动控制器不会保留失效文本。

### Screen 测试

- 普通和工厂 Screen 使用相同外部文本快照。
- 外部文本始终位于标准文本之后。
- 长文本在两个不同 viewport 下都按客户端宽度换行。
- 外部文本溢出时沿用现有滚动和分页行为。

实现 Java/网络代码后，按项目要求串行运行 `./gradlew test --no-daemon` 和 `./gradlew runGameTestServer --no-daemon`。

## 兼容性

现有 `ControllerSpec.tooltip`、KubeJS `controllerTooltip` 和 `InterfaceTooltips` 的物品提示行为保持不变。控制器 Screen 文本使用独立 API、独立状态和独立 payload，不复用 tooltip 字段。
