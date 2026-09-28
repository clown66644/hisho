# Codexレビュー・改修結果（2026-07-27）

## 1. 結論

優先1・優先2のGradle対象モジュールを再レビューし、High相当のManager間Undo/Redo競合とOpenAI通信境界の例外漏出を修正した。

**判定: 優先1コア層は統合可能。優先2は物理端末試験待ちで条件付き。優先3は未実装。**

## 2. 発見・修正した問題

### P1-012 — 複数Managerが同じ操作を二重Undoできる

- 発生条件: 同じ永続履歴を2つの`UndoManager`が復元し、同時にUndoする。
- 影響: DB外副作用や対象更新が二重実行される可能性。
- 重大度: High
- 修正: Undoトランザクション内で最新履歴を再取得し、既にUndo済みならCommandを再実行しない。
- テスト: `concurrentManagersUndoSameOperationOnlyOnce`

### P1-013 — 無効化済みRedo分岐を古いManagerが復活できる

- 発生条件: Manager Aが新規操作でRedo分岐を無効化した後、古いスタックを持つManager BがRedoする。
- 影響: 取り消した古い分岐のデータが復活し、履歴と状態が不整合になる。
- 重大度: High
- 修正: Redoトランザクション内で`isUndone`と`isRedoable`を再確認し、無効分岐は実行せずスタックから除去する。
- テスト: `staleManagerCannotReviveInvalidatedRedoBranch`

### P1-014 — 優先3の誤った完了記録

- 問題: `TASKS.md`等は構造化AI操作を完了扱いしていたが、Gradle対象`app/src`に記載された4ファイルが存在しなかった。
- 影響: 未実装機能を統合・リリース可能と誤認する。
- 重大度: High
- 修正: 優先3を未実装へ戻し、v0.1.4の完了記録を撤回した。

### P1-015 — 遅延した失敗履歴が新しい成功状態を上書きできる

- 発生条件: Manager AのCommandが失敗して補償中に、Manager Bが同じ操作IDを成功させる。
- 影響: Manager Aが後から保存する失敗履歴により、成功状態がFAILEDへ巻き戻される。
- 重大度: High
- 修正: 失敗記録の保存トランザクションで現在の状態・試行回数を比較し、より新しい変更があれば保存しない。
- テスト: `delayedFailureRecordCannotOverwriteNewerSuccess`

### P1-016 — 同一時刻の操作を再起動後に誤った順序でUndoする

- 発生条件: 複数操作が同じミリ秒のtimestampを持つ。
- 影響: UUID順で復元され、最後に実行した操作とは別の操作をUndoする。
- 重大度: High
- 修正: DBトランザクション内で単調増加する`operationOrder`を割り当て、復元順に使用。
- Migration: Room v2→v3で既存履歴をrowid順に移行。
- テスト: `restartRestoresSameTimestampCommandsInExecutionOrder`

### P1-017 — 失敗後の再試行が古い順序で復元される

- 発生条件: 操作A失敗、操作B成功、操作A再試行成功の順に処理する。
- 影響: 操作AがBより前として復元され、Undo順が逆転する。
- 重大度: High
- 修正: 再試行は成功した時点で新しい`operationOrder`を割り当てる。
- テスト: `successfulRetryGetsOrderOfSuccessfulExecution`

### P1-018 — 優先4の誤った完了記録

- 問題: 文書は本番アラーム統合を完了扱いしていたが、Gradle対象`app/src`にAlarm関連ソースが存在しない。
- 重大度: High
- 修正: 優先4を未実装へ戻し、該当CHANGELOG記録を撤回した。

### P1-019 — 資格情報プロバイダー例外が安全なエラー境界を迂回する

- 発生条件: API資格情報またはsafety identifierのプロバイダーが例外を送出する。
- 影響: `OpenAiClient`の安全なエラー応答を経由せず、呼び出し元のクラッシュや内部例外露出につながる。
- 重大度: High
- 修正: プロバイダー呼び出しを例外境界内へ移し、資格情報取得失敗を`CREDENTIAL_UNAVAILABLE`、識別子取得失敗を`INVALID_REQUEST`へ変換。送受信の一時バイトバッファも利用後に消去。
- テスト: `credentialProviderFailureIsConvertedToSafeError`、`safetyIdentifierProviderFailureStopsBeforeNetwork`

### P2-020 — 一時的なAI通信障害を制限付き再試行しない

- 発生条件: OpenAI通信で一時的なネットワーク例外またはHTTP 5xxが1回発生する。
- 影響: 復旧可能な一過性障害でも即時失敗し、対話機能の可用性が下がる。
- 重大度: Medium
- 修正: ネットワーク例外と5xxだけを250ms後に最大1回再試行。同じ`X-Client-Request-Id`を維持し、401/403と429は再試行対象外とした。
- テスト: `transientNetworkFailureRetriesOnceWithSameRequestId`、`serviceFailureRetriesOnlyOnce`、`authenticationAndRateLimitFailuresAreNotRetried`

## 3. テスト結果

- `testDebugUnitTest`: 46/46成功
- `connectedDebugAndroidTest`: 10/10成功（Android 15 AVD、優先3統合後に再実行）
- `lintDebug`: Error 0 / Warning 12
- `assembleDebug`: 成功
- APIキー・秘密鍵形式のソース検査: 一致0件

## 4. 残存リスク

- 物理端末のKeystore、実再起動、クラウドバックアップ、端末間転送は未検証。
- `CREATE_TODO`構造化AI操作コアは実装済み。確認必須操作の確認UI・確認済み証跡は未実装。
- 本番アラーム統合は未実装。

## 5. 統合判定

- 優先1コア層: 統合可能。
- 優先2: Android AVD検証済み、物理端末確認待ちのため条件付き。
- 優先3 `CREATE_TODO`構造化操作コア: 統合可能。
- フェーズ1全体: 優先4以降が未実装のため未完了。

## 6. 優先3再レビュー・改修

### P1-021 — 優先3実装がGradle対象外

- 旧実装は`app/app/src`に作成され、APK・テストへ含まれていなかった。
- 修正: 現行`app/src`へ`AiActionSchema`、`AiCommandConverter`、`OperationPolicyManager`、対応テストを統合。

### P1-022 — 未確認操作と保存なしCommandを成功扱いできる

- 修正: `CREATE_TODO`だけを自動実行可能とし、確認必須・禁止操作はCommand化前に拒否。DAOを必須依存にした。

### P1-023 — 構造化応答と操作IDを保証しない

- 修正: Responses APIの`text.format`にstrict JSON Schemaを設定し、要求・応答の操作ID一致を検証。
- 公式仕様: https://developers.openai.com/api/docs/guides/structured-outputs

### P1-024 — CREATE_TODOが既存行を上書きできる

- 修正: `@Upsert`ではなく競合時中止の`@Insert`を使用し、初回実行・Redoとも既存行を上書きしないようにした。

### P1-025 — 同一operationIdの再変換で対象IDが変わる

- 修正: `operationId`を入力とするSHA-256ベースの安定IDを生成し、同一操作の対象を固定した。

### P1-026 — 未分類の操作が自動実行可能になる

- 修正: `CREATE_TODO`だけを明示的に自動実行可能とし、それ以外の非禁止操作は確認必須へ倒すfail-closed設計にした。

### P1-027 — 未完了応答・refusalを操作候補として受理できる

- 修正: Responses APIの`status == completed`を構造化操作の必須条件とし、`refusal`内容を実行候補に使用しないようにした。
- 公式仕様: https://developers.openai.com/api/docs/guides/structured-outputs
