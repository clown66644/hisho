# CHANGELOG.md - 変更履歴

## [v0.2.4] - 2026-09-29
### Fixed & Improved (Persistence / Undo / CI Reliability Remediation Sprint)
- **H-001 (GitHub Actions CI Reliability)**:
  - `.gitignore` の `*.jar` 除外ルールに `!gradle/wrapper/gradle-wrapper.jar` 例外を追加し、ラッパーバイナリをコミット追跡。Ubuntu CI ランナーでの `Unable to access jarfile` 失敗を根絶。
- **H-002 & H-003 (UndoManager DB Wiring & Fail-Closed Initialization)**:
  - `MainViewModel.Factory` で `OperationHistoryDao` を `UndoManager(historyDao = historyDao)` に渡し、起動時に `undoManager.initialize()` で永続化履歴をロード。
  - 暗号化 DB 初期化失敗時、インメモリ・平文へのフォールバックを廃止し、`isDatabaseAvailable = false` としてユーザーに安全なエラー通知を行い、変更コマンドを拒否（Fail-closed 化）。
- **H-004 (Calendar Delete → Undo → Redo ID Corruption)**:
  - `DeleteEventCommand` において `redo()` をオーバーライド。Undo（復元）時に新規発行された Provider ID（`restoredEventId`）を追跡・対象として再削除を行い、再削除後に ID をクリーンアップ。
- **H-005 (Calendar Snapshot Persistence in OperationHistory)**:
  - `AiCommandConverter` で `parseUpdateEvent` および `parseDeleteEvent` を生成する際、カレンダープロバイダから取得したスナップショットを JSON シリアライズし、`OperationHistory.previousStateJson` に永続化。
- **H-006 & H-007 (Calendar Nullable Writable ID & Safe Duplicate Detection Fail-Closed)**:
  - `CalendarSyncManager.getWritableCalendarId()` が書き込み可能カレンダー不在時に固定値 `1L` ではなく `null` を返却。
  - `CalendarSyncManager.detectDuplicatesSafe()` を新設。プロバイダ障害時は `CreateEventCommand` が `duplicateDetectionFailed = true` として作成を安全に拒否（Fail-closed）。
- **H-008 & H-009 (Notification Actions History & Snooze Verification)**:
  - `NotificationActionReceiver.handleCompleteTodo` で ToDo 完了時に `OperationHistoryEntity`（`COMPLETE_TODO`, `NOTIFICATION`）を永続化。
  - `NotificationActionReceiver.handleSnoozeAlarm` で `AlarmScheduler.scheduleExactAlarm` の成否（`ScheduleResult.Scheduled`）を判定し、スケジュール成功時のみ DB に保存。失敗時の幽霊アラーム残存を防止。
- **H-010 (Single Source of Truth Cleanup in `codex_review_pack`)**:
  - `codex_review_pack/app` 内の重複ソースツリー（`app/app/...` および `app/src/...`）を完全削除。`codex_review_pack/ARCHIVED_DO_NOT_EDIT.md` を作成し、リポジトリルート `/app` が唯一の真実源であることを明文化。
- **M-005 (Periodic Calendar Sync Worker Registration)**:
  - `MainActivity.onCreate()` において初回インストール・起動時にも `CalendarSyncWorker.enqueuePeriodicSync(this)` を呼び出し、定期同期ジョブを確実に登録。
- **Tests & Quality**:
  - 全 50 件の単体テスト PASSED（100% 成功、新規追加 4 件含む）。
  - Android Lint: 0 Errors。
  - `app-debug.apk` ビルド成功。

## [v0.2.3] - 2026-09-29
### Fixed & Improved (Source of Truth 統合 & P0/P1 是正 Sprint)
- **Source of Truth 一本化**:
  - 正式製品コードをトップレベル `/app` に集約・一本化。
  - レビュー用アーカイブとの境界を明確化。
- **P0-01 (Database Data Loss Prevention)**:
  - `AppDatabase.kt` から危険な `.fallbackToDestructiveMigration()` を完全削除。
  - 明示的なマイグレーション `MIGRATION_1_2`（操作履歴テーブル作成）および `MIGRATION_2_3`（アラームテーブル作成）を実装。
