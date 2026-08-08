kubejs集成的测试

[X]更多的接口 

Modifer JEI集成

自动输入自动输出功能

接口集成

声音事件

- 多线程 Stage 5---- Minecraft Crash Report ----
// Would you like a cupcake?

Time: 2026-08-08 09:21:35
Description: Unexpected error

java.lang.IllegalArgumentException: Progress must be between 0 and 1, got: 12.207031
        at com.google.common.base.Preconditions.checkArgument(Preconditions.java:217) ~[guava-33.5.0-jre.jar:?] {}
        at TRANSFORMER/jade@26.1.3+neoforge/snownee.jade.api.view.ProgressView$Part.<init>(ProgressView.java:108) ~[jade-324717-8251883.jar:26.1.3+neoforge] {}
        at TRANSFORMER/jade@26.1.3+neoforge/snownee.jade.api.view.ProgressView$Part.<init>(ProgressView.java:112) ~[jade-324717-8251883.jar:26.1.3+neoforge] {}
        at TRANSFORMER/jade@26.1.3+neoforge/snownee.jade.api.view.ProgressView$Part.of(ProgressView.java:132) ~[jade-324717-8251883.jar:26.1.3+neoforge] {}
        at TRANSFORMER/jade@26.1.3+neoforge/snownee.jade.addon.universal.EnergyStorageProvider$Client.lambda$appendTooltip$0(EnergyStorageProvider.java:101) ~[jade-324717-8251883.jar:26.1.3+neoforge] {}
        at TRANSFORMER/jade@26.1.3+neoforge/snownee.jade.api.view.ClientViewGroup.tooltip(ClientViewGroup.java:59) ~[jade-324717-8251883.jar:26.1.3+neoforge] {}
        at TRANSFORMER/jade@26.1.3+neoforge/snownee.jade.addon.universal.EnergyStorageProvider$Client.appendTooltip(EnergyStorageProvider.java:73) ~[jade-324717-8251883.jar:26.1.3+neoforge] {}
        at TRANSFORMER/jade@26.1.3+neoforge/snownee.jade.impl.BlockAccessorClientHandler.gatherComponents(BlockAccessorClientHandler.java:98) ~[jade-324717-8251883.jar:26.1.3+neoforge] {}
        at TRANSFORMER/jade@26.1.3+neoforge/snownee.jade.impl.BlockAccessorClientHandler.gatherComponents(BlockAccessorClientHandler.java:26) ~[jade-324717-8251883.jar:26.1.3+neoforge] {}
        at TRANSFORMER/jade@26.1.3+neoforge/snownee.jade.overlay.WailaTickHandler.tickClient(WailaTickHandler.java:259) ~[jade-324717-8251883.jar:26.1.3+neoforge] {}
        at TRANSFORMER/jade@26.1.3+neoforge/snownee.jade.util.ClientProxy.onClientTick(ClientProxy.java:161) ~[jade-324717-8251883.jar:26.1.3+neoforge] {}
        at net.neoforged.bus.ConsumerEventHandler.invoke(ConsumerEventHandler.java:27) ~[bus-8.0.5.jar:?] {}
        at net.neoforged.bus.EventBus.post(EventBus.java:360) ~[bus-8.0.5.jar:?] {}
        at net.neoforged.bus.EventBus.post(EventBus.java:328) ~[bus-8.0.5.jar:?] {}
        at TRANSFORMER/neoforge@26.1.2.84/net.neoforged.neoforge.client.ClientHooks.fireClientTickPost(ClientHooks.java:912) ~[neoforge-26.1.2.84-universal.jar:?] {}
        at TRANSFORMER/minecraft@26.1.2/net.minecraft.client.Minecraft.tick(Minecraft.java:1981) [neoforge-26.1.2.84.jar:?] {neoforge:access_transformer,neoforge:access_transformer,neoforge:mixin[APP:kubejs.mixins.json:MinecraftClientMixin from mod kubejs]}
        at TRANSFORMER/minecraft@26.1.2/net.minecraft.client.Minecraft.runTick(Minecraft.java:1308) [neoforge-26.1.2.84.jar:?] {neoforge:access_transformer,neoforge:access_transformer,neoforge:mixin[APP:kubejs.mixins.json:MinecraftClientMixin from mod kubejs]}
        at TRANSFORMER/minecraft@26.1.2/net.minecraft.client.Minecraft.run(Minecraft.java:937) [neoforge-26.1.2.84.jar:?] {neoforge:access_transformer,neoforge:access_transformer,neoforge:mixin[APP:kubejs.mixins.json:MinecraftClientMixin from mod kubejs]}
        at TRANSFORMER/minecraft@26.1.2/net.minecraft.client.main.Main.main(Main.java:246) [neoforge-26.1.2.84.jar:?] {}
        at net.neoforged.fml.startup.Client.main(Client.java:19) [loader-11.0.15.jar:11.0] {}


