# TripTrail Shared Journey v1

`*.triptrail` 用于在 iPhone 和 Android 之间分享单段旅程或足迹。当前 Android 客户端支持不含媒体的 UTF-8 JSON 文件。

- MIME：`application/vnd.triptrail.journey`
- 扩展名：`.triptrail`
- 日期：ISO 8601
- `formatVersion`：`1`

```json
{
  "format": "triptrail.shared-journey",
  "formatVersion": 1,
  "sharedAt": "2026-08-31T00:00:00Z",
  "kind": "trip",
  "trip": {}
}
```

`kind` 为 `trip` 或 `footprint`，并且只能携带对应的 `trip` 或 `story`。导入时先展示标题和类型，只有用户确认后才追加到本地；根 UUID 相同的内容不会重复导入。导入不会覆盖本机数据，足迹中的源旅程同步关系不会跨设备保留。

Android 客户端导出的旅程字段与 iPhone 版 `TripRecord`、`TripDayRecord`、`ItineraryItemRecord` 对齐；足迹字段与 `StoryRecord`、`StoryDayRecord`、`StoryEntryRecord` 对齐。

完整照片和视频迁移请使用 Android 客户端的 `.triptrailbackup`，其内容是数据清单和媒体文件组成的 ZIP 容器。

## 已移除的路程字段

行程、收藏和足迹不再提供“前往方式”和“路程说明”。`transportRaw` / `transport`、`distanceText`、`routeInfo` 仅兼容读取旧文件，新生成的交换 JSON 不再输出这些字段；接收方必须允许字段缺失。旧版本客户端可能需要升级后导入。本地旧字段仅用于存储兼容，不参与展示、识别、导航方式选择或足迹自动说明。

地点的单地点/起终点、航班车次、预约信息、开始和结束时间继续保留。高德路线规划使用所选地点和规划时的出行方式，不再依赖安排上的旧前往方式。
