# CODEX_REVIEW.md - Codex コードレビュー & 安全性チェック記録

## レビューフォーマット規定
Codexによるレビュー指摘は以下の重大度分類および構造に従って本ファイルに記録します。

### 重大度規定
- **Critical**: 個人情報/APIキー漏えい, 認証回避, データ破損, 誤服薬指示, 暗号化不備
- **High**: 予定/ToDo誤登録, 二重実行, 通知不作動, Undo失敗, 権限過剰取得
- **Medium**: UI視認性, 軽度の不整合, 説明不足, パフォーマンス
- **Low**: 表記ゆれ, レイアウト微修正, リファクタリング

---

## [レビュー要請] フェーズ1：コア・ドメイン層、暗号化DB、ChatGPT APIクライアント
- **対象ファイル**:
  - `app/src/main/java/com/example/butler/domain/model/` ([TodoItem.kt](file:///d:/AI/%E7%A7%98%E6%9B%B8/app/src/main/java/com/example/butler/domain/model/TodoItem.kt), [OperationHistory.kt](file:///d:/AI/%E7%A7%98%E6%9B%B8/app/src/main/java/com/example/butler/domain/model/OperationHistory.kt))
  - `app/src/main/java/com/example/butler/domain/logic/` ([PriorityCalculator.kt](file:///d:/AI/%E7%A7%98%E6%9B%B8/app/src/main/java/com/example/butler/domain/logic/PriorityCalculator.kt), [UndoManager.kt](file:///d:/AI/%E7%A7%98%E6%9B%B8/app/src/main/java/com/example/butler/domain/logic/UndoManager.kt))
  - `app/src/main/java/com/example/butler/data/local/` ([AppDatabase.kt](file:///d:/AI/%E7%A7%98%E6%9B%B8/app/src/main/java/com/example/butler/data/local/AppDatabase.kt), [TodoEntity.kt](file:///d:/AI/%E7%A7%98%E6%9B%B8/app/src/main/java/com/example/butler/data/local/entity/TodoEntity.kt))
  - `app/src/main/java/com/example/butler/data/remote/` ([OpenAiClient.kt](file:///d:/AI/%E7%A7%98%E6%9B%B8/app/src/main/java/com/example/butler/data/remote/OpenAiClient.kt), [PersonaPrompts.kt](file:///d:/AI/%E7%A7%98%E6%9B%B8/app/src/main/java/com/example/butler/data/remote/PersonaPrompts.kt))
- **重点確認要請点**:
  1. `PriorityCalculator.kt`: 健康・安全および取り返しのつかない損失が常に「最優先」に判定される安全性が担保されているか。
  2. `UndoManager.kt`: 操作失敗時やロールバック時のデータ整合性・冪等性が保たれているか。
  3. `AppDatabase.kt`: SQLCipher による Room データベースパスフレーズ暗号化が正常に設定されているか。
  4. `OpenAiClient.kt`: APIキーや会話内容が不意にログやエラー文に露出しない設計になっているか。