A detailed walkthrough of the error, its code path and all known details is as follows:
---------------------------------------------------------------------------------------

-- Head --
Thread: Render thread
Stacktrace:
        at com.google.common.base.Preconditions.checkArgument(Preconditions.java:217) ~[guava-33.5.0-jre.jar:?] {}
        at TRANSFORMER/jade@26.1.3+neoforge/snownee.jade.api.view.ProgressView$Part.<init>(ProgressView.java:108) ~[jade-324717-8251883.jar:26.1.3+neoforge] {}
        at TRANSFORMER/jade@26.1.3+neoforge/snownee.jade.api.view.ProgressView$Part.<init>(ProgressView.java:112) ~[jade-324717-8251883.jar:26.1.3+neoforge] {}
        at TRANSFORMER/jade@26.1.3+neoforge/snownee.jade.api.view.ProgressView$Part.of(ProgressView.java:132) ~[jade-324717-8251883.jar:26.1.3+neoforge] {}
        at TRANSFORMER/jade@26.1.3+neoforge/snownee.jade.addon.universal.EnergyStorageProvider$Client.lambda$appendTooltip$0(EnergyStorageProvider.java:101) ~[jade-324717-8251883.jar:26.1.3+neoforge] {}
        at TRANSFORMER/jade@26.1.3+neoforge/snownee.jade.api.view.ClientViewGroup.tooltip(ClientViewGroup.java:59) ~[jade-324717-8251883.jar:26.1.3+neoforge] {}
        at TRANSFORMER/jade@26.1.3+neoforge/snownee.jade.addon.universal.EnergyStorageProvider$Client.appendTooltip(EnergyStorageProvider.java:73) ~[jade-324717-8251883.jar:26.1.3+neoforge] {}
        at TRANSFORMER/jade@26.1.3+neoforge/snownee.jade.impl.BlockAccessorClientHandler.gatherComponents(BlockAccessorClientHandler.java:98) ~[jade-324717-8251883.jar:26.1.3+neoforge] {}
        at TRANSFORMER/jade@26.1.3+neoforge/snownee.jade.impl.BlockAccessorClientHandler.gatherComponents(BlockAccessorClientHandler.java:26) ~[jade-324717-8251883.jar:26.1.3+neoforge] {}
        at TRANSFORMER/jade@26.1.3+neoforge/snownee.jade.overlay.WailaTickHandler.tickClient(WailaTickHandler.java:259) ~[jade-324717-8251883.jar:26.1.3+neoforge] {}
        at TRANSFORMER/jade@26.1.3+neoforge/snownee.jade.util.ClientProxy.onClientTick(ClientProxy.java:161) ~[jade-324717-8251883.jar:26.1.3+neoforge] {}
        at net.neoforged.bus.ConsumerEventHandler.invoke(ConsumerEventHandler.java:27) ~[bus-8.0.5.jar:?] {}
        at net.neoforged.bus.EventBus.post(EventBus.java:360) ~[bus-8.0.5.jar:?] {}
        at net.neoforged.bus.EventBus.post(EventBus.java:328) ~[bus-8.0.5.jar:?] {}
        at TRANSFORMER/neoforge@26.1.2.84/net.neoforged.neoforge.client.ClientHooks.fireClientTickPost(ClientHooks.java:912) ~[neoforge-26.1.2.84-universal.jar:?] {}
-- Uptime --
Details:
        JVM uptime: 24.371s
        Wall uptime: 16.423s
        High-res time: 23.603s
        Client ticks: 241 ticks / 12.050s
