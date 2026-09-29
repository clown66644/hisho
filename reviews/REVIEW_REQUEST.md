# Review Request

## Sprint
Source of Truth 統合 & P0/P1 是正 Sprint

## Branch
fix/source-of-truth-and-p0

## Review Target Commit
89f7ae2fceed4ae2e2c18052b9c2aeb8753c36fd

## 目的
ChatGPT Review (Result: FIX REQUIRED) による指摘事項に基づき、Source of Truth の完全一本化、データ破損・消失リスク（P0）の根絶、および UI・DB・カレンダー・権限の配線不備（P1）の是正を完了。

## 解決した問題・変更内容

### 1. Source of Truth 一本化 (P1-01)
- 正式製品コードをトップレベル `/app` に集約・一本化。
- `codex_review_pack` は提出アーカイブ・ログ用として整理し、コードの多重管理を解消。

### 2. Database Data Loss Prevention (P0-01)
- `app/src/main/java/com/example/butler/data/local/AppDatabase.kt` から危険な `.fallbackToDestructiveMigration()` を完全削除。
- 明示的なマイグレーション `MIGRATION_1_2`（操作履歴テーブル）および `MIGRATION_2_3`（アラームテーブル）を実装・登録。
- マイグレーションの SQL 実行検証テストを `EncryptedDatabaseTest.kt` に追加。

### 3. Calendar Snapshot & Safe Undo (P0-02, P0-03)
- `CalendarSyncManager.kt` に `getEventById(eventId: String): CalendarEvent?` を実装。
- `AiCommandConverter.parseUpdateEvent()` および `parseDeleteEvent()` において、変更/削除前にカレンダープロバイダから対象イベントの完全なスナップショットを取得。
- 存在しないイベントに対する更新/削除は `NoSuchElementException` で即座に安全に拒否（架空データの生成・上書きを根絶）。
- 翌月や過去日付への変更でも、Undo 時に元日時・場所・タイトルが 100% 正確に復元されるよう検証テストを追加。

### 4. UI と暗号化 DB / Context の実配線 (P1-02, P1-03)
- `MainViewModel.Factory` を新設し、`MainActivity` から `AppDatabase`（`TodoDao`）および Context（`CalendarSyncManager`）を注入。
- `MainViewModel` 初期化時に DB から ToDo リストを自動読込。

### 5. BootReceiver のプロセス保護とアラーム復元 (P1-04)
- `BootReceiver.kt` において、OS 呼び出し時（provider が null の場合）に `AppDatabase.getInstance(context)` を安全に取得してアラームを復元するフォールバックを実装。
- `val pendingResult = goAsync()` と `try-finally { pendingResult.finish() }` を適用し、バックグラウンド処理中のプロセス強制終了を防止。

### 6. Runtime Permission の要求処理追加 (P1-05)
- `MainActivity.kt` に `POST_NOTIFICATIONS`（Android 13+）および `READ_CALENDAR` / `WRITE_CALENDAR` の実行時権限要求ダイアログフローを実装。

### 7. カレンダー ID 動的解決 (P1-06)
- `CalendarSyncManager` に `getWritableCalendarId()` を実装し、1L 固定ではなく書き込み可能なプライマリカレンダーを探索して設定。

### 8. GenericConfirmationCommand の No-op 成功廃止 (P1-07)
- `GenericConfirmationCommand.execute()` を `false` に変更し、不完全な操作の承認で「操作を承認・実行しました」と誤認表示される問題を解消。

### 9. CreateEventCommand への重複予定ガード接続 (P1-08)
- `CreateEventCommand.execute()` 内で `calendarSyncManager.detectDuplicates()` を実行。`allowDuplicate = false`（デフォルト）の場合に重複作成をブロック。

### 10. ViewModel Undo 時の整合性保証 (P1-09)
- `MainViewModel.undo()` で無条件に `currentTodoList.removeAt(last)` を呼ぶ雑な処理を廃止。直前のコマンドが ToDo 作成であった場合のみリストから除外し、DB 接続時は `loadTodos()` で常に正確な状態を同期。

### 11. Security (SettingsManager Fail-closed)
- `SettingsManager.kt` において、`EncryptedSharedPreferences` 初期化失敗時に平文 SharedPreferences へフォールバックする脆弱性を廃止。暗号化ストレージが使用不可の場合は API キー保存を安全に拒否（Fail-closed 化）。

## 変更ファイル
- `app/src/main/java/com/example/butler/data/local/AppDatabase.kt`
- `app/src/main/java/com/example/butler/data/local/CalendarSyncManager.kt`
- `app/src/main/java/com/example/butler/data/local/security/SettingsManager.kt`
- `app/src/main/java/com/example/butler/domain/logic/AiCommandConverter.kt`
- `app/src/main/java/com/example/butler/domain/logic/CalendarCommands.kt`
- `app/src/main/java/com/example/butler/domain/logic/UndoManager.kt`
- `app/src/main/java/com/example/butler/receiver/BootReceiver.kt`
- `app/src/main/java/com/example/butler/ui/MainActivity.kt`
- `app/src/main/java/com/example/butler/ui/MainViewModel.kt`
- `app/src/test/java/com/example/butler/data/local/CalendarSyncTest.kt`
- `app/src/test/java/com/example/butler/data/local/EncryptedDatabaseTest.kt`
- `docs/CHANGELOG.md`
- `docs/TASKS.md`
- `reviews/CHATGPT_REVIEW.md`
- `reviews/REVIEW_REQUEST.md`

## 関連要件
REQUIREMENTS.md:
- 1.1 セキュリティとプライバシー（平文保存禁止、Fail-closed）
- 2.1 データ破損・消失の防止（Destructive Migration の禁止）
- 3.2 カレンダー連携（重複検知、安全な同期）
- 4.2 操作の取り消し (Undo/Redo: 完全なスナップショットによる復元)

## テスト結果
- Build: SUCCESS (`assembleDebug`, APK 24.4MB 生成)
- Lint: SUCCESS (`lintDebug` - 0 errors, 55 warnings)
- Unit tests: 46件中46件成功 (`testDebugUnitTest` - 100% PASS)
  - `AlarmIntegrationTest`: 4/4 PASS
  - `CalendarSyncTest`: 7/7 PASS (スナップショット更新・削除 Undo, 重複ブロック, 不在時例外含む)
  - `EncryptedDatabaseTest`: 5/5 PASS (Migration SQL 実行検証含む)
  - `SettingsAndPersonaTest`: 2/2 PASS
  - `AiStructuredActionTest`: 7/7 PASS
  - `PriorityCalculatorTest`: 3/3 PASS
  - `UndoManagerTest`: 11/11 PASS
  - `UiStateTest`: 3/3 PASS
  - `NotificationAndSyncTest`: 4/4 PASS

## 重点確認してほしい内容
1. `AppDatabase.kt` の `MIGRATION_1_2` および `MIGRATION_2_3` のスキーマ定義の妥当性
2. `AiCommandConverter.kt` および `CalendarSyncManager.kt` におけるスナップショット取得・不在時安全拒否の設計
3. `MainViewModel.kt` における Room DB / Context の Factory 注入と Undo 時の ToDo 整合性
4. `BootReceiver.kt` の `goAsync()` および DB 取得フォールバックの実装
