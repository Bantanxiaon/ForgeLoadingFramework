# 安装说明

当前经过实际启动测试的方式将 Bootstrap 注册为标准 library。先备份目标实例的版本 JSON 与 config/fml.toml，并关闭游戏和启动器。

实例位于 .minecraft/versions/<实例名称>。先正常安装 Minecraft 1.20.1 + Forge 47.3.22 或 47.3.33。

1. 将客户端 ForgeLoadingFramework-0.3.0.jar 放入实例 mods。
2. 将 ForgeLoadingFramework-bootstrap-0.3.0.jar 复制并改名放到 .minecraft/libraries/shanghan/earlyboot/0.3.0/earlyboot-0.3.0.jar。
3. 在实例版本 JSON 现有 libraries 数组中追加以下条目，保留其他内容：

    {
      "name": "shanghan:earlyboot:0.3.0",
      "downloads": {
        "artifact": {
          "path": "shanghan/earlyboot/0.3.0/earlyboot-0.3.0.jar",
          "size": 58381,
          "sha1": "2121994dab21366f610668b0fb0c742fce8f6609"
        }
      }
    }

4. 在实例 config/fml.toml 中设置：

    earlyWindowControl = true
    earlyWindowProvider = "shanghanearlyloading"

5. 复制配置模板为实例 loading 文件夹，放入自己的 video/loading.mp4 和 bin/ffmpeg.exe。重新打开启动器后启动。

这是实例配置和扩展库注册，不替换 Forge 或 PCL 文件。其他启动器如何处理本地 library 条目需要独立测试。

卸载时关闭游戏和启动器，还原备份的版本 JSON 与 fml.toml，移除自己的客户端 Jar。确认没有实例引用后再移除 Bootstrap library。已有作者工具提供备份及还原功能。

两个 Jar 都放进 mods 的路径正在测试，本发布版本尚未验证该方式，不作为稳定安装说明。
