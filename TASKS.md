# TASKS.md - タスク管理・進捗状況

## 現在のフェーズ
**フェーズ1：最初の試作版 (Kotlin Native Android)**

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

### フェーズ1：最初の試作版 (着手中)
- [x] **1-1. コア・ドメイン層構築**
  - [x] ToDo / 予定 / 操作履歴 エンティティとモデル実装 (`TodoItem.kt`, `CalendarEvent.kt`, `OperationHistory.kt`)
  - [x] 優先順位計算エンジン (`PriorityCalculator.kt`, 4段階表示 & 数値評価)
  - [x] Commandパターンによる Undo (取り消し) / Redo マネージャー (`UndoManager.kt`)
  - [x] ドメイン層の全単体テストクリア (`PriorityCalculatorTest.kt`, `UndoManagerTest.kt`)
- [x] **1-2. ローカル暗号化DB & セキュリティ構築**
  - [x] Room + SQLCipher 暗号化データベースの実装 (`AppDatabase.kt`, `TodoEntity.kt`, `TodoDao.kt`)
  - [x] KeyStore / EncryptedSharedPreferences による API キー & 設定安全保存基盤
- [x] **1-3. ChatGPT API & ペルソナ制御**
  - [x] OpenAI API クライアント (`OpenAiClient.kt`)
  - [x] 執事長 / メイド長 システムプロンプト制御 & 切り替えモジュール (`PersonaPrompts.kt`)
- [ ] **1-4. アラーム・通知・カレンダー機能連携**
  - [ ] Exact Alarm Scheduler & Boot Receiver の本番組み込み
  - [ ] Google Calendar API 連携モジュール
- [ ] **1-5. UIレイアウト & カード操作**
  - [ ] ホーム・チャット画面 (上部: 今日の状況, 下部: 対話)
  - [ ] ToDo・予定・通知実行結果カード (完了・延期・取り消し操作)
