# 安装说明：游戏组件 0.4.2 / 作者工具 0.4.1

适用 Windows；Minecraft/Forge 与 Java 对应关系见 README。请在副本中接入并备份原配置。

两种方式任选一种，作者工具不是必需。

## 方法一：直接复制安装

1. 下载并解压 Releases 中的完整工具包，打开“可复制到实例”文件夹。
2. 将里面的 mods 和 loading 合并到目标实例目录，也就是该实例存放 mods、config 的那一层。复制的是里面这两个文件夹。
3. 打开实例的 config/fml.toml，合并下面两项。文件不存在时可以新建；已经有同名设置时修改其值，不要重复追加，也不要覆盖其他原有设置。

~~~toml
earlyWindowControl = true
earlyWindowProvider = "shanghanearlyloading"
~~~

4. 重新打开 PCL，启动该实例，确认能看到动画并进入原主菜单。

完整工具包的 loading 已包含 FFmpeg、配置和演示视频，不需要再单独下载这些文件。想换自己的视频，可以替换 loading/video/loading.mp4，使用 H.264 视频和 AAC 音轨；需要转码时可使用作者工具或其他转码软件。

两个运行 Jar 要配套保留。已有旧版本时先备份并移走重复的旧 Jar；已有 loading/config.json 或自定义素材时，先备份再合并，保留自己需要的设置。旧版通过 shanghan:earlyboot library 注册的安装，先按下方“文件位置与旧版升级”处理旧注册。

## 方法二：使用作者工具安装

1. 解压完整工具包，双击“作者工具”文件夹中的 ForgeLoadingAuthor.exe。
2. 点击“选择实例”。可以选择整合包外层、.minecraft、versions，或者实际实例目录；找到多个结果时按完整路径和 Forge 版本选择目标。
3. 选择自己的启动视频，按需要选择画质和音量。默认“兼容优先 · 640×360”。
4. 点击“安装 / 更新”，等待安装和视频转换完成。
5. 重新打开 PCL，启动该实例，确认动画结束后能进入原主菜单。

工具会写入两个运行 Jar、FFmpeg 和配置，并备份安装前的相关文件；选定的视频会自动转换为 H.264、AAC（有音轨时）、30 FPS。后续可以使用“替换视频”和“恢复上次安装”。作者工具本身不放进 mods。

选择整合包外层时，里面需要有 .minecraft/versions 等已安装的游戏目录。自定义实例目录需要有同名版本 JSON，并包含受支持的 Forge 信息。不要选择盘符根目录；尚未安装的导入 ZIP 请先交给 PCL 安装。

## 文件位置与旧版升级

实例目录结构：

    mods/
      ForgeLoadingFramework-bootstrap-0.4.2.jar
      ForgeLoadingFramework-0.4.2.jar
    config/
      fml.toml
    loading/
      config.json
      video/loading.mp4
      bin/ffmpeg.exe

完整工具包已包含配置、播放器和演示视频。仅从源码仓库或单独 Jar 接入时，需要另行准备 source/examples/loading/config.json 模板及 FFmpeg。title 为空时自动读取整合包或实例名称，资源相对路径以 loading 为根目录。兼容旧 config/shanghan_loading 素材目录。

两个 Jar 必须配套，不保留重复版本。旧版通过 shanghan:earlyboot Maven library 注册的 Bootstrap，应先用原作者工具还原或备份后移除该专属注入，再采用双 Jar 接入；不要同时注册两份早期组件。标准 Forge 主类、版本信息保持原样。

## 更换视频

直接替换 loading/video/loading.mp4，文件名不变即可。默认解码输出 640×360、30 FPS。作者工具提供“替换视频”，自动转码 H.264、AAC、30 FPS，并备份旧文件；也可修改配置选择其他尺寸。

作者工具便携版自带 Java；单独运行 Jar 版需要 Java 17 或更高。正常安装保留 Forge 版本 JSON；升级旧 library 方式时，仅清理本组件以前的专属注册。还原保留备份，视频会保留。

## 整合包安装 ZIP

CurseForge / Modrinth 安装包中，以上文件放进 overrides 对应位置。PCL 继续按原 manifest/index 下载其他 Mod、游戏和 Forge。保留原整合包的依赖清单，不复制开发机的账户、绝对路径、存档、工作日志或全部游戏库。

## 检查

通过 PCL 启动，确认动画正常显示，结束后进入原主菜单。只出现 PCL“启动成功”提示不能代替这两步观察。loading/runtime.log 可协助排查，正常结束会记录 OVERLAY_CLOSED。安装阶段的 Forge 下载 SSL EOF 发生在组件运行之前，应检查启动器下载连接。

## PCL 与 Forge 49.0.2 的库目录

本次 1.20.3 测试中，PCL 生成的 Forge 49.0.2 启动配置找不到 libraries；移除动画两个 Jar 后仍出现同样异常。该实例通过标准 JVM 参数 -DlibraryDirectory 指向实际 Minecraft libraries 目录后继续测试。此项属于该启动组合的配置前提，不能把它算成动画组件本身的自动修复；不要把测试机绝对路径写进分发包。

文字样式与自动标题见 [作者使用教程](docs/作者使用教程.md)。
