# CHANGELOG.md - 変更履歴

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