Stacktrace:
        at TRANSFORMER/minecraft@26.1.2/net.minecraft.client.Minecraft.fillReport(Minecraft.java:2472) [neoforge-26.1.2.84.jar:?] {neoforge:access_transformer,neoforge:access_transformer,neoforge:mixin[APP:kubejs.mixins.json:MinecraftClientMixin from mod kubejs]}
        at TRANSFORMER/minecraft@26.1.2/net.minecraft.client.Minecraft.emergencySaveAndCrash(Minecraft.java:992) [neoforge-26.1.2.84.jar:?] {neoforge:access_transformer,neoforge:access_transformer,neoforge:mixin[APP:kubejs.mixins.json:MinecraftClientMixin from mod kubejs]}
        at TRANSFORMER/minecraft@26.1.2/net.minecraft.client.Minecraft.run(Minecraft.java:960) [neoforge-26.1.2.84.jar:?] {neoforge:access_transformer,neoforge:access_transformer,neoforge:mixin[APP:kubejs.mixins.json:MinecraftClientMixin from mod kubejs]}
        at TRANSFORMER/minecraft@26.1.2/net.minecraft.client.main.Main.main(Main.java:246) [neoforge-26.1.2.84.jar:?] {}
        at net.neoforged.fml.startup.Client.main(Client.java:19) [loader-11.0.15.jar:11.0] {}已经实现了

额外解锁槽(发电阵列)

modifier等级功能(实现线圈方块等级能力)

- 动态机器注册，先注册机器(start_up)，再注册机器结构和配方(server)。

全新的动态机器和配方的注册机制，可以同步拿到KubeJS集成，一劳永逸地解决控制器方块注册问题，涉及大改动，但是必须进行
首先，将机器注册，结构注册，配方注册拆分开，机器注册是单独的，因为涉及到控制器方块添加，这一类注册必须在类似方块注册的阶段进行，同时，这一阶段也只能在KubeJS的start_up阶段进行，这样机器是否存在与控制器方块的注册直接共存亡，就解决了reload阶段可能出现的丢注册情况

机器存在之后默认为其注册一种配方类型，但是不为其注册实际的配方，仅存在该种类型。同时这种start_up阶段的注册也限定了机器是垂直型还是水平型，是否具有Modifier等等性质，又解决了一种隐患

机器结构注册和配方，modifier方块是哪些，有什么用，以及未来可能的结构扩展，都在server_script阶段，首先配方本身就是可以动态加载的，结构检测等等自然也是可以动态加载的，这样对reload的负担无疑是最小的，而reload实现也是最不容易出错的。

因此除了要修改优化KubeJS相关集成，我们自己的默认机器和默认配方代码也可以相应优化一下了。

写一份implement plan，期间参考reference/mmce和reference/gtceu，给出最好解决和优化方案。

- 基于basic_casing的动态模型和贴图加载(控制器和端口分开，以及一个api同时设置)，网络包通信客户端渲染，客户端持久化和玩家login事件。

- block model动态化，统一化，overlay渲染，一劳永逸解决模型贴图问题，同时解决model gen问题

大改当前的方块贴图相关部分，因为我们已经实现了更现代的注册功能，所以当前控制器的表现可以扩展一下了

首先，在有KubeJS集成的情况下，我们期望控制器方块动态注册，因此它不能和Model Data Gen深度耦合，我们期望控制器方块是共享同一种固定方块模型，包括垂直型控制器和水平型控制器，然后具体贴图是动态的。

一个设计要点是：无论控制器方块还是接口方块，其本质是一个cube_all方块上面加了overlay，其中控制器方块只在一个面上加overlay，接口方块是六个面都有overlay

因此我们需要：当注册机器时(也就是start_up阶段)，就指定一种该机器的basic方块(称为machine_basic_block)，该机器控制器贴图默认使用machine_basic_block的贴图作为底图+overlay的格式，也可以特别指定控制器使用某Identifier作为底图。接口方块因为是提前注册的，因此接口类方块都使用已有的mmcr:basic_casing方块作为底图。

当机器成型时，默认进行服务端客户端同步，接口方块的底图自动更新为machine_basic_block的底图，实现完美的CTM体验，当然这个成型时自动更新的底图也可以自己指定Identifier。

考虑到server_script具有重载性，machine_basic_block的指定，特别指定控制器使用某Identifier作为底图，成型时自动更新的接口底图都必须在注册机器时声明，同时它是一个optional项目，默认全使用mmcr:basic_casing方块作为底图。

为了防止重新登录等等时候出现更新不及时，最好在可预料的时候都进行一次提前的服务端客户端同步来保证体验

写一份implement plan，期间参考reference/mmce和reference/gtceu，给出最好解决和优化方案。

机器的render和相关api暴露