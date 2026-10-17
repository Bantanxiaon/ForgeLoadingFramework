# 安装说明：0.4.0

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

直接替换 loading/video/loading.mp4，文件名不变即可。默认解码输出 640×360、30 FPS。作者工具提供“导入视频”，自动转码 H.264、AAC、30 FPS，并备份旧文件；也可修改配置选择其他尺寸。

ForgeLoadingAuthor 便携版带独立 Java，双击运行后选择实例目录。Jar 版需要 Java 17 或更高。工具可安装、转码和还原；安装写入两个 mods 文件和 fml 配置，不修改正常 Forge 版本 JSON。升级旧 library 方式时，只清理本组件以前的专属注册。还原会保留备份与迁移后的文件以便回退。

## 整合包安装 ZIP

CurseForge / Modrinth 安装包中，以上文件放进 overrides 对应位置。PCL 继续按原 manifest/index 下载其他 Mod、游戏和 Forge。保留原整合包的依赖清单，不复制开发机的账户、绝对路径、存档、工作日志或全部游戏库。

## 检查

通过 PCL 启动，确认动画正常显示，结束后进入原主菜单。只出现 PCL“启动成功”提示不能代替这两步观察。loading/runtime.log 可协助排查，正常结束会记录 OVERLAY_CLOSED。安装阶段的 Forge 下载 SSL EOF 发生在组件运行之前，应检查启动器下载连接。

## Forge 49.0.2 的库目录

PCL 启动 Minecraft 1.20.3 / Forge 49.0.2 时，若出现 Library directory: libraries does not exist，可通过实例标准 JVM 参数 -DlibraryDirectory 指定实际 Minecraft libraries 目录。目录应属于当前安装，不要把开发机绝对路径写入整合包。此设置属于启动器与 Forge 的支持库定位配置。
