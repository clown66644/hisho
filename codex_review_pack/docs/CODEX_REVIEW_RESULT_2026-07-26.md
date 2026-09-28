# Codexレビュー・改修結果（2026-07-26）

## 1. 対象と結論

フェーズ1の優先順位、Undo/Redo、Room + SQLCipher、APIキー保存、OpenAI API、ペルソナ制御を対象とした。

**判定: コア層のみ条件付き統合可能。フェーズ1全体およびmainへの統合は不可。**

対象コードの既知Critical／High問題は改修済みで、単体テスト15件、Debug APK、Android Lintは成功した。
実機暗号化試験、Keystore消失試験、外部操作の統合試験が残るため、本番安定版とは判定しない。

## 2. 発見した問題と対応

### P1-001 — 安全タスクを固定Lowへ降格できる

- **問題の内容:** 固定優先度を安全判定より先に返していた。
- **発生条件:** `isHealthOrSafety`または`irretrievableLoss`と固定`LOW`を同時指定。
- **影響範囲:** 服薬確認、安全行動、取消不能期限等の主要ToDo表示。
- **重大度:** High
- **推奨修正:** 安全・不可逆損失を固定値より先に強制昇格する。
- **修正対象:** `PriorityCalculator.kt`
- **追加テスト:** 安全+固定Low、不可逆損失+固定Low、旧版の範囲外係数。
- **対応:** 修正済み。スコアも0～100へ制限した。

### P1-002 — Undo失敗時に操作を再試行できない

- **問題の内容:** `undo()`より先にスタックから削除し、失敗・例外時にも復元しなかった。Redoもなかった。
- **発生条件:** DB更新失敗、通信・ストレージ例外、複数回Undo。
- **影響範囲:** 取り消し、復元、操作履歴、データ整合性。
- **重大度:** High
- **推奨修正:** 成功後にだけスタックを移動し、失敗時は保持。同一操作IDを冪等化する。
- **修正対象:** `UndoManager.kt`
- **追加テスト:** 失敗後再試行、例外、Redo、重複ID、失敗履歴。
- **対応:** 修正済み。永続DBの原子性は各Commandのトランザクション責務として明記した。

### P1-003 — DB暗号鍵の生成・保管・消失時動作が未定義

- **問題の内容:** 任意`ByteArray`を外部から受けるだけで、弱い鍵や固定鍵を拒否できず、Keystore保護もなかった。
- **発生条件:** 呼出側が固定値を渡す、バックアップ復元でKeystore鍵だけ失う、保存暗号文が破損。
- **影響範囲:** 全ローカルデータの暗号化と復旧。
- **重大度:** Critical
- **推奨修正:** 32バイト乱数を生成し、非エクスポートKeystore鍵でAES-GCM暗号化する。鍵消失時は上書きしない。
- **修正対象:** `DatabasePassphraseProvider.kt`, `AppDatabase.kt`, `AndroidManifest.xml`
- **追加テスト:** 実機で初回作成、再起動、暗号文破損、Keystore鍵消失、バックアップ除外。
- **対応:** 実装修正済み。実機確認待ち。

### P1-004 — RoomのREPLACEが既存行を削除して再作成する

- **問題の内容:** 競合時`REPLACE`を使い、将来の外部キー・関連履歴を失う設計だった。
- **発生条件:** 同じIDのToDoを保存。
- **影響範囲:** ToDo、将来のサブタスク、履歴、関連付け。
- **重大度:** High
- **推奨修正:** Roomの`@Upsert`を使い、更新・削除件数を確認可能にする。
- **修正対象:** `TodoDao.kt`
- **追加テスト:** 同一ID更新、存在しない更新・削除、関連データ保持。
- **対応:** 修正済み。DB統合試験は未実施。

### P1-005 — API通信が無期限に待機し、応答サイズも無制限

- **問題の内容:** 接続・読取タイムアウト、HTTPS検証、応答上限、エラー種別がなかった。
- **発生条件:** 通信停止、巨大応答、401/429/5xx、壊れたJSON。
- **影響範囲:** チャット停止、メモリ、エラー復旧、機密情報表示。
- **重大度:** High
- **推奨修正:** タイムアウト、HTTPS限定、応答上限、一般化エラー、テスト可能なTransportを導入する。
- **修正対象:** `OpenAiClient.kt`, `PersonaPrompts.kt`
- **追加テスト:** 成功、認証失敗、例外、匿名識別子不正、機密値非露出。
- **対応:** Responses APIへ移行し、`store=false`、`safety_identifier`、15秒/60秒、2MiB上限を追加。
  モデルは既存の費用・応答特性を変えないため`gpt-4o-mini`を維持した。

