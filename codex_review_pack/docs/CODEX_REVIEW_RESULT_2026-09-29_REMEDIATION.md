# レビュー・改修結果（2026-09-29 再監査）

## 結論

正式対象は `D:\AI\秘書\codex_review_pack` の `settings.gradle.kts` が参照する `app/src` です。
操作IDの競合処理を修正しました。カレンダー・設定・ペルソナ・本番アラームの正式版への統合は未完了であり、フェーズ1完成・次段階への移行・安定版統合の承認はしません。

旧 `app/app` は参照専用のまま保存しています。このツリーを修正したり、危険な実装を正式版へコピーしたりはしていません。親フォルダの別プロジェクトも変更対象外です。

## 発見した問題と対応

### R1: 実装先・テスト対象・完了報告の不一致（High）

- 内容・条件: `app/app` や親フォルダの別アプリにある新機能を、正式な `app/src` に統合済みと報告していた。正式設定には `include(":app")` のみ。
- 影響: 利用できない機能を完成と判断し、別プロジェクトの37/38件PASSを根拠に次段階へ進む。
- 対応: `AGENTS.md` に実装先・検証対象を固定。README、TASKS、TEST_PLAN、CODEX_REVIEW、旧カレンダー・ペルソナ報告に明示的な訂正を追記。旧本文は証跡として残した。
- 追加検証: 正式5スイートの新しい実行結果で確認する。新機能は正式UI・永続化・再起動復元まで接続してから完了判定する。
- 状態: 誤った完了判定は訂正。機能統合そのものは未対応。

### R2: 失敗・部分成功の操作IDで別要求を実行できる（High）

- 内容・条件: `UndoManager.executeCommand` がSUCCESS履歴だけ内容照合し、FAILED/PARTIAL_SUCCESSでは別の対象・操作・JSONを同じIDで実行できた。
- 影響: 操作IDの使い回しによる誤登録と監査履歴の上書き。
- 対応ファイル: `app/src/main/java/com/example/butler/domain/logic/UndoManager.kt`。
- 修正: 全状態で操作種別・対象ID・変更前後JSONを照合。トランザクション内でも再確認。競合専用の例外処理で、元の履歴・試行回数・スタックを変更せず拒否。遅れて記録する失敗履歴も別内容の履歴を上書きしない。
- テスト: `UndoManagerTest` に4件追加（FAILED、PARTIAL_SUCCESS、操作/前後JSON差異、初回読取後の競合）。既存の同一要求の失敗後再試行テストは維持。
- 留意: JSONの比較は従来どおり文字列の厳密一致。同じ意味でもJSONを再整形した場合は安全側に拒否するため、再送には元の要求を保持する。
- 状態: 実装修正済み。

### R3: 旧カレンダーのUndoが架空の変更前データを使用する（High）

- 内容・条件: 旧 `app/app/src/.../AiCommandConverter.kt` は更新先日時の周辺で予定が見つからないと「変更前の予定」を生成し、削除ではAIペイロードや現在時刻で復元用予定を生成する。
- 影響: Undoでタイトル・日時・場所等が元に戻らない。削除Undoで再発行されたProvider IDも保持されず、続くRedoが元IDを参照する。
- 推奨修正: Providerから対象IDで実データを取得し、カレンダーIDを含む復元に必要な情報を永続化。取得・権限・対象不一致は実行前に拒否。外部更新と履歴保存の途中失敗を補償し、再発行IDを永続履歴へ反映する。
- 対象: 旧 `AiCommandConverter.kt` / `CalendarCommands.kt` / `CalendarSyncManager.kt` を移植する際の正式版設計。
- 必須テスト: 元日時から離れた日への変更、取得失敗、権限拒否、場所・終日・繰り返し保持、削除→Undo→再起動→Redo、補償失敗。
- 状態: 未修正。旧ツリーは隔離継続。正式版では操作を拒否しており、この機能は提供しない。

### R4: 旧カレンダーの重複検知が登録経路に接続されていない（High）

- 内容・条件: 旧 `CreateEventCommand.execute` は重複検知を呼ばず、直ちに挿入する。
- 影響: 時間帯が重なる予定を警告なしで登録。検知メソッド単体のテストでは登録フローを保証できない。
- 推奨修正: 実行前の重複照会と警告・本人確認を登録フローへ接続。照会失敗を「重複なし」と扱わない。
- 対象: 正式版のカレンダー登録Commandと確認UI。必須テストは重複時の挿入抑止、確認後の実行、照会失敗、繰り返し予定の衝突。
- 状態: 未修正。旧実装の統合は禁止。

