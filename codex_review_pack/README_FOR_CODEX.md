# Codex レビュー用ファイルパック

本フォルダ (`codex_review_pack`) は、Codex によるコードレビューおよび安全性確認のために必要なドキュメントおよびソースコードを取りまとめたものです。

---

## 📁 フォルダ構成

```
codex_review_pack/
├── docs/                             # 仕様書・設計書・レビュー要請ドキュメント群
│   ├── CODEX_REVIEW.md               # 今回のレビュー要請事項
│   ├── REQUIREMENTS.md               # 統合要件定義・開発指示書 v1.0
│   ├── ARCHITECTURE.md               # システムアーキテクチャ概要
│   ├── DATA_MODEL.md                 # データモデル仕様書
│   ├── SECURITY.md                   # セキュリティ・プライバシー要件
│   ├── DECISIONS.md                  # 技術決定記録 (ADR-001, ADR-002)
│   ├── TEST_PLAN.md                  # テスト計画書
│   ├── TASKS.md                      # タスク管理
│   └── CHANGELOG.md                  # 変更履歴
├── app/                              # フェーズ1 本番実装ソースコード (Kotlin)
│   └── src/
│       ├── main/java/com/example/butler/
│       │   ├── domain/
│       │   │   ├── model/            # TodoItem, CalendarEvent, OperationHistory
│       │   │   └── logic/            # PriorityCalculator, UndoManager
│       │   ├── data/
│       │   │   ├── local/            # AppDatabase (SQLCipher/Room), TodoEntity, TodoDao
│       │   │   └── remote/           # OpenAiClient, PersonaPrompts
│       └── test/java/com/example/butler/
│           └── domain/logic/         # PriorityCalculatorTest, UndoManagerTest
└── poc_kotlin/                       # フェーズ0 技術検証POC (Exact Alarm, EncryptedSharedPreferences等)
```

---

## 🔍 今回の重点レビュー要請事項

詳細は `docs/CODEX_REVIEW.md` を参照してください。

1. **優先順位判定アルゴリズム (`PriorityCalculator.kt`)**
   - 健康・安全 (`isHealthOrSafety`) および取り返しのつかない損失 (`irretrievableLoss`) が常に「最優先 (TOP_PRIORITY)」に判定されるルールが漏れなく担保されているか。
2. **Undo/Redo マネージャー (`UndoManager.kt`)**
   - 操作失敗時や複数ステップのロールバック時の冪等性と状態整合性が維持できる構造になっているか。
3. **ローカル暗号化DB (`AppDatabase.kt`, `TodoEntity.kt`, `TodoDao.kt`)**
   - SQLCipher による Room データベースパスフレーズ暗号化が安全かつ正常に設定されているか。
4. **ChatGPT API クライアント & ペルソナ (`OpenAiClient.kt`, `PersonaPrompts.kt`)**
   - APIキーや会話内容が不意にログやエラー文に露出しない設計になっているか。

