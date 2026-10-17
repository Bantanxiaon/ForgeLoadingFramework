# Forge Loading Framework 0.4.0

**后续新增代码及新版本禁止商业使用。** 采用 PolyForm Noncommercial 1.0.0；v0.4.1 及以前已发布的版本保留原 MIT 授权。[许可范围说明](COMMERCIAL-USE.md)

Windows 客户端的 Forge 启动视频组件，支持 Minecraft 1.20.1–26.3 的已知 Forge 版本族。使用同一对 Jar，根据实际版本和接口选择适配代码，不再为每个 Forge 补丁版本单独发包。

启动时播放外部 H.264 MP4 / AAC 音频，显示加载任务和单一总进度，完成后渐隐进入游戏原主菜单。整合包标题自动读取元数据或实例名称，更换视频不需要重新编译。

作者工具已更新至 0.4.1，游戏组件仍为 0.4.0。下面的使用教程包含文字样式、字体、布局和自动标题的具体步骤。

完整工具包用于直接接入整合包，包含作者工具、两份游戏 Jar、配置模板和教程；公开包使用单独生成的演示视频，不包含整合包的原视频素材。

[下载完整工具包](https://github.com/Bantanxiaon/ForgeLoadingFramework/releases/latest) · [单独阅读教程](docs/作者使用教程.md)

## 安装

**直接复制安装**和**作者工具安装**任选一种，作者工具不是必需。下面的使用教程第 1 节列出两种方式的完整步骤，也可查看 [INSTALL.md](INSTALL.md)。

| 文件 | 用途 |
| --- | --- |
| ForgeLoadingFramework-bootstrap-0.4.0.jar | 早期窗口服务、版本路由、视频、音频和进度 |
| ForgeLoadingFramework-0.4.0.jar | Forge 客户端 Mod、资源加载与菜单交接 |
| ForgeLoadingAuthor-0.4.1.jar | 可选作者工具，安装、转码、备份与还原 |

Bootstrap 是早期服务库，普通 Mod 列表可能只显示客户端组件。没有额外前置 Mod；FFmpeg 是外部播放器依赖。作者工具无需安装进游戏。

不修改 Forge/PCL 本体，不需要私有启动器，不依赖开发会话。源码仓库不包含 Minecraft、Java、第三方播放器二进制或视频素材；完整工具包另包含便携 Java 和 FFmpeg。

## 使用教程

### 1. 两种安装方法，任选一种

直接复制和作者工具安装都可以，作者工具是可选的。先在 PCL 中安装好受支持的 Forge 实例，备份要改的文件，再退出游戏和 PCL。

#### 方法一：直接复制安装

1. 下载并解压 Releases 中的完整工具包，打开“可复制到实例”文件夹。
2. 将里面的 mods 和 loading 合并到目标实例目录，也就是该实例存放 mods、config 的那一层。复制的是里面这两个文件夹。
3. 打开实例的 config/fml.toml，合并下面两项。文件不存在时可以新建；已经有同名设置时修改其值，不要重复追加，也不要覆盖其他原有设置。

~~~toml
earlyWindowControl = true
earlyWindowProvider = "shanghanearlyloading"
~~~

4. 重新打开 PCL，启动该实例，确认能看到动画并进入原主菜单。

完整工具包的 loading 已包含 FFmpeg、配置和演示视频，不需要再单独下载这些文件。想换自己的视频，可以替换 loading/video/loading.mp4，使用 H.264 视频和 AAC 音轨；需要转码时可使用作者工具或其他转码软件。

两个运行 Jar 要配套保留。已有旧版本时先备份并移走重复的旧 Jar；已有 loading/config.json 或自定义素材时，先备份再合并，保留自己需要的设置。旧版通过 shanghan:earlyboot library 注册的安装，先按 INSTALL.md 中的说明处理旧注册。

#### 方法二：使用作者工具安装

1. 解压完整工具包，双击“作者工具”文件夹中的 ForgeLoadingAuthor.exe。
2. 点击“选择实例”。可以选择整合包外层、.minecraft、versions，或者实际实例目录；找到多个结果时按完整路径和 Forge 版本选择目标。
3. 选择自己的启动视频，按需要选择画质和音量。默认“兼容优先 · 640×360”。
4. 点击“安装 / 更新”，等待安装和视频转换完成。
5. 重新打开 PCL，启动该实例，确认动画结束后能进入原主菜单。

工具会写入两个运行 Jar、FFmpeg 和配置，并备份安装前的相关文件；选定的视频会自动转换为 H.264、AAC（有音轨时）、30 FPS。后续可以使用“替换视频”和“恢复上次安装”。作者工具本身不放进 mods。

选择整合包外层时，里面需要有 .minecraft/versions 等已安装的游戏目录。自定义实例目录需要有同名版本 JSON，并包含受支持的 Forge 信息。不要选择盘符根目录；尚未安装的导入 ZIP 请先交给 PCL 安装。

### 2. 找到配置

~~~text
实例文件夹/
├─ mods/
│  ├─ ForgeLoadingFramework-bootstrap-0.4.0.jar
│  └─ ForgeLoadingFramework-0.4.0.jar
├─ config/fml.toml
└─ loading/
   ├─ config.json
   ├─ video/loading.mp4
   ├─ fonts/
   ├─ images/
   └─ bin/ffmpeg.exe
~~~

作者工具不用放进 mods。两份游戏 Jar 配套保留。

旧版 config/shanghan_loading 仍可读取；如果 loading 已存在，会优先读取 loading。不要两处都改。下文的 config.json 指实际使用的素材目录中的文件。

改配置前退出游戏，另存备份，用支持 UTF-8 的文本编辑器打开。JSON 不能写注释，最后一项后面不能多逗号。下面标明“片段”的示例只修改对应字段，不要覆盖整份文件。

### 3. 换视频、改音量

在作者工具中选择实例和新视频，点击“替换视频”。兼容优先输出 640×360、30 FPS、H.264，有音轨时转成 AAC，旧视频会备份。

也可以直接替换 loading/video/loading.mp4，但文件必须是可解码的视频，不能只改后缀。高分辨率素材建议先通过工具转码。

| 顶层字段 | 示例 | 作用 |
| --- | --- | --- |
| video | "video/loading.mp4" | 视频文件 |
| audio | true | 是否播放音频 |
| audioFile | "" | 空值使用视频音轨；也可指定独立音频 |
| volume | 0.8 | 音量，0 到 1 |
| width / height | 640 / 360 | 解码输出尺寸，画面仍随窗口缩放 |
| fps | 30 | 目标帧率，30 到 60 |
| fadeOutMs | 450 | 结束渐隐时间，单位毫秒 |

素材路径相对于 loading，例如 fonts/pack.ttf。不要填写自己电脑上的绝对路径，否则换电脑会找不到文件。

### 4. 自动标题怎样读名称

保留顶层 title 为空，ui.title.text 写为 ${packName}。配置位置示意：

~~~json
{
  "title": "",
  "ui": {
    "title": {
      "text": "${packName}"
    }
  }
}
~~~

这是片段。程序按以下顺序找名称，找到非空名称就停止：

1. 游戏目录下 modrinth.index.json 的 name。
2. 游戏目录下 manifest.json 的 name。
3. 启动参数 --version 后面的实例名称。
4. 游戏目录的最后一级文件夹名。

例如游戏目录是 D:/Minecraft/versions/我的整合包，前面三项都没有名称时，标题读取“我的整合包”。

PCL 通常会传入 --version，因此多数情况下显示启动器中的实例名。它不是始终读取整合包最外层文件夹的名字；元数据里的 name 优先。

要跟随实例名称：title 留空，ui.title.text 保持 ${packName}，通过 PCL 修改实例名称。如果游戏目录仍有上述元数据，还要检查其中 name 是否是旧名。不要只在资源管理器中改 versions 下的文件夹名，否则可能与版本 JSON 文件名不匹配。

想固定一个名字，改顶层字段：

~~~json
"title": "我的整合包"
~~~

ui.title.text 仍保持 ${packName}。也能直接把 ui.title.text 写成固定文字，但这个位置就不再显示自动名称。

### 5. 改提示和加载文字

| ui 中的项目 | 对应内容 |
| --- | --- |
| title | 大标题 |
| subtitle | 标题下的提示 |
| status | 当前加载阶段 |
| tasks | 当前 Mod 和操作的详细条目 |
| mods | 已发现的 Mod 数量 |
| percent | 总进度百分比 |

例如顶层 subtitle 改成：

~~~json
"subtitle": "正在准备世界，请稍候"
~~~

ui.subtitle.text 保持 ${subtitle}，就会显示这句话。

text 中可以使用：

| 变量 | 内容 |
| --- | --- |
| ${packName} | 自动识别或指定的标题 |
| ${subtitle} | 顶层 subtitle |
| ${stage} | 当前阶段 |
| ${overallPercent} | 总进度，例如 42% |
| ${modCount} | 已发现的 Mod 数量信息 |
| ${sourceLabel} | 当前进度来源 |

tasks 的内容由观察到的 Mod 任务生成，不用填写 text，改字体和位置即可。percent 保持 ${overallPercent}；${stagePercent} 是局部阶段数据，不代表总加载进度。

### 6. 改字体、字号、颜色

下面是完整的 ui.title 对象，替换现有 ui 里的 title：

~~~json
"title": {
  "text": "${packName}",
  "font": "fonts/pack.ttf",
  "size": 40,
  "weight": "bold",
  "color": "#FFF3D6",
  "opacity": 1,
  "anchor": "top-left",
  "x": "50",
  "y": "430",
  "enabled": true
}
~~~

| 字段 | 修改方式 |
| --- | --- |
| font | 字体相对路径；空值使用顶层 font，再为空使用系统默认字体 |
| size | 字号，8 到 180，默认标题 36 |
| weight | bold 粗体，normal 常规 |
| color | #RRGGBB，例如 #FFFFFF 是白色 |
| opacity | 0 到 1；1 完全显示，0.5 半透明 |
| enabled | true 显示，false 隐藏 |

把字体放进 loading/fonts，文件名与配置一致。字体应不超过 8,000,000 字节，且能被 Java 字体读取器识别；建议先用包含中文的 TTF 测试。文件缺失或读取失败会回到系统默认字体。发包前确认字体允许随包分发。

统一所有文字字体时，顶层 font 填写：

~~~json
"font": "fonts/pack.ttf"
~~~

各文字项目的 font 留空。某一处需要另一种字体，再单独填写。

颜色也支持 #RRGGBBAA，末尾两位为透明度，例如 #FFFFFF80 约半透明。它会与 opacity 相乘，一般只改其中一种。

### 7. 改位置和进度条

布局使用 1280×720 设计画布，再随窗口缩放，坐标不是显示器物理像素。x 增大向右，y 增大向下，也能填写百分比。

| anchor | 坐标对应位置 |
| --- | --- |
| top-left | 左上角 |
| top-right | 右上角 |
| top-center | 顶部中点 |
| bottom-left | 左下角 |
| bottom-right | 右下角 |
| center | 中心 |

标题居中、靠下的片段：

~~~json
"anchor": "top-center",
"x": "50%",
"y": "430"
~~~

保留 title 的其他字段。左对齐文字过长会截短；居中或右对齐也可能超出画布，需要减小字号或缩短名称。

ui.taskSpacing 为任务行距，默认 25；ui.maxTasks 为最多显示行数，默认 3。增加行数后要给下方文字留位置。

完整的 ui.progress 对象：

~~~json
"progress": {
  "x": "50",
  "y": "647",
  "width": "1080",
  "height": "10",
  "radius": 10,
  "color": "#A9DDAE",
  "background": "#FFFFFF37",
  "opacity": 1,
  "enabled": true
}
~~~

color 是已加载部分，background 是轨道，radius 控制圆角。百分比文字在 ui.percent，需单独移动。

### 8. 遮罩与 Logo

ui.dimBackground 为 false 时关闭底部渐变遮罩。ui.shadeOpacity 控制深浅，0 到 1。它只影响视频上的遮罩，不应使游戏窗口透出桌面。

顶层 logo 指定图片，默认 images/logo.png。位置和大小由 ui.logo 的 x、y、width、height 控制，不需要时设置 ui.logo.enabled 为 false。

### 9. 检查和发包

保存配置，用 PCL 启动同一个实例，检查标题、字体、位置，并等待进入原主菜单。

全部变回默认样式时，先检查 JSON 引号、逗号和括号。字体不对就核对文件名。标题不对按第 4 节核对来源。没有动画，检查是否改到了当前实例使用的素材目录，再查看其中 runtime.log。

发布整合包时，把两个 Jar、loading 文件夹和 fml.toml 的组件设置放进安装包 overrides 的对应位置，保留原 Mod 下载清单。不要带上账户、存档、运行日志或安装备份。

“恢复上次安装”会回退上次写入的组件与配置，视频保留。shanghan_loading_backups 是本地备份目录，不作为玩家端素材分发。

## 版本与维护

| Minecraft | Forge 主版本 | 游戏所需 Java |
| --- | --- | --- |
| 1.20.1–1.20.4 | 47–49 | 17 |
| 1.20.6、1.21–1.21.11 的已发布 Forge 对应版本 | 50–61 | 21 |
| 26.1–26.3 | 62–66 | 25 |

1.20.1 最低 Forge 47.3.22。仅接受路由表列出的 Minecraft/Forge 配对；没有对应 Forge 发行的版本不凭空添加支持。运行时先检查接口，再加载具体适配代码；同一版本族补丁通常无需改代码，但实际兼容仍以测试为准。未知或不兼容接口保留标准 Forge 加载流程。

共用媒体、配置和进度核心；窗口入口分为旧 GLFW、带后端参数的 GLFW、SDL 三类，后端选择接口另做探测。资源加载桥接保留旧接口路径，并为较新游戏生成本组件自己的 LoadingOverlay 子类，不修改游戏或 Forge 类。新 Minecraft 改动接口时仍可能需要补充适配，路由不能免除未来维护。

纯净版验证不等于所有整合包、显卡、第三方覆盖界面均已验证。Vulkan、Linux 和 macOS 未作为完整游戏实测支持范围。

## 配置和性能

配置模板位于 source/examples/loading。默认输出 640×360、30 FPS，画面按窗口缩放；不默认解码 1080p。FFmpeg 软件解码，OpenGL 合成，有界帧队列、复用纹理、降低 UI 重绘频率；播放期间持续更新。帧率仍取决于电脑和加载负载，不能保证所有电脑始终达到 30 FPS。

进度条和数字均使用 overall，总进度不按阶段清零。任务显示 Forge 可观察到的当前 Mod 名称、modId 和操作；游戏没有暴露的内部子任务无法逐项获得。百分比不是剩余时间承诺。

## 开发

源代码、构建入口和 SDK 隔离说明见 [source/README.md](source/README.md)。新增代码与后续版本采用非商业许可；既有 MIT 代码和第三方依赖保留原许可，见 [许可范围说明](COMMERCIAL-USE.md)、LICENSE 和 source/third_party。
