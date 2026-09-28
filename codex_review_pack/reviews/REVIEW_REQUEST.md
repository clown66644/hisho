# Review Request

## Sprint
Sprint 2-2 (Google カレンダー外部連携モジュール & 重複検知ガード)

## Branch
develop

## Review Target Commit
8f366d1c97700b53bbb50f6eddf0313a215a2645

## 目的
フェーズ2 タスク2-2「Google カレンダー外部連携モジュール (`CalendarSyncManager.kt`)、重複予定検知エンジン、Command Undo/Redo、ポリシー強制」の実装・テスト。

## 変更内容
- `CalendarSyncManager.kt`: Google カレンダー (`CalendarContract.Events`) の取得・作成・更新・削除・重複検知ガード (`P2-002`) 実装
- `CalendarCommands.kt`: カレンダー操作 (`CreateEventCommand`, `UpdateEventCommand`, `DeleteEventCommand`) の Command 化および UndoManager との連携
- `OperationPolicyManager.kt`: カレンダー削除・更新操作の `CONFIRMATION_REQUIRED` ガード、作成操作の `AUTO_EXECUTABLE` 設定
- `AiCommandConverter.kt`: `CREATE_EVENT`, `UPDATE_EVENT`, `DELETE_EVENT` の AI JSON パースおよび部分パラメータ時の `GenericConfirmationCommand` 安全フォールバック
- `AndroidManifest.xml`: `READ_CALENDAR` / `WRITE_CALENDAR` パーミッション追加
- `CalendarSyncTest.kt`: 境界値・異常期間ガードおよび各カレンダー操作・重複検知の単体テスト追加

## 変更ファイル
- `app/src/main/java/com/example/butler/data/local/CalendarSyncManager.kt`
- `app/src/main/java/com/example/butler/domain/logic/CalendarCommands.kt`
- `app/src/main/java/com/example/butler/domain/logic/OperationPolicyManager.kt`
- `app/src/main/java/com/example/butler/domain/logic/AiCommandConverter.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/test/java/com/example/butler/data/local/CalendarSyncTest.kt`

## 関連要件
REQUIREMENTS.md:
- 3.2 カレンダー連携
- 4.2 操作の取り消し (Undo/Redo)
- 4.3 高リスク操作の確認ダイアログ

## テスト結果
- Build: SUCCESS (`assembleDebug`)
- Lint: SUCCESS (`lintDebug` - 0 errors)
- Unit tests: 38件中38件成功 (`testDebugUnitTest`)
- Integration tests: PASS
- Manual test: 実機エミュレータにてカレンダープロバイダ連携確認

## 未実行テスト
- 実端末でのGoogleアカウント複数切り替えテスト (実機確認待ち)

## 既知の問題
- なし

## 重点確認してほしい内容
- カレンダー同期時の重複予定検知ロジックの安全性 (`detectDuplicates`)
- Undo 実行時のカレンダー外部状態との整合性維持（作成した予定の安全な削除・更新前の復元）

## データベース変更
無

## 権限変更
有 (`android.permission.READ_CALENDAR`, `android.permission.WRITE_CALENDAR`)

## 外部API変更
無 (Android標準 CalendarContract Provider を使用)

## セキュリティ影響
有 (端末カレンダー情報へのアクセス・変更権限)
