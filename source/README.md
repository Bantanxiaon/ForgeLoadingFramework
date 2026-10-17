# 0.4.0 开发与维护

本项目在既有代码上升级；内部 shanghan 包名、modId、服务名保留，已有实例可迁移。同一套双 Jar 负责已知 Minecraft 1.20.1–26.3 Forge 配对。版本范围和安装方法见根目录 README / INSTALL。

## 模块

- boot：ImmediateWindowProvider、早期发现服务、MediaSession、音频时钟、主题、总进度和任务观察。
- router/java：纯 Java 版本表、接口探测、统一服务门面、后端 GLFW 入口。
- adapters/modern：SDL 入口、Windows 子窗口渐隐。依赖单独的 LWJGL 3.4.3 SDK。
- client：普通客户端 Mod。
- adapters/shared-client：旧版桥接选择与本组件 LoadingOverlay 子类生成。
- author：可选安装、转码、备份与还原工具。
- tests：路由、元数据、媒体、资源交接和图形回归。
- examples/loading：外部配置模板；third_party：随附依赖许可。
- release.json：版本、元数据范围、构建 SDK 目标。buildProfiles 不是运行时支持白名单。

共享代码以 Java 17 和旧 LWJGL 3.3.1 编译，SDL 类独立使用 3.4.3 SDK 编译，避免新重载导致旧版本 NoSuchMethodError。运行游戏仍须使用 Minecraft 对应的 Java 17/21/25。

## 构建

需要 Python 3.11 或更高、JDK 17 和启动器正常安装的 Forge/Minecraft libraries。仅编译本项目，不重新编译 Forge。

    python -B source/build.py --forge 47.4.16 --jdk "<JDK17>" --libraries "<libraries>"

先运行 python -B source/prepare_sdk.py，下载并校验固定版本的四个官方编译/组件检查依赖；--verify-only 只检查。SDL 编译需要 source/adapters/modern/dependencies 下的 lwjgl-3.4.3.jar、lwjgl-opengl-3.4.3.jar、lwjgl-sdl-3.4.3.jar。请从 LWJGL 官方 Maven 工件获取；源码分发包不附带这些二进制。--forge-libraries 可指定精确 Forge 核心 SDK，排除其他核心版本。产物位于 dist。

    python -B source/build_author.py --jdk "<JDK17>" --libraries "<libraries>" --ffmpeg-dir "<FFmpeg目录>" --portable

便携版由 jpackage 生成独立 Java 运行时，与开发环境和当前会话无关。分发 FFmpeg 必须满足实际构建的许可证及对应源码要求；不能把附带许可证文本视为全部分发义务已经满足。

## 版本路由

RuntimeRouter 将精确 Minecraft 发行版与 Forge 主版本配对，补丁版本先走相同窗口族，再核对真实 SPI。未知版本不加载具体图形实现。RoutingWindowProvider 保持稳定服务标识，支持三种 setupMinecraftWindow 签名以及后端选择；版本可从启动参数或官方 FMLLoader.versionInfo 获得。

客户端先探测完整旧接口；不匹配时生成继承当前 LoadingOverlay 的本组件子类，保留原 super 调用和资源完成回调。较新 GUI 提取线程不执行 OpenGL；有界游戏线程监视负责交接清理。不是修改游戏类，也不替换 Forge 本体。

读取 Mod 元数据必须等待 updateModuleReads 发布游戏层，不能提前触发新版 LoadingModList 的延迟静态 holder。捕获初始化异常不能修复已经污染的 holder。

## 配置与媒体

素材优先位于实例 loading，兼容旧 config/shanghan_loading。使用相对路径，不绑定开发目录。title 留空自动读取实例名。默认 width/height/fps 为 640/360/30；队列有界，重复帧复用纹理；正常播放持续更新。声音使用独立 PCM 输出，加载结束同步渐隐。

任务显示可观察的当前 Mod；overallPercent 与条长均使用总进度，不按阶段重新计数。插件 API 位于 boot 的 ProgressProvider / LoadingApi。

## 验证

    python -B source/test.py --forge 47.4.16 --jdk "<JDK17>" --libraries "<libraries>" --ffmpeg "<ffmpeg.exe>" --video "<test.mp4>" --modern-sdk-check --modern-jdk "<JDK25>"

--non-gl 跳过图形检查。--modern-native 显式运行测试自己的 SDL 窗口；不同 SDK 编译和运行类路径保持隔离。实际 PCL 测试只需动画正常显示，再进入主菜单；纯净版不验证整合包 Mod 组合。结果不能只凭“启动成功”提示或只有解码日志判断。

Windows 视频显示层嵌入实际游戏窗口，父窗口始终不透明。完成后关闭媒体子进程、音频设备、纹理、显示层和监视任务。日志仅用于诊断，不要公开启动器账户或启动命令中的令牌。

源码包 source/tests/fixtures/ForgeLoadingFramework-0.3.3.jar 仅作为 ModMetadataSmoke 复现旧声明错误的负例，不用于安装游戏。实际 Forge66 接口检查及原生检查另需对应官方依赖；日常构建只需 prepare_sdk 获取的 SDK 和旧 Forge libraries。