### P1-006 — APIキー安全保存の実体がない

- **問題の内容:** 文書では完了扱いだったが、本体に保存クラスがなかった。
- **発生条件:** UIからAPIキーを保存する工程。
- **影響範囲:** API認証情報。
- **重大度:** Critical
- **推奨修正:** Keystore連携AES-GCM暗号化ストレージに限定し、バックアップ・ログ・列挙を禁止する。
- **修正対象:** `ApiKeyStore.kt`, `AndroidManifest.xml`
- **追加テスト:** 保存・読出・削除、端末ロック変更、鍵消失、バックアップ除外。
- **対応:** 実装修正済み。実機確認待ち。

### P1-007 — フェーズ1本体を再現ビルドできない

- **問題の内容:** ルート設定、Wrapper、Manifest、ProGuard、Roomスキーマ出力先がなかった。
- **発生条件:** クリーン環境、CI、APK生成、単体テスト。
- **影響範囲:** 本体全体とレビュー証跡。
- **重大度:** High
- **推奨修正:** 完全なGradle骨格を追加し、JDK/Gradleを固定する。
- **修正対象:** ルートGradleファイル、`app/build.gradle.kts`、Manifest。
- **追加テスト:** `testDebugUnitTest`, `assembleDebug`, `lintDebug`。
- **対応:** Gradle 8.2.1 WrapperとJDK 17条件を確立し、各タスク成功を確認した。

## 3. 修正しなかった問題

- **SQLCipher新ライブラリへの移行:** 既存DB互換、暗号移行、Room組合せの評価が必要なため別ADRとする。
- **targetSdkおよび依存の一括更新:** Android権限・バックグラウンド挙動を変えるため、フェーズ0実機回帰試験と一緒に行う。
- **KAPTからKSPへの移行:** 性能改善であり安全性修正ではないため保留。
- **構造化AI操作:** JSON Schema、承認ゲート、操作ID、履歴との統合設計が未完成のため、自然文回答のみとした。

## 4. 実行したテスト

- `testDebugUnitTest`: 15件成功（優先順位6、Undo/Redo 5、API 4）。
- `assembleDebug`: 成功。`app-debug.apk`生成。
- `lintDebug`: 成功、0 errors / 11 warnings。警告はtargetSdk、依存更新、KSP移行に関する既知項目。
- APIキー形式・Bearer値のソース検索。
- RoomスキーマJSON生成確認。

日本語を含む元パスではGradle 8.2.1のJava引数ファイルがテストクラスパスを誤認したため、
同一フォルダへの英数字ジャンクション経由で標準Gradleテストを成功させた。明示クラスパスのJUnit実行でも
同じ15件が成功しており、コードではなく実行パス依存の問題と判定した。

## 5. 残存リスク

- Keystore、EncryptedSharedPreferences、SQLCipherの実機暗号化・鍵消失試験が未実施（High）。
- UndoManagerはメモリ内スタックであり、アプリ再起動後のUndo/Redo永続化は未実装（High）。
- 各CommandによるDBトランザクションと外部API補償処理は未実装（High）。
- `@Upsert`のSQLCipher実機統合試験が未実施（High）。
- OpenAIの構造化操作出力、承認ゲート、重複実行防止は未実装（High）。
- SQLCipher旧AndroidライブラリとtargetSdk 34の更新計画が必要（Medium）。
- 日本語パスからGradle標準テストを直接実行できない環境制約がある（Medium）。

## 6. 統合可否

レビュー対象のコア層は条件付き統合可能。Criticalの既知未修正問題はない。

ただし実機暗号化、永続Undo、構造化AI操作、外部操作統合が未完了であるため、フェーズ1全体および
`main`への統合は禁止する。

## 7. 参照した一次資料

- OpenAI Model guidance: https://developers.openai.com/api/docs/guides/latest-model
- Android Keystore: https://developer.android.com/privacy-and-security/keystore
- Android backup security: https://developer.android.com/privacy-and-security/risks/backup-best-practices
- SQLCipher Room integration: https://github.com/sqlcipher/android-database-sqlcipher
- SQLCipher後継Androidライブラリ: https://github.com/sqlcipher/sqlcipher-android
