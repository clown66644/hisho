# 優先2 実装・検証結果（2026-07-26）

## 1. 実装した内容

- Android統合テスト`EncryptedStorageIntegrationTest`を10件追加した。
- DB鍵とAPIキーの保存名・Keystoreエイリアスを`internal`定数へ集約した。
- SQLCipher、Keystore、APIキー保存、バックアップ除外を自動検証可能にした。

## 2. 発見した問題

- 優先2について、実際のAndroid上で動く統合テストが存在しなかった。
- 文書では実機統合試験完了となっていたが、物理端末の実行証跡がなかった。
- ビルド対象外の旧テストにAPIキー形式のサンプル文字列が残っていた。
- 複数Managerによる同一操作IDの同時実行で外部副作用が重複する競合窓があった。
- 既存DBだけ残って暗号化パスフレーズ設定が消えた場合、新しい鍵データを保存する可能性があった。
- APIキー保存時に不要な不変`String`を生成していた。

## 3. 修正した問題

- Android 15 AVD上で暗号化・鍵管理統合テストを実行した。
- 物理端末未検証項目を完了扱いから除外した。
- APIキー形式の旧サンプルを非キー形式の合成マーカーへ変更した。
- 操作IDをDBトランザクション内で再確認し、複数Manager間でも二重実行を防止した。
- 既存DBと鍵設定の不整合時は新規生成せず、復旧エラーにした。
- APIキーを`CharArray`から直接UTF-8変換し、入力・一時バッファをゼロクリアした。
- 暗号化v1 DBからv2への実Migrationテストを追加した。

## 4. 修正しなかった問題と理由

- SQLCipher後継ライブラリへの移行は既存DB互換性評価が必要なため保留。
- 物理端末の実再起動、クラウドバックアップ復元、端末間転送は必要環境がないため未実施。
- SDK XML、依存更新、KSP移行等のLint Warning 12件は今回の安全性試験範囲外。

## 5. 変更ファイル

- `app/build.gradle.kts`
- `app/src/main/java/com/example/butler/data/local/DatabasePassphraseProvider.kt`
- `app/src/main/java/com/example/butler/data/local/ApiKeyStore.kt`
- `app/src/androidTest/java/com/example/butler/data/local/EncryptedStorageIntegrationTest.kt`
- `app/app/src/test/java/com/example/butler/data/local/EncryptedDatabaseTest.kt`
- `docs/TASKS.md`
- `docs/TEST_PLAN.md`
- `docs/CHANGELOG.md`

## 6. 実行したテストと件数

- Android統合テスト: 10件
- 単体テスト: 24件
- 合計: 34件

## 7. テスト結果

- `connectedDebugAndroidTest`: 10/10成功
- `testDebugUnitTest`: 24/24成功
- `lintDebug`: 成功、Error 0 / Warning 12
- `assembleDebug`: 成功
- Logcat機密マーカー検査: 4パターンすべて一致0件
- APIキー・秘密鍵形式のソース検査: 一致0件

検証端末はAndroid 15のMedium Phone AVDであり、物理端末ではない。

## 8. 残存リスク

- 端末メーカー固有のKeystore挙動は未検証。
- 電源再起動を伴う永続性試験は未検証。
- クラウドバックアップと端末間転送の実動作は未検証。設定のAPK反映のみ確認済み。
- SQLCipher旧Room連携ライブラリの更新計画が必要。

## 9. Codexに重点確認してほしい点

- 物理端末でのKeystore鍵保持・消失試験。
- OSバックアップ復元および端末間転送後にDB・SharedPreferencesが復元されないこと。
- v1→v2 MigrationはAndroid 15 AVDでデータ保持を確認済み。物理端末でも再確認する。

## 10. 統合判定

- 優先2の自動Android統合テスト層: 統合可能。
- 優先2全体: 物理端末試験待ちのため条件付き。
- フェーズ1全体: 構造化AI操作と本番アラーム統合が未完了のため統合不可。