- **P0-02 & P0-03 (Calendar Snapshot & Safe Undo)**:
  - `CalendarSyncManager` に `getEventById()` を追加。
  - `AiCommandConverter.parseUpdateEvent()` および `parseDeleteEvent()` において、変更/削除前にカレンダープロバイダから完全な予定スナップショットを取得。架空データによる上書き・復元破壊を根絶。
  - 存在しないイベントへの更新/削除要求は `NoSuchElementException` で安全に即時拒否。
- **P1-02, P1-03 & P1-09 (UI & DB 実配線 / Safe Undo)**:
  - `MainViewModel.Factory` を新設し、暗号化 DB（`TodoDao`）および Context（`CalendarSyncManager`）を実接続。
  - ToDo データを Room DB から自動読込。
  - `MainViewModel.undo()` で無関係な ToDo が末尾から削除される問題を解消し、コマンド種別に応じた安全な Undo 処理および DB リロードを実装。
- **P1-04 (BootReceiver Alarm Restoration & goAsync)**:
  - `BootReceiver` において OS 呼び出し時に `AppDatabase` を安全に取得してアラームを復元するフォールバックを実装。
  - `goAsync()` と `try-finally { pendingResult.finish() }` によりプロセス強制終了を防止。
- **P1-05 (Runtime Permissions)**:
  - `MainActivity` に `POST_NOTIFICATIONS`（Android 13+）および `READ_CALENDAR` / `WRITE_CALENDAR` の実行時権限要求処理を実装。
- **P1-06 & P1-08 (Calendar Enhancements)**:
  - `CalendarSyncManager` に `getWritableCalendarId()` を実装し、書き込み可能なカレンダー ID を動的解決。
  - `CreateEventCommand.execute()` で重複検知（`detectDuplicates`）を実行し、無断の重複予定作成をガード。
- **P1-07 (GenericConfirmationCommand No-op 成功廃止)**:
  - 不完全な操作が承認ボタン押下で「成功」と誤認表示される挙動を解消。
- **Security (SettingsManager Fail-Closed)**:
  - `SettingsManager` の平文 SharedPreferences フォールバックを廃止。暗号化ストレージ初期化失敗時は API キーの平文保存を行わず安全に拒否（Fail-closed）。
- **Tests & Quality**:
  - 全 46 件の単体テスト PASSED（100% 成功）。
  - Android Lint: 0 Errors。
  - `app-debug.apk` ビルド成功。

## [v0.2.2] - 2026-09-29
### Added
- **2-3：バックグラウンド同期 & 通知カスタマイズの実装 (フェーズ2完成)**:
  - `WorkManager` による定期的な端末カレンダー同期エンジン (`CalendarSyncWorker.kt`)。
  - バッテリー非低下・15分最小実行間隔の制約設定および BootReceiver 連動。
  - 重要度別通知チャンネル生成モジュール (`NotificationHelper.kt`: アラーム用 `IMPORTANCE_HIGH`、同期用 `IMPORTANCE_DEFAULT`)。
  - インタラクティブ通知アクションボタン（「完了」「10分延期」）および安全な受信ハンドラ (`NotificationActionReceiver.kt`)。
  - AlarmReceiver の NotificationHelper 統合リファクタリング。
  - 通知チャンネル・アクション処理の単体テスト作成 (`NotificationAndSyncTest.kt`、全 42 件単体テスト PASSED、Lint Error 0 件)。

## [v0.2.1] - 2026-09-29
### Added
- **2-2：Google カレンダー外部連携モジュールの実装**:
  - `CalendarSyncManager` による端末カレンダー (`CalendarContract`) 同期、権限ガード、重複予定検知 (`detectDuplicates`)。
  - カレンダー操作用 Command (`CreateEventCommand`, `UpdateEventCommand`, `DeleteEventCommand`) と Undo / Redo 連動。
  - `OperationPolicyManager` に `CREATE_EVENT` を追加、変更・削除 (`UPDATE_EVENT`, `DELETE_EVENT`) の確認必須ガード。
  - `AiCommandConverter` における `CREATE_EVENT`, `UPDATE_EVENT`, `DELETE_EVENT` パースおよび不完全パラメータ時の `GenericConfirmationCommand` 安全フォールバック。
  - カレンダー同期・操作・ポリシーの単体テスト作成 (`CalendarSyncTest.kt`、全 38 件単体テスト PASSED、Lint Error 0 件)。

## [v0.2.0] - 2026-07-27
### Added
- **2-1：設定暗号化 & マルチペルソナ統合モジュールの実装**
