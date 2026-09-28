# TEST_PLAN.md - テスト計画書

## 2026-09-29 再監査の検証規則（現在有効）

- 正式ルートは `D:\AI\秘書\codex_review_pack`。対象タスクは `:app:testDebugUnitTest` / `:app:lintDebug` / `:app:assembleDebug`。
- 日本語パスで生成エラーが起きた場合は、`app/app`・`build`・`.gradle` を除外した英数字パスの新しい作業コピーで検証する。ソース一致を確認し、ルートを記録する。
- 正式版の単体テストは `OpenAiClientTest` / `AiStructuredActionTest` / `PriorityCalculatorTest` / `UndoManagerTest` / `MainUiControllerTest`。別ツリーの37/38件を合格証拠にしない。
- 追加回帰: FAILED/PARTIAL_SUCCESSのID再利用、対象・操作種別・前後JSONの競合、トランザクション内の再照合、競合時の履歴不変、同一要求の再試行。
- カレンダー統合時は、実Providerからの変更前取得、取得失敗時の拒否、承認、重複検知、再起動後Undo、削除Undo後の新IDによるRedo、カレンダーID・繰り返し・権限拒否を検証する。
- 最新の実行結果は [改修結果](CODEX_REVIEW_RESULT_2026-09-29_REMEDIATION.md) を参照。Android端末テストは単体テストと分けて報告する。

以下は訂正前の計画・結果の履歴です。全件PASSという記述は正式版の今回実行結果ではありません。

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
