# 優先1 実装結果（2026-07-26）

## 1. 実装した内容

- `OperationHistory`をSQLCipherで暗号化されるRoom DBへ永続化した。
- CommandのローカルDB更新と履歴保存を`RoomOperationTransactionRunner`の同一トランザクションへ統合した。
- Execute / Undo / Redoの成功、失敗、部分成功、試行回数、補償結果を記録するようにした。
- 履歴更新に失敗した場合、対象DB変更をロールバックし、Undo/Redoスタックを移動しないようにした。
- 成功済みの同一操作IDを再実行しない冪等処理を追加した。
- アプリ再起動相当の`UndoManager`再生成後に、Undo/Redoスタックを履歴から復元できるようにした。
- Undo後に新規操作を行った場合、古いRedo分岐をDB上でも無効化するようにした。
- Room v1からv2への非破壊マイグレーションを追加した。

## 2. 発見した問題

- Command実行と履歴保存が同一Roomトランザクションになっていなかった。
- Undo/Redo後の履歴更新失敗を成功扱いし、スタックとDBが不一致になる可能性があった。
- 再起動後にUndo/Redoスタックを復元できなかった。
- Undo後の新規操作で、メモリ上だけRedoを消しており、再起動すると古いRedo分岐が復活する可能性があった。
- システム既定JDK 25はGradle 8.2.1と互換性がなかった。

## 3. 修正した問題

上記の操作履歴・トランザクション・復元・Redo分岐の問題を修正した。検証はJDK 17を明示して実施した。

## 4. 修正しなかった問題と理由

- 暗号化DB、Keystore鍵消失、バックアップ除外の実機試験は優先2の範囲であり未実施。
- 構造化AI操作は優先3、本番アラーム統合は優先4のため未実装。
- SDK XMLバージョン警告、依存更新、KSP移行等のLint Warning 11件は今回の安全性修正範囲外。

## 5. 変更ファイル

- `app/src/main/java/com/example/butler/domain/model/OperationHistory.kt`
- `app/src/main/java/com/example/butler/domain/logic/UndoManager.kt`
- `app/src/main/java/com/example/butler/data/local/AppDatabase.kt`
- `app/src/main/java/com/example/butler/data/local/RoomOperationTransactionRunner.kt`
- `app/src/main/java/com/example/butler/data/local/entity/OperationHistoryEntity.kt`
- `app/src/main/java/com/example/butler/data/local/dao/OperationHistoryDao.kt`
- `app/src/test/java/com/example/butler/domain/logic/UndoManagerTest.kt`
- `docs/TASKS.md`
- `docs/TEST_PLAN.md`
- `docs/CHANGELOG.md`

## 6. 実行したテストと件数

- `PriorityCalculatorTest`: 6件
- `OpenAiClientTest`: 4件
- `UndoManagerTest`: 13件
- 合計: 23件

## 7. テスト結果

- `testDebugUnitTest`: 23/23成功、Failure 0、Error 0、Skipped 0
- `lintDebug`: 成功、Error 0、Warning 11
- `assembleDebug`: 成功
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Roomスキーマ: v1、v2のJSON生成を確認
- APIキー形式および秘密鍵形式のソース検索: 該当なし

日本語パスによるKotlin/KAPTのクラスパス問題を避けるため、同じフォルダを指す英数字ジャンクション
`C:\Users\upsil_km8tika\AppData\Local\Temp\codex-butler-review-ascii`から実行した。

## 8. 残存リスク

- CommandのローカルDB更新は、`UndoManager`へ渡したものと同じ`AppDatabase`のDAOを使う必要がある。
- OS予約などDB外の副作用はRoomでロールバックできないため、各Commandの`compensate`実装が必要。
- SQLCipher・Keystore・v1→v2 Migrationの実機統合試験は未実施。
- `Command`を復元する本番Command Resolverは、対応する各機能の実装時に厳格なJSON検証が必要。

## 9. Codexに重点確認してほしい点

- 各本番Commandが同一`AppDatabase`のDAOだけを利用しているか。
- DB外副作用を持つCommandが、失敗時の補償処理を正しく実装しているか。
- 履歴JSONからCommandを復元するResolverが未知操作・破損データを拒否するか。

## 10. 統合判定

- 優先1のコア層: 統合可能。
- フェーズ1全体: 実機暗号化試験、構造化AI操作、本番アラーム統合が未完了のため統合不可。
