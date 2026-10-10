# Forge Loading Framework

Minecraft Forge 1.20.1 客户端启动视频加载组件。当前版本：0.3.0 Alpha。

在 Forge 早期加载和 Minecraft 资源加载过程中播放外部 H.264 MP4 与 AAC 音频，显示加载阶段及可观察到的当前 Mod 任务，完成后渐隐进入整合包原主菜单。

A client loading-video framework for Minecraft 1.20.1 Forge. Plays external H.264/AAC media, displays loading stages and observable active mod tasks, and fades into the existing main menu.

## Compatibility / 当前范围

- Windows、Java 17、Minecraft 1.20.1。
- 构建目标：Forge 47.3.22、47.3.33；完整最终版本在 47.3.22 上通过 PCL 启动。47.3.33 曾进行启动测试，之后的修订通过构建和回归检查。
- 视频显示层嵌入游戏窗口，软件解码，OpenGL 合成。
- 默认解码输出 640×360、30 FPS；实际性能取决于电脑和整合包负载，低配置设备需要实际测试。
- 其他系统及 Forge build 尚未验证。不修改 Forge 或 PCL 本体。

## Installation / 安装

两个运行时组件可从 downloads 目录下载：

- ForgeLoadingFramework-bootstrap-0.3.0.jar：早期窗口服务、媒体、主题和进度。
- ForgeLoadingFramework-0.3.0.jar：Forge 客户端 Mod、资源加载和主菜单过渡。

经过运行测试的方式：Bootstrap 作为标准 Maven library 注册，客户端 Jar 放入实例 mods。具体步骤见 [INSTALL.md](INSTALL.md)。

作者工具源码提供安装、转码和还原功能，安装前备份实例配置。此工具不属于游戏运行时的第三个 Mod。

两个 Jar 都放入 mods 的简化方式目前只完成机制审查，尚未完成实际导入和启动验证。

本仓库不附带第三方播放器、Java、Minecraft 库、整合包或视频素材。

## Configuration / 配置与素材

将 source/examples/loading 复制为实例中的 loading 文件夹，放入自己的 video/loading.mp4 和 bin/ffmpeg.exe。

默认播放视频中的音频；audioFile 可指定独立音频。标题留空时读取整合包元数据或实例名称。视频、字体和图片路径相对于 loading 文件夹；兼容旧 config/shanghan_loading 目录。

width、height、fps、volume、fadeOutMs 控制视频输出、音量和过渡。ui 配置控制标题、状态、任务条目、进度条和 Logo 的字体、位置及颜色。更换素材无需重新编译。

FFmpeg 是外部媒体依赖。请自行获取 Windows 构建并放到上述路径，或配置 ffmpeg 路径。分发它时须遵守实际构建及第三方组件的许可证。

## Build / 构建

需要 Python 3、JDK 17，以及由正常启动器安装的 Minecraft/Forge libraries。不重新编译 Forge。

    python -B source/build.py --forge 47.3.22 --jdk "<JDK17>" --libraries "<minecraft libraries>"
    python -B source/build.py --forge 47.3.33 --jdk "<JDK17>" --libraries "<minecraft libraries>"

产物在 dist。构建脚本目前使用 Windows 工具文件名。

先构建运行时 Jar，再构建作者工具：

    python -B source/build_author.py --jdk "<JDK17>" --libraries "<minecraft libraries>" --ffmpeg-dir "<FFmpeg build directory>"

添加 --portable 可生成包含 Java 运行时的 app-image。完整分发工具前，应补齐 FFmpeg 对应源码、构建资料及许可证；本次只公开作者工具源码。

## Tests / 测试

    python -B source/test.py --jdk "<JDK17>" --libraries "<minecraft libraries>" --ffmpeg "<ffmpeg.exe>" --video "<test video.mp4>"

--non-gl 跳过图形测试。检查覆盖窗口嵌入、音频时钟、重复帧纹理、过渡不透明度、配置、进度任务及媒体失败恢复。

进度来自 Forge 与 Minecraft 可观察的状态；没有公开信号的 Mod 内部子任务无法逐项展示。阶段百分比不等同于整个启动耗时的精确比例。

## Source layout / 模块

- source/boot：早期服务、窗口、媒体、主题、进度和有限的扩展 API。
- source/client：Forge Mod、资源加载桥接、主菜单过渡。
- source/author：安装、转码和还原工具。
- source/tests：回归检查。
- source/examples/loading：外部配置模板。
- release.json：版本和构建目标。

内部包名、modId 与服务标识保留 shanghan 名称，便于已有实例升级。

## Issues / 反馈

请提供 Windows、Java、Forge、启动器版本与复现步骤，日志应去除账号信息。不要上传访问令牌、账户文件或私人存档。

## License

代码沿用 Mod 元数据中的 MIT 许可证，见 [LICENSE](LICENSE)。第三方组件保留各自许可证；Gson 许可位于 source/third_party/gson-LICENSE。
