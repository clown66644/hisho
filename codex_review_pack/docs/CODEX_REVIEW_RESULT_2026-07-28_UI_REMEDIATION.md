# Codexレビュー・改修結果（正式UI再統合）

## 結論

`CREATE_TODO`と基本ToDo UIは正式な`app/src`へ統合済み。
フェーズ1全体は、確認必須操作UIと本番アラームが未実装のため未完了。

## 改修

- `MainActivity`を正式ManifestのLAUNCHER Activityとして登録。
- `MainUiController`を現行`AiCommandConverter`、`UndoManager`、`TodoDao`へ接続。
- 作成・Undo・Redo・再起動復元後にDAOを再読込して画面を更新。
- 同一操作の再送でカードを重複追加しない。
- 暗号化DBを開けない場合はデータを上書きせず操作UIを停止。
- 旧`app/app`のBoolean Command・任意DAO実装は統合していない。

## 追加安全改修

- 同一`operationId`の異なる要求を拒否。
- Undo済み操作IDを適用済み成功として扱わない。
- DB-onlyロールバックは補償済みFAILEDとして記録。
- Responses APIは通常応答を含め`status == completed`を必須化。

## 検証

- 単体テスト: 53/53 PASS
- Android 15 AVD統合テスト: 10/10 PASS
- Lint: Error 0 / Warning 17
- APK生成: SUCCESS
- launchable Activity: `com.example.butler.ui.MainActivity`
- AVDコールド起動: SUCCESS
- FATAL EXCEPTION: 検出なし

## 残存事項

- 現UIの入力は`CREATE_TODO`構造化JSON。OpenAI通常対話との画面接続は未実装。
- 確認必須操作は安全側に拒否し、承認カードはまだ表示しない。
- 本番アラーム、設定暗号化、追加ペルソナ、Google Calendarは未実装。