### R5: 旧確認Commandが何もせず成功する（High）

- 内容・条件: 旧 `GenericConfirmationCommand.execute/undo` が常にtrue。不完全な更新・削除要求でも確認後に成功扱いになる。
- 影響: 実際には変更されていない予定に対して完了・Undo可能と表示する。
- 推奨修正: パース不完全・未実装操作はエラーまたは再質問。確認済み証跡と実処理を持つCommandだけを成功判定に進める。
- 対象: 旧 `AiCommandConverter.kt` を正式版へ移植する際のCommand変換・確認UI。
- 追加テスト: 正式 `AiStructuredActionTest` に1件追加。カレンダー3操作について、通常形と不完全ペイロードの両方を拒否し、履歴・ToDoが変化しないことを確認する。
- 状態: 旧実装は未修正。正式版の拒否動作を回帰テストで保護。

## 検証

2026-09-29 04:01 JST、正式ソースの検証用コピーで実行完了（52タスクを実行、BUILD SUCCESSFUL）。既存の7月の53件PASSや別ツリーの37/38件PASSは今回の件数に含めていません。

| 検証 | 結果 |
|---|---|
| OpenAiClientTest | 15 / 15 PASS |
| AiStructuredActionTest | 8 / 8 PASS |
| PriorityCalculatorTest | 6 / 6 PASS |
| UndoManagerTest | 26 / 26 PASS |
| MainUiControllerTest | 3 / 3 PASS |
| 単体テスト合計 | **58 / 58 PASS、Failure 0 / Error 0** |
| lintDebug | Error 0 / Warning 9 |
| assembleDebug | 成功 |
| Android実機・エミュレーター統合試験 | 今回は未実行 |

実行ルート: `C:\Users\upsil_km8tika\AppData\Local\Temp\cr-1d756c25`。
`app/src` 全35ファイルおよびGradle設定4ファイルのSHA-256が正式ソースと一致することを確認しました。検証用コピーには旧 `app/app` を含めていません。

```powershell
$env:JAVA_HOME='C:\Users\upsil_km8tika\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2'
$env:ANDROID_HOME='C:\Users\upsil_km8tika\AppData\Local\Android\Sdk'
$env:TEMP='C:\Users\upsil_km8tika\AppData\Local\Temp'
$env:TMP=$env:TEMP
$env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Windows\Temp\codex-jvm-2b8924f6'
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --no-daemon --console=plain '-Pkotlin.incremental=false' '-Pkotlin.compiler.execution.strategy=in-process' --stacktrace
```

上記は実行記録です。再実行時は、存在する書込み可能な英数字パスの検証用コピーと、短いソケット用一時ディレクトリを用意してください。環境変数の変更は検証プロセス内だけで、OSの恒久設定は変更していません。

通常実行ではJavaの `Unable to establish loopback connection`、既存ジャンクションでは生成フォルダエラーが発生。短いソケット一時ディレクトリと、ユーザーのTemp配下の新しい実体コピーで検証できました。WindowsのTemp配下に置いた最初の作業コピーではKaptのR.jarアクセスエラーも出たため、そちらの結果は合格に数えていません。

証跡: [検証XMLの案内](verification/2026-09-29/README.md)。Lintの9警告、SDK XML世代差の警告、SQLCipherネイティブライブラリのstrip警告は残っています。単体テスト・APK生成成功を端末実動作の保証とは扱いません。

## Geminiへの引き継ぎ

`INSTRUCTIONS_FOR_GEMINI.md` の2026-09-29追加指示に従うこと。次段階の新規機能ではなく、正式版への小単位の安全な統合を優先する。上記R3〜R5を未解決のまま旧実装をコピーしない。

## 変更・未変更の範囲

- 変更: 正式 `UndoManager.kt`、`UndoManagerTest.kt`、`AiStructuredActionTest.kt`、作業規約とレビュー・進捗・検証・Gemini指示の文書。
- 未変更: DBスキーマ、保存済み利用者データ、APIモデル・料金設定、外部サービス、旧 `app/app`、親フォルダの別プロジェクト。
- 単独入力 `a` / `あ` は今後「レビューして、必要なら改修して。」として扱うことを作業規約に記録。
