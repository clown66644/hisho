# DATA_MODEL.md - データモデル仕様書

## 1. 概要
本アプリで管理する主要データエンティティの定義。すべてのエンティティは端末暗号化ストレージに保存されます。

---

## 2. エンティティ定義

### 2.1 ToDo (タスク)
- `id`: String (UUID)
- `title`: String (必須)
- `detail`: String?
- `memo`: String?
- `dueDate`: Long? (Unix Timestamp)
- `scheduledStartTime`: Long?
- `scheduledEndTime`: Long?
- `estimatedMinutes`: Int?
- `priorityScore`: Double (算出スコア)
- `priorityLevel`: Enum (CRITICAL, HIGH, MEDIUM, LOW)
- `status`: Enum (UNSTARTED, IN_PROGRESS, PAUSED, COMPLETED, POSTPONED, CANCELLED, ARCHIVED)
- `mentalLoad`: Int (1-5)
- `requiredStamina`: Int (1-5)
- `requiresOutgoing`: Boolean
- `createdAt`: Long
- `updatedAt`: Long

### 2.2 CalendarEvent (予定)
- `id`: String (UUID)
- `googleEventId`: String?
- `title`: String
- `startTime`: Long
- `endTime`: Long
- `location`: String?
- `belongings`: List<String>
- `prepTasks`: List<String>
- `isAllDay`: Boolean

### 2.3 OperationHistory (操作履歴)
- `id`: String (UUID)
- `timestamp`: Long
- `actor`: Enum (USER, AI_BUTLER, AI_MAID, SYSTEM)
- `userInput`: String?
- `aiInterpretation`: String?
- `actionType`: String (例: "CREATE_TODO", "UPDATE_EVENT")
- `previousStateJson`: String? (暗号化)
- `newStateJson`: String? (暗号化)
- `status`: Enum (SUCCESS, FAILED, PARTIAL_SUCCESS)
- `isUndone`: Boolean
