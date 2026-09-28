# CHANGELOG.md - 変更履歴

## [v0.1.1] - 2026-07-23
### Added
- フェーズ1コアモジュールの実装:
  - `TodoItem`, `CalendarEvent`, `OperationHistory` データモデル
  - 優先順位計算エンジン `PriorityCalculator` (健康・安全、締切、金額、仕事等の優先要素判定)
  - Command パターンによる Undo/Redo マネージャー `UndoManager`
  - SQLCipher 暗号化 Room DB (`AppDatabase`, `TodoEntity`, `TodoDao`)
  - OpenAI API クライアント `OpenAiClient` および 執事長/メイド長切り替えモジュール `PersonaPrompts`
  - ドメインロジックの単体テスト (`PriorityCalculatorTest`, `UndoManagerTest`)

## [v0.1.0] - 2026-07-23
### Added
- Codexレビュー結果の確認および指摘事項（権限事前確認、UUIDによるPendingIntent個別識別、暗号化保存、エラー判定）の統合・承認。
- 技術選定 ADR-002 の確定: 初期版の開発言語として **Kotlin (Native Android)** を正式採択。
- フェーズ1（最初の試作版）の開発開始。

## [v0.0.2] - 2026-07-22
### Added
- Kotlin 最小検証 POC モジュール (`poc_kotlin/`) の構築（ExactAlarmScheduler, BootReceiver, AlarmReceiver, SecureStorage）
- Flutter 最小検証 POC モジュール (`poc_flutter/`) の構築（AlarmService, SecureStorageService, HomeScreen）

## [v0.0.1] - 2026-07-22
### Added
- `REQUIREMENTS.md` (統合要件定義・開発指示書 v1.0) の保存
- プロジェクト初期ドキュメント群 (`ARCHITECTURE.md`, `DATA_MODEL.md`, `SECURITY.md`, `API_SPEC.md`, `TEST_PLAN.md`, `TASKS.md`, `DECISIONS.md`, `CODEX_REVIEW.md`) の作成
- フェーズ0：Kotlin vs Flutter 技術検証計画の策定
