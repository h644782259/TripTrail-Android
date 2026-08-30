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
