# AI執事・秘書アプリ (Butler Project)

個人特化型のローカル優先・高信頼性AI執事・秘書 Android アプリケーション。

---

## 1. 概要

本プロジェクトは、日々のToDo・スケジュール・リマインダー・外部連携を統合管理し、ユーザー専属のAI執事（またはメイド長）が行動支援を行うAndroidネイティブアプリです。

- **アーキテクチャ**: Clean Architecture / MVVM / Kotlin Native (Jetpack Compose / Coroutines / Flow)
- **セキュリティ & プライバシー**: 完全ローカル暗号化DB (SQLCipher + Android KeyStore)、プライベート設計
- **高信頼性実行**: Exact Alarm + BootReceiver による確実なリマインド・通知
- **AI連携**: ChatGPT API 連携 / 構造化Tool Calling / ペルソナ制御 / 操作ポリシーガード

---

## 2. ドキュメント体系 (Single Source of Truth)

リポジトリ内のドキュメントを公式の情報源とし、常に整合性を維持します。

| ドキュメント | 内容 |
| :--- | :--- |
| **[REQUIREMENTS.md](REQUIREMENTS.md)** | 最優先の要件定義書・受入基準 |
| **[OPERATION_SPEC.md](OPERATION_SPEC.md)** | AI開発運用仕様書 v2.0 (開発・レビュー体制) |
| **[DECISIONS.md](DECISIONS.md)** | アーキテクチャ決定レコード (ADR) |
| **[ARCHITECTURE.md](ARCHITECTURE.md)** | システム全体構造・レイヤー設計 |
| **[DATA_MODEL.md](DATA_MODEL.md)** | データベーススキーマ・エンティティ設計 |
| **[SECURITY.md](SECURITY.md)** | 暗号化・APIキー管理・権限セキュリティ仕様 |
| **[API_SPEC.md](API_SPEC.md)** | 内部モジュール & 外部API仕様 |
| **[TEST_PLAN.md](TEST_PLAN.md)** | 自動テスト方針・品質保証計画 |
| **[TASKS.md](TASKS.md)** | フェーズ別タスク・進捗管理 |
| **[CHANGELOG.md](CHANGELOG.md)** | 変更履歴 |

---

## 3. レビュー & 監査 (reviews/)

AI開発運用仕様書 v2.0 に準拠したレビューサイクルを管理します。

- **[reviews/REVIEW_REQUEST.md](reviews/REVIEW_REQUEST.md)**: 各Sprint完了時のレビュー依頼（SHA固定）
- **[reviews/CHATGPT_REVIEW.md](reviews/CHATGPT_REVIEW.md)**: ChatGPTによる通常コードレビュー結果
- **[reviews/CODEX_REVIEW.md](reviews/CODEX_REVIEW.md)**: Codexによる重点技術監査結果
- **[reviews/REVIEW_HISTORY.md](reviews/REVIEW_HISTORY.md)**: 過去のレビュー・監査記録ログ
