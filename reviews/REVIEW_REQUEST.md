# Review Request

## Sprint
Sprint 1-4 (Google カレンダー外部連携 & アラーム連携)

## Branch
feature/calendar-alarm-integration

## Review Target Commit
<commit SHA>

## 目的
フェーズ1 タスク1-4「Google カレンダー外部連携モジュールおよび Exact Alarm リマインダー連携」の実装・テスト。

## 変更内容
- `CalendarSyncManager.kt`: Google カレンダーの取得・作成・更新・削除・重複検知ガード (`P2-002`) 実装
- `CalendarCommands.kt`: カレンダー操作の Command 化および UndoManager との連携
- `OperationPolicyManager.kt`: カレンダー削除・更新操作の Confirmation ガード適用
- `CalendarSyncTest.kt`: 境界値・異常日時ガードおよび各カレンダー操作の単体テスト追加

## 変更ファイル
- `app/src/main/java/com/example/butler/data/local/CalendarSyncManager.kt`
- `app/src/main/java/com/example/butler/domain/logic/CalendarCommands.kt`
- `app/src/main/java/com/example/butler/domain/logic/OperationPolicyManager.kt`
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
- カレンダー同期時の重複予定検知ロジックの安全性
- Undo 実行時のカレンダー外部状態との整合性維持

## データベース変更
無

## 権限変更
有 (`android.permission.READ_CALENDAR`, `android.permission.WRITE_CALENDAR`)

## 外部API変更
無 (Android標準 CalendarContract Provider を使用)

## セキュリティ影響
有 (端末カレンダー情報へのアクセス・変更権限)
