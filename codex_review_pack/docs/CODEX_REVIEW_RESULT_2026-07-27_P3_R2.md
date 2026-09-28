# Codexレビュー・改修結果（フェーズ1 優先3・再レビュー2）

## 1. 結論

**判定: `CREATE_TODO`構造化AI操作コアは、今回の指摘改修後に次段階へ進行可能。**

## 2. 発見・修正した問題

### High — CREATEが既存ToDoを上書きできる

- 原因: 新規作成CommandがRoomの`@Upsert`を使用していた。
- 修正: 競合時中止の`@Insert(onConflict = ABORT)`へ変更。Redoも同じ挿入契約を使用する。
- 回帰確認: 衝突時は操作をFAILEDとし、既存ToDoの内容を保持する。

### High — 同一operationIdの対象IDが変動する

- 原因: JSONからCommandへ変換するたびにランダムIDを生成していた。
- 修正: `operationId`からSHA-256ベースの安定ToDo IDを生成する。
- 回帰確認: 同じoperationIdから生成した複数Commandの対象IDが一致し、ToDoは1件だけ作成される。

### High — 将来追加した操作が暗黙に自動実行可能になる

- 原因: 禁止・確認必須リストのどちらにもない操作を自動実行可能としていた。
- 修正: 自動実行を`CREATE_TODO`へ明示限定し、その他は安全側の確認必須とする。

### High — Responses APIの未完了応答・refusalを受理できる

- 原因: `output_text`だけを抽出し、トップレベル`status`と`refusal`を検証していなかった。
- 修正: 構造化操作では`status == completed`を必須化し、`refusal`を検出した場合は`INVALID_RESPONSE`として拒否する。
- 根拠: OpenAI Structured Outputsガイドの、`status`、`incomplete_details`、`refusal`を分岐処理する指針。

## 3. 検証結果

- `testDebugUnitTest`: 46/46 PASS
  - `OpenAiClientTest`: 14
  - `AiStructuredActionTest`: 7
  - `PriorityCalculatorTest`: 6
  - `UndoManagerTest`: 19
- `connectedDebugAndroidTest`: 10/10 PASS（Android 15 AVD）
- `lintDebug`: Error 0
- `assembleDebug`: SUCCESS

## 4. 残存事項

- OpenAI実サービスを使用するE2E試験は未実施。
- 確認必須操作の確認UI・確認済み証跡は未実装で、現在は安全側に拒否する。
- 物理端末でのKeystore・再起動・バックアップ関連試験は未実施。

