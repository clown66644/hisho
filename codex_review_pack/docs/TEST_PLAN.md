# TEST_PLAN.md - テスト計画書

## 1. テストレベル
- **単体テスト (Unit Test)**:
  - 優先順位スコア計算ロジック (`PriorityCalculatorTest`)
  - 操作履歴とUndo/Redo状態計算・永続化・アトミックロールバック・冪等性 (`UndoManagerTest`)
  - 暗号化DB・KeyStore鍵管理・非露出・非破壊保護・`@Upsert`安全更新 (`EncryptedDatabaseTest`)
  - 構造化AI操作・ポリシーカーネル判定・空タイトル/壊れたJSON拒否 (`AiStructuredActionTest`)
  - 本番アラームUUID独立管理・期限切れ除外・DB失敗時OS補償取消・Undo解約 (`AlarmIntegrationTest`)
  - UIカード自動追加・優先順位ソート・確認カード承認/拒否・Undoボタン活性化同期 (`UiStateTest`)
  - EncryptedSharedPreferences 暗号化設定保存・マルチペルソナ変更 Command & Undo 復元 (`SettingsAndPersonaTest`)
  - カレンダー同期マネージャーCRUD・重複予定検出・カレンダーCommand実行/Undo/Redo・AI構造化パース・ポリシーカーネル判定 (`CalendarSyncTest`)
- **統合テスト (Integration Test)**:
  - AI対話 -> ToDo自動生成 -> DB暗号化保存 -> 通知予約の一連フロー
  - Googleカレンダー連携と重複検知
- **実機検証 (Device Integration Test)**:
  - Doze Mode（バッテリー最適化）下でのアラーム精度検証
  - Android再起動 (`RECEIVE_BOOT_COMPLETED`) 後のアラーム再登録検証

---

## 2. 全単体・統合テスト実行結果 (2026-09-29)
- **実行タスク**: `.\gradlew.bat testDebugUnitTest`
- **結果**: 37/37 全件 PASS (0 Failures / 0 Errors)
  - `CalendarSyncTest` (3/3 PASSED)
    - `testCalendarSyncManagerCrudAndDuplicateDetection` (カレンダーCRUDおよび重複検知)
    - `testCalendarCommandsExecutionAndUndo` (予定作成・更新・削除 Command と Undo/Redo 連動)
    - `testAiJsonToCalendarCommandsAndPolicy` (AI JSON からのコマンド変換とポリシーガード)
  - `SettingsAndPersonaTest` (2/2 PASSED)
  - `UiStateTest` (3/3 PASSED)
  - `AlarmIntegrationTest` (4/4 PASSED)
  - `AiStructuredActionTest` (7/7 PASSED)
  - `EncryptedDatabaseTest` (4/4 PASSED)
  - `PriorityCalculatorTest` (3/3 PASSED)
  - `UndoManagerTest` (11/11 PASSED)
