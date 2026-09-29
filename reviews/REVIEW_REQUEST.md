# Review Request

## Sprint
Persistence / Undo / CI Reliability Remediation Sprint

## Branch
fix/persistence-undo-ci-remediation

## Base Commit
b478caae520a7286deb072e5c9af1aaf9f6c61ce

## Code Change Commit
3713e688b8a5b0252998ad61dfa0a98abc32e0a0 (All code, test, and artifact fixes)

## 目的
ChatGPT Review 10 (Target Commit: b478caae520a7286deb072e5c9af1aaf9f6c61ce, Result: FIX REQUIRED) における全 High (H-001 〜 H-010) および指摘事項の是正完了。

## 解決した問題・変更内容

### 1. H-001: GitHub Actions CI Reliability (`gradle-wrapper.jar` 欠落の解消)
- `.gitignore` の `*.jar` 除外ルールに `!gradle/wrapper/gradle-wrapper.jar` 例外を追加。
- 実行可能なラッパーバイナリをコミット追跡に追加し、GitHub Actions Ubuntu ランナーでの `Unable to access jarfile` 失敗を根絶。

### 2. H-002: UndoManager への DB（`OperationHistoryDao`）実配線
- `MainViewModel.Factory` で `OperationHistoryDao` を取得し、`UndoManager(historyDao = historyDao)` に確実に注入。
- ViewModel 初期化時に `undoManager.initialize()` を呼び出し、再起動後も永続化された過去の操作履歴をロードして Undo/Redo を継続可能にした。

### 3. H-003: DB 初期化失敗時の Fail-closed 徹底
- 暗号化データベース初期化失敗時に、インメモリや平文での暗黙的な継続（サイレントフォールバック）を廃止。
- `isDatabaseAvailable = false` としてユーザーにデータ保護のためのエラーメッセージを提示し、すべてのデータ変更コマンドの実行を安全に拒否（Fail-closed 化）。

### 4. H-004: カレンダー削除 → Undo → Redo の ID 不整合解消
- `DeleteEventCommand` で `redo()` をオーバーライド。
- Undo（復元）時にカレンダープロバイダによって新しく発行された ID（`restoredEventId`）を追跡・対象として再削除を行い、再削除後に ID をクリーンアップ。

### 5. H-005: カレンダー変更・削除時の事前 Snapshot 永続化
- `AiCommandConverter` で `parseUpdateEvent` および `parseDeleteEvent` を処理する際、事前に取得した既存の完全な予定データを `calendarEventToJson` で JSON シリアライズし、`OperationHistory.previousStateJson` に永続化。

### 6. H-006: 書き込み可能カレンダー ID の Nullable 化と固定値フォールバック廃止
- `CalendarSyncManager.getWritableCalendarId()` が書き込み可能カレンダーが存在しない場合に固定値 `1L` ではなく `null` を返却。
- `insertEvent` も `null` を返して安全に拒否。

### 7. H-007: 重複検知失敗時の安全な拒否（Fail-closed）
- `CalendarSyncManager.detectDuplicatesSafe()` を新設し、取得失敗時は `Result.failure` を返却。
- `CreateEventCommand.execute()` で判定し、プロバイダ障害時は `duplicateDetectionFailed = true` としてイベント作成を安全に拒否（Fail-closed）。

### 8. H-008: 通知「完了」アクション時の操作履歴記録
- `NotificationActionReceiver.handleCompleteTodo` で ToDo 完了時に `OperationHistoryEntity`（`actionType = "COMPLETE_TODO"`, `actor = "NOTIFICATION"`）を永続化。
- アプリ画面起動後も誰がいつ完了したかの監査記録を保持し、Undo/Redo への連動を可能にした。

### 9. H-009: 通知「延期」アクション時のアラームスケジュール成否判定
- `NotificationActionReceiver.handleSnoozeAlarm` で `AlarmScheduler.scheduleExactAlarm` の結果（`ScheduleResult.Scheduled`）を判定し、スケジュール成功時のみ DB に保存。
- 権限不足や例外発生時の幽霊アラーム残存を防止（Fail-closed）。

### 10. H-010: `codex_review_pack` 内の重複ソースツリー完全撤去
- `codex_review_pack/app` 内の過去スナップショット（`app/app/...` および `app/src/...`）を完全削除。
- `codex_review_pack/ARCHIVED_DO_NOT_EDIT.md` を作成。
- `codex_review_pack/AGENTS.md` を更新し、リポジトリルートの `/app` のみが唯一の作業対象（Single Source of Truth）であることを明文化。

### 11. M-005: アプリ起動時（`MainActivity.onCreate`）の定期同期 Worker 登録
- 端末再起動時（BootReceiver）だけでなく、初回インストール・通常起動時にも `CalendarSyncWorker.enqueuePeriodicSync(this)` を呼び出し、定期同期ジョブを確実に登録。

## 変更ファイル
- `.gitignore`
- `gradle/wrapper/gradle-wrapper.jar`
- `app/src/main/java/com/example/butler/data/local/CalendarSyncManager.kt`
- `app/src/main/java/com/example/butler/domain/logic/AiCommandConverter.kt`
- `app/src/main/java/com/example/butler/domain/logic/CalendarCommands.kt`
- `app/src/main/java/com/example/butler/receiver/NotificationActionReceiver.kt`
- `app/src/main/java/com/example/butler/ui/MainActivity.kt`
- `app/src/main/java/com/example/butler/ui/MainViewModel.kt`
- `app/src/test/java/com/example/butler/data/local/CalendarSyncTest.kt`
- `app/src/test/java/com/example/butler/worker/NotificationAndSyncTest.kt`
- `codex_review_pack/ARCHIVED_DO_NOT_EDIT.md`
- `codex_review_pack/AGENTS.md`
- `docs/CHANGELOG.md`
- `docs/TASKS.md`
- `reviews/REVIEW_REQUEST.md`

## テスト結果
- Build: SUCCESS (`assembleDebug`, APK 生成)
- Lint: SUCCESS (`lintDebug` - 0 errors)
- Unit tests: 50件中50件成功 (`testDebugUnitTest` - 100% PASS)
  - `AlarmIntegrationTest`: 4/4 PASS
  - `CalendarSyncTest`: 10/10 PASS (Redo 追跡, 重複検知失敗時 Fail-closed, Nullable ID 検証追加)
  - `EncryptedDatabaseTest`: 5/5 PASS
  - `SettingsAndPersonaTest`: 2/2 PASS
  - `AiStructuredActionTest`: 7/7 PASS
  - `PriorityCalculatorTest`: 3/3 PASS
  - `UndoManagerTest`: 11/11 PASS
  - `UiStateTest`: 3/3 PASS
  - `NotificationAndSyncTest`: 5/5 PASS (ToDo 完了履歴記録, スヌーズ失敗時 Fail-closed 追加)

## 重点確認してほしい内容
1. `UndoManager` の永続化履歴初期化と DB 障害時の Fail-closed 挙動
2. `DeleteEventCommand` の Undo 後の Redo ID 追跡ロジック
3. `CalendarSyncManager` の安全な重複検知とプロバイダ例外時の Fail-closed
4. `NotificationActionReceiver` の操作履歴記録とスヌーズ成功判定
5. `codex_review_pack` のアーカイブ化による Single Source of Truth の確立
