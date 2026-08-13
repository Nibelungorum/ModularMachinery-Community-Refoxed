智能接口的UI 上一个和下一个

- [X] KubeJS tag+data组合拳 以及游戏内注册测试tag和tag带data (估计又是一天时间过去了)

- [ ] kubejs集成的测试

- [X] 共享多方块结构 和已经输入输出锁解决异步和线程问题

.worktrees/shared-multiblock-io

已经OK了

- [X] 等级系统(实现线圈方块等级能力)

.worktrees/machine-level-system

这个确实已经搞完了

- [X] 配方输入带数据的物品 输出带数据的物品 DataCompoment处理(?)(今天就搞) 不消耗和概率消耗配方

.worktrees/data-component-recipe-inputs

基本功能应该没有问题X

还有概率输出没做，我真服了

测试样例
- [X] 后期优化一下这个东西的性能问题 token还够没想到吧

- [X] 红石停机的上下文持久化，以及对应的正确UI渲染

- [ ] 配方锁定(今天就搞)

包括单独的特定线程配方锁定和无线程仓的配方锁定

我想问你我这UI资源从哪来T_T

- [ ] 自动输入自动输出功能(使用原版的控件，嗯对，参考MEK)

- [ ] 声音事件

实现完成并已通过最终 review。
- Worktree：.worktrees/machine-sounds
- 分支：feat/machine-sounds
- 基于：dev/neo/26.1.2
- 提交范围：2be3758..fc88c5a，共 9 个实现/修复/测试提交
- 验证：focused suite 最终 BUILD SUCCESSFUL in 8s；此前 compileJava 也已通过 BUILD SUCCESSFUL in 10s
- Review：final whole-branch re-review 结论 Ready，无 Critical/Important findings
- 遗留：未执行手动集成客户端验收，因为当前会话没有获批替代客户端启动流程，且项目禁止 ./gradlew runClient --no-daemon
Implementation complete. What would you like to do?
1. Merge back to dev/neo/26.1.2 locally
2. Push and create a Pull Request
3. Keep the branch as-is
4. Discard this work

- [ ] 接口集成(AE2 总成型接口[新UI?] MEK气体接口(涉及配方modify) 输入输出总成)

- [ ] 额外解锁槽(发电阵列，生物屠宰场类似)系统，这样UI又要改了，干吧跌。
把这个单独扔到一个方块里面吧，嗯对

- [ ] 插件型机器 参考太空电梯

- [ ] 可扩展型机器 参考蒸馏塔

- [ ] 异步(这个可能要后面再说了)，大范围异步的线程内资源共享也是开销的一部分

- [ ] Modifer JEI集成(机器预览功能出了再搞)

- [ ] 机器的render和相关api暴露(最后一个再做这个)

- [ ] 重构其它地方



- [X] 多线程 Stage 5已经实现了

- [X] 工厂调度器与线程分散器：独立线程仓、结构聚合、注册 API、菜单和资源

.worktrees/phase5-mmce-optimizations

多线程UI的滑块贴图还没有做 干吧跌

- [X] 并行仓测试，默认机器加入并行仓进行测试

- [X] 更多的接口 

- [X] 动态机器注册，先注册机器(start_up)，再注册机器结构和配方(server)。

全新的动态机器和配方的注册机制，可以同步拿到KubeJS集成，一劳永逸地解决控制器方块注册问题，涉及大改动，但是必须进行
首先，将机器注册，结构注册，配方注册拆分开，机器注册是单独的，因为涉及到控制器方块添加，这一类注册必须在类似方块注册的阶段进行，同时，这一阶段也只能在KubeJS的start_up阶段进行，这样机器是否存在与控制器方块的注册直接共存亡，就解决了reload阶段可能出现的丢注册情况

机器存在之后默认为其注册一种配方类型，但是不为其注册实际的配方，仅存在该种类型。同时这种start_up阶段的注册也限定了机器是垂直型还是水平型，是否具有Modifier等等性质，又解决了一种隐患

机器结构注册和配方，modifier方块是哪些，有什么用，以及未来可能的结构扩展，都在server_script阶段，首先配方本身就是可以动态加载的，结构检测等等自然也是可以动态加载的，这样对reload的负担无疑是最小的，而reload实现也是最不容易出错的。

因此除了要修改优化KubeJS相关集成，我们自己的默认机器和默认配方代码也可以相应优化一下了。

写一份implement plan，期间参考reference/mmce和reference/gtceu，给出最好解决和优化方案。

- [X] 基于basic_casing的动态模型和贴图加载(控制器和端口分开，以及一个api同时设置)，网络包通信客户端渲染，客户端持久化和玩家login事件。

block model动态化，统一化，overlay渲染，一劳永逸解决模型贴图问题，同时解决model gen问题

大改当前的方块贴图相关部分，因为我们已经实现了更现代的注册功能，所以当前控制器的表现可以扩展一下了

首先，在有KubeJS集成的情况下，我们期望控制器方块动态注册，因此它不能和Model Data Gen深度耦合，我们期望控制器方块是共享同一种固定方块模型，包括垂直型控制器和水平型控制器，然后具体贴图是动态的。

一个设计要点是：无论控制器方块还是接口方块，其本质是一个cube_all方块上面加了overlay，其中控制器方块只在一个面上加overlay，接口方块是六个面都有overlay

因此我们需要：当注册机器时(也就是start_up阶段)，就指定一种该机器的basic方块(称为machine_basic_block)，该机器控制器贴图默认使用machine_basic_block的贴图作为底图+overlay的格式，也可以特别指定控制器使用某Identifier作为底图。接口方块因为是提前注册的，因此接口类方块都使用已有的mmcr:basic_casing方块作为底图。

当机器成型时，默认进行服务端客户端同步，接口方块的底图自动更新为machine_basic_block的底图，实现完美的CTM体验，当然这个成型时自动更新的底图也可以自己指定Identifier。

考虑到server_script具有重载性，machine_basic_block的指定，特别指定控制器使用某Identifier作为底图，成型时自动更新的接口底图都必须在注册机器时声明，同时它是一个optional项目，默认全使用mmcr:basic_casing方块作为底图。

为了防止重新登录等等时候出现更新不及时，最好在可预料的时候都进行一次提前的服务端客户端同步来保证体验

写一份implement plan，期间参考reference/mmce和reference/gtceu，给出最好解决和优化方案。

.worktrees/dynamic-machine-texture-models
