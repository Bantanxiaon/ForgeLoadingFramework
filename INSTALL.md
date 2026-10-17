# 安装说明：游戏组件 0.4.0 / 作者工具 0.4.1

适用 Windows；Minecraft/Forge 与 Java 对应关系见 README。请在副本中接入并备份原配置。

## 双 Jar 接入

实例目录结构：

    mods/
      ForgeLoadingFramework-bootstrap-0.4.0.jar
      ForgeLoadingFramework-0.4.0.jar
    config/
      fml.toml
    loading/
      config.json
      video/loading.mp4
      bin/ffmpeg.exe

合并 config/fml.toml，保留原来的其他设置：

    earlyWindowControl = true
    earlyWindowProvider = "shanghanearlyloading"

复制 source/examples/loading/config.json 作为模板。放入自己的 H.264/AAC 视频和合法获得的 FFmpeg Windows 构建。title 为空时自动读整合包或实例名称。相对路径均以 loading 为根目录。兼容旧 config/shanghan_loading 素材目录。

两个 Jar 必须配套，不保留重复版本。旧版通过 shanghan:earlyboot Maven library 注册的 Bootstrap，应先用原作者工具还原或备份后移除该专属注入，再采用双 Jar 接入；不要同时注册两份早期组件。标准 Forge 主类、版本信息保持原样。

## 更换视频

直接替换 loading/video/loading.mp4，文件名不变即可。默认解码输出 640×360、30 FPS。作者工具提供“替换视频”，自动转码 H.264、AAC、30 FPS，并备份旧文件；也可修改配置选择其他尺寸。

ForgeLoadingAuthor 便携版带独立 Java，双击运行后可选整合包外层、.minecraft、versions 或实际实例；多个结果需要选择目标。Jar 版需要 Java 17 或更高。工具可安装、转码和还原；安装写入两个 mods 文件和 fml 配置，不修改正常 Forge 版本 JSON。升级旧 library 方式时，只清理本组件以前的专属注册。还原会保留备份与迁移后的文件以便回退。

## 整合包安装 ZIP

CurseForge / Modrinth 安装包中，以上文件放进 overrides 对应位置。PCL 继续按原 manifest/index 下载其他 Mod、游戏和 Forge。保留原整合包的依赖清单，不复制开发机的账户、绝对路径、存档、工作日志或全部游戏库。

## 检查

通过 PCL 启动，确认动画正常显示，结束后进入原主菜单。只出现 PCL“启动成功”提示不能代替这两步观察。loading/runtime.log 可协助排查，正常结束会记录 OVERLAY_CLOSED。安装阶段的 Forge 下载 SSL EOF 发生在组件运行之前，应检查启动器下载连接。

## PCL 与 Forge 49.0.2 的库目录

本次 1.20.3 测试中，PCL 生成的 Forge 49.0.2 启动配置找不到 libraries；移除动画两个 Jar 后仍出现同样异常。该实例通过标准 JVM 参数 -DlibraryDirectory 指向实际 Minecraft libraries 目录后继续测试。此项属于该启动组合的配置前提，不能把它算成动画组件本身的自动修复；不要把测试机绝对路径写进分发包。

文字样式与自动标题见 [作者使用教程](docs/作者使用教程.md)。
