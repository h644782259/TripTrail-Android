# 旅迹 Android

这是与 iPhone 版并列、独立维护的原生 Android 客户端，不会修改 iOS 数据或构建设置。应用使用 Kotlin、Jetpack Compose、Kotlin Serialization 和设备端 ML Kit 中文 OCR，旅行数据默认只保存在本机。

## 已实现

- 旅程：创建、按日期生成天数、继续加天、安排增删改、完成状态、同日下一段自动衔接上一段结束时间、高德原生导航。
- 智能录入：粘贴文本或选择截图；截图使用设备端中文 ML Kit OCR，识别结果先进入可编辑表单，确认后才保存。
- 安排字段：类别、时间、地址、交通、路程、游玩时长、预约、花费、笔记，以及最多 9 个照片/视频。
- 足迹：独立创建、旅程一键收录、源旅程骨架同步、足迹内容独立编辑、照片/视频、二次确认删除。
- 统计：旅程总花费和每日花费柱状图。
- 分享：导出、导入无媒体 `.triptrail`，JSON 字段与 iPhone 版 `TripTrail Shared Journey v1` 对齐。
- 换机：`.triptrailbackup` 同时包含本地数据与所选照片/视频原件；恢复前显示数量并二次确认替换。
- 所有旅行数据都保存在本机，不依赖业务服务器。

## 构建

需要 JDK 17 或更高版本以及 Android SDK 35：

```bash
./gradlew :app:assembleDebug
```

APK 生成在：

```text
app/build/outputs/apk/debug/app-debug.apk
```

安装到已连接且开启 USB 调试的 Android 手机：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

运行完整本地检查：

```bash
./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
```

## 目录

```text
app/src/main/java/com/personal/triptrail/
├── data/       # 领域模型、本地 JSON 仓库和业务规则
├── ui/         # 旅程、足迹、统计、设置页面
└── util/       # OCR 解析、高德跳转、分享与完整备份
```

最低支持 Android 8.0（API 26），目标版本 Android 15（API 35）。截图识别使用随应用打包的设备端中文模型，不会把订单或行程截图上传到旅迹服务器。

`.triptrail` 无媒体 JSON 与 iPhone 版 v1 协议对齐；Android 完整换机备份使用 `.triptrailbackup`，包含旅行数据和 App 本地媒体副本。
