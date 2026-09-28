# CHANGELOG.md - 変更履歴

## [v0.2.1] - 2026-09-29
### Added
- **2-2：Google カレンダー外部連携モジュールの実装**:
  - `CalendarSyncManager` による端末カレンダー (`CalendarContract`) 同期、権限ガード、重複予定検知 (`detectDuplicates`)。
  - カレンダー操作用 Command (`CreateEventCommand`, `UpdateEventCommand`, `DeleteEventCommand`) と Undo / Redo 連動。
  - `OperationPolicyManager` に `CREATE_EVENT` を追加、変更・削除 (`UPDATE_EVENT`, `DELETE_EVENT`) の確認必須ガード。
  - `AiCommandConverter` における `CREATE_EVENT`, `UPDATE_EVENT`, `DELETE_EVENT` パースおよび不完全パラメータ時の `GenericConfirmationCommand` 安全フォールバック。
  - カレンダー同期・操作・ポリシーの単体テスト作成 (`CalendarSyncTest.kt`、全 37 件単体テスト PASSED、Lint Error 0 件)。

## [v0.2.0] - 2026-07-27
### Added
- **2-1：設定暗号化 & マルチペルソナ統合モジュールの実装**:
  - KeyStore 連携 `EncryptedSharedPreferences` による OpenAI API キーおよびユーザー設定の安全なローカル暗号化保存 (`SettingsManager.kt`)。
  - AI マルチペルソナプロンプトエンジンの拡張 (`PersonaType.kt`, `PersonaPrompts.kt`: 執事長, 秘書, コーチ, メイド長)。
  - ペルソナ変更の Undo / Redo 対応 Command (`ChangePersonaCommand.kt`)。
  - メイン UI におけるペルソナ選択 RadioGroup バインディングとヘッダータイトルのリアルタイム更新 (`MainActivity.kt`, `activity_main.xml`)。
  - 暗号化設定およびペルソナ切替/Undo 復元の単体テスト作成 (`SettingsAndPersonaTest.kt`、全 34 件単体テスト PASSED)。

## [v0.1.6] - 2026-07-27
### Added
- **1-6：UIレイアウト & カード操作の実装 (フェーズ1完成)**
