# DECISIONS.md - 技術決定・アーキテクチャ判定記録

## ADR-001: 初期要件定義およびドキュメント管理方針の確定
- **日時**: 2026-07-22
- **ステータス**: 承認済
- **文脈**: 「統合要件定義・開発指示書 v1.0」を全開発のマスターリファレンスとしてリポジトリ直下に `REQUIREMENTS.md` として配置。
- **決定事項**:
  - 本書（REQUIREMENTS.md）の規定を最優先とし、未確認での大幅な仕様変更を禁止。
  - 開発プロセスは `OPERATION_SPEC.md` (v2.0) に準拠し、AntiGravity（主設計・主実装）、ChatGPT（要件管理・通常レビュー）、Codex（重点技術監査）の役割分担を徹底する。

---

## ADR-002: 技術選定（Kotlin Native Androidの採用）
- **日時**: 2026-07-23
- **ステータス**: 承認済（確定）
- **文脈**:
  - フェーズ0にて Kotlin POC と Flutter POC の検証を実施。
  - Codexレビュー（CR-001, CR-004等）により、Flutterで `ExactAlarmScheduler` や端末再起動時復旧 (`RECEIVE_BOOT_COMPLETED`) を実現するためには背景プロセスの制約や MethodChannel / ネイティブホストコード依存が大きく、Doze Mode やバックグラウンド制限に対する安定性の担保が困難であることが判明。
  - 要件定義書 6章 の「Flutterで重要なAndroid機能の安定性を確保できない場合は、初期版をKotlinで開発する」という方針を適用。
- **決定事項**:
  - **初期版（フェーズ1〜フェーズ4）の開発言語として Kotlin (Native Android) を採用する。**
  - アーキテクチャは Android Jetpack (AppCompat / XML Layout / RecyclerView / Room / WorkManager / Security Crypto) + Kotlin Coroutines / Flow によるクリーンアーキテクチャを適用する。
  - ドメインロジック（優先順位計算、対話パース、Undoコマンド履歴）は Android 依存から分離し、将来の Windows/iPhone 展開（フェーズ5）での共有・移植性を維持する。

---

## ADR-003: AI開発運用体制 v2.0 (GitHubハブ・ChatGPT通常レビュー・Codex重点監査) への移行
- **日時**: 2026-09-29
- **ステータス**: 承認済
- **決定事項**:
  - 開発運用体制を `OPERATION_SPEC.md` (v2.0) に正式統一する。
  - **AntiGravity**: 主任開発者（設計・実装・テスト・ビルド確認・修正）
  - **ChatGPT**: 要件管理・通常コードレビュー
  - **Codex**: 高リスク技術監査（Room Migration, SQLCipher, Calendar Undo, 冪等性, Boot Recovery, 外部操作補償等）
  - **人間**: 最終判断者
  - 各Sprint完了時に実commit SHAを固定し、`reviews/REVIEW_REQUEST.md` 経由でレビューを依頼する。
