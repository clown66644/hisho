# TASKS.md - タスク管理・進捗状況

## 2026-09-29 訂正後の進捗（こちらが有効）

- [x] 正式ビルド対象を `codex_review_pack/app/src` に限定。
- [x] 暗号化DB、CREATE_TODO、基本UI、履歴・Undo/Redoの実装あり（全体完成ではない）。
- [x] 失敗・部分成功を含む操作IDの内容照合を改修、回帰テスト追加。
- [ ] 自然言語対話と操作UIの接続、ToDo変更・完了・延期等。
- [ ] 正式版への設定・ペルソナ切替統合。
- [ ] 正式版への本番アラーム・タイマー・通知統合。
- [ ] 正式版へのカレンダー統合。旧コードの重複登録・不正なUndo・空操作成功を修正してから個別に移植。
- [ ] 受け入れ例「明日の15時に病院、1時間前通知」の端末検証。
- [ ] フェーズ1完成・安定版統合・次段階への移行。

最新証拠は [改修結果](CODEX_REVIEW_RESULT_2026-09-29_REMEDIATION.md)。以下の完了チェックは訂正前の履歴であり、現在の完了判定に使用しないでください。

## 現在のフェーズ
**フェーズ2：実機能の追加・発展 (Kotlin Native Android)**

---

## タスク一覧

### フェーズ0：技術検証 (完了)
- [x] リポジトリ初期構成確認および `REQUIREMENTS.md` の保存
- [x] 初期設計ドキュメント群の構築 (`ARCHITECTURE.md`, `DATA_MODEL.md`, `SECURITY.md` 等)
- [x] 技術検証計画 (Kotlin vs Flutter) および評価マトリクス策定
- [x] Kotlin POC アプリ構築 (アラーム, 通知, 暗号化, バックグラウンド復元検証)
- [x] Flutter POC アプリ構築と限界評価 (Exact Alarmプラグイン制限・ネイティブコード依存)
- [x] Codexレビュー改修内容の反映および評価完了
- [x] 初期版の開発言語として Kotlin (Native Android) を採用決定 (`DECISIONS.md` ADR-002)

---

### フェーズ1：最初の試作版 (完了)
- [x] **1-1. コア・ドメイン層構築** (優先順位計算, Undo/Redo)
- [x] **1-2. 永続的な操作履歴とUndo/Redo (優先1完了)** (Room+SQLCipher, トランザクション, 冪等性)
- [x] **1-3. 暗号化DB・鍵管理の実機統合試験 (優先2完了)** (KeyStore AES-256-GCM, バックアップ除外)
- [x] **1-4. 構造化AI操作 (優先3完了)** (Structured Outputs, ポリシーカーネル)
- [x] **1-5. 本番アラーム統合 (優先4完了)** (Doze Mode 回避アラーム, UUID 一意化, 再起動復元)
- [x] **1-6. UIレイアウト & カード操作 (画面統合完了)** (ダークUI, ToDo/確認カード, Undo同期)

---

### フェーズ2：実機能の追加・発展 (進行中)
- [x] **2-1. 設定暗号化 & マルチペルソナ統合モジュール (完了)**
  - [x] KeyStore 連携 `EncryptedSharedPreferences` 管理 (`SettingsManager.kt`)
  - [x] AI マルチペルソナエンジン拡張 (`PersonaType.kt`, `PersonaPrompts.kt`: 執事長, 秘書, コーチ, メイド長)
  - [x] Undo 可能型ペルソナ変更コマンド (`ChangePersonaCommand.kt`)
  - [x] メイン画面ペルソナ切替 RadioGroup 制御 & ヘッダー動的更新 (`MainActivity.kt`, `activity_main.xml`)
  - [x] 単体テスト追加・全 34 件 PASSED (`SettingsAndPersonaTest.kt`)
- [x] **2-2. Google カレンダー外部連携モジュール (完了)**
  - [x] Device Calendar API / CalendarContract 同期マネージャー (`CalendarSyncManager.kt`)
  - [x] 権限チェック (READ_CALENDAR / WRITE_CALENDAR) および安全フォールバック設計
  - [x] 重複予定・時間帯重複の検出エンジン (`detectDuplicates`)
  - [x] カレンダー操作 Command (`CreateEventCommand.kt`, `UpdateEventCommand.kt`, `DeleteEventCommand.kt`)
  - [x] ポリシーカーネル連動 (変更・削除の `CONFIRMATION_REQUIRED` 判定、新規作成の `AUTO_EXECUTABLE`)
  - [x] AI 構造化操作パースおよび不完全パラメータ時の `GenericConfirmationCommand` 安全フォールバック
  - [x] 単体テスト追加・全 37 件 PASSED (`CalendarSyncTest.kt`)
- [ ] **2-3. バックグラウンド同期 & 通知カスタマイズ**
