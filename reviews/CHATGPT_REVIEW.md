<USER_REQUEST>
# ChatGPT Review

## Review Target

- Repository: `clown66644/hisho`
- Branch: `main`
- Commit: `8f366d1c97700b53bbb50f6eddf0313a215a2645`
- Review type: リポジトリ全体＋高リスク箇所重点レビュー
- 結論: **FIX REQUIRED**
- Codex監査推奨: **Yes**

---

# 現状

トップレベルでは以下が正式ビルド入口になっています。

- `settings.gradle.kts`
- `app/build.gradle.kts`
- `app/src/...`

一方、リポジトリ内には別プロジェクトとして、

- `codex_review_pack/app/src`
- `codex_review_pack/app/app/src`

も存在します。

さらに `codex_review_pack/AGENTS.md` では、

- `codex_review_pack/app/src` をそのパック内の正式ソース
- `codex_review_pack/app/app` を旧スナップショット

と定義しています。

最新の再監査文書自身も、過去に**別ツリーの実装やテスト結果を正式版として誤認していた**ことをHighとして認定しています。

したがって現在の最大の運用リスクは、

**「どのappを直したのか」「どのappをテストしたのか」が混ざること**

です。

---

# 問題点

## P0 / Critical-01
### RoomのMigration不足時に全データが消える

対象:

`app/src/main/java/com/example/butler/data/local/AppDatabase.kt`

現在:

```kotlin
.fallbackToDestructiveMigration()
```

が指定されています。

Room公式仕様では、Migration経路が存在しない場合、この設定はDBテーブルを破棄して再作成し、既存データを恒久的に削除します。

### 発生条件

アプリ更新時にDB versionを上げたが、対応Migrationが不足している場合。

### 想定被害

- ToDo消失
- 操作履歴消失
- アラーム情報消失
- 将来的には健康・服薬・家計データ消失
- 復元不能

REQUIREMENTS.md上でも「データ破損・復元不能」はCritical相当です。

### 修正

`codex_review_pack/app/src/.../AppDatabase.kt` 側には、

```kotlin
.addMigrations(MIGRATION_1_2, MIGRATION_2_3)
```

を使用したより安全な実装が既にあります。

ただし単純コピーではなく、現在のトップレベルDB schemaと照合して正式Migrationを作ってください。

`fallbackToDestructiveMigration()` は本番系から削除。

### 必要テスト

- DB v1 → 現行
- v2 → 現行
- データ件数保持
- 全カラム保持
- 暗号化DB Migration
- Migration失敗時に旧DBを破壊しない

---

## P0 / Critical-02
### カレンダー更新Undoが元の予定を破壊する可能性

対象:

`AiCommandConverter.kt`

`parseUpdateEvent()` は更新前イベントを、

```kotlin
getEvents(
    startTime - 86400000,
    endTime + 86400000
)
```

から探します。

ここで使っている `startTime/endTime` は**変更後の日時**です。

たとえば、

「10月1日の予定を12月1日に移動」

した場合、10月1日の元イベントは12月1日前後の検索範囲に存在しません。

その場合、

```kotlin
CalendarEvent(
    title = "変更前の予定",
    startTime = startTime,
    endTime = endTime
)
```

という**架空の変更前データ**を作っています。

### 想定被害

Undoすると、

- 元タイトル
- 元日時
- 場所
- その他予定情報

が正しく復元されません。

これは実データ破損です。

`codex_review_pack` の最新再監査でも、同じ問題を **R3 High** として認識しています。

### 修正

更新前に必ず、

**Provider Event IDから対象イベントそのものを取得**

してください。

取得できない場合は更新を実行してはいけません。

Undo用スナップショットへ最低限、

- Provider event ID
- calendar ID
- title
- start/end
- timezone
- all-day
- location
- description
- recurrence関連
- reminder関連

を保存してください。

---

## P0 / Critical-03
### カレンダー削除Undoが完全復元になっていない

対象:

`AiCommandConverter.parseDeleteEvent()`

現在は削除前に実データを取得せず、

AI payloadの、

- title
- startTime
- endTime

を使って `deletedEvent` を生成します。

値がなければ、

- `"削除された予定"`
- 現在時刻
- 現在時刻+1時間

が使用されます。

### 想定被害

削除→Undoすると、

元イベントではなく**別の予定を新規作成する可能性**があります。

さらにCalendar Providerで再作成するとProvider IDが変わる可能性がありますが、その新IDも永続履歴へ正しく保存されていません。

### 修正

削除前にProviderから完全なsnapshotを取得。

取得失敗時は削除禁止。

Undo後に新しいProvider IDが発行された場合は、履歴内のtarget IDも更新する設計が必要。

---

# P1 / High

## P1-01
### 正式ソースが複数存在している

現在、

```text
/app
/codex_review_pack/app
/codex_review_pack/app/app
```

に同一パッケージのコードがあります。

実際、review packでは改善された `UndoManager` やMigration実装がある一方、トップレベル `app/` は古い実装のままです。

### 影響

AIが、

- 間違ったコードを修正
- 間違ったコードをテスト
- 別ツリーのPASSを完成根拠にする

可能性があります。

これは既に一度発生したことが再監査文書にも記録されています。

### 修正

今後は原則、

```text
/app
```

を唯一の製品ソースにすることを推奨します。

`codex_review_pack` はコード複製ではなく、

```text
archive/reviews/
```

等へレビュー証跡だけを保存する方式へ変更してください。

安全版コードはdiffを確認してトップレベルへ段階的に移植します。

---

## P1-02
### 実際のアプリがDBへ接続されていない

対象:

`MainActivity.kt`

```kotlin
private val viewModel: MainViewModel = MainViewModel()
```

対象:

`MainViewModel.kt`

```kotlin
UndoManager()
AiCommandConverter()
```

をデフォルト生成しています。

結果、

- `TodoDao = null`
- `OperationHistoryDao = null`
- CalendarSyncManagerのContext = null

です。

### 影響

ToDoは `currentTodoList` にしか存在せず、

**アプリ終了・プロセス停止で消えます。**

操作履歴もDBへ保存されません。

Calendar操作も実Providerへ到達しません。

### 修正

Room DBを起点として依存関係を生成してください。

review pack側にある、

```kotlin
MainUiController(AppDatabase)
```

方式は現状より安全です。

小規模段階なので、Hilt等を今すぐ追加せず単純なFactory/Containerでも十分です。

---

## P1-03
### CalendarSyncManagerが本番UIから動作しない

`AiCommandConverter()` のdefault:

```kotlin
CalendarSyncManager()
```

はContextなしです。

そのため実端末で、

```kotlin
hasWritePermission()
```

がfalseになり、予定追加が失敗します。

現在の単体テストは `inMemoryEvents` を使っているため、この本番配線問題を検出できません。

---

## P1-04
### BootReceiverが再起動後のアラームを復元できない

対象:

`BootReceiver.kt`

```kotlin
private val alarmDaoProvider: (() -> AlarmDao?)? = null
```

Android OSがReceiverを生成する通常ルートではproviderはnullです。

その後、

```kotlin
alarmDaoProvider?.invoke() ?: return@launch
```

となるため、何も復元せず終了します。

さらに `goAsync()` を使わずCoroutineを開始しているため、`onReceive()` 終了後にプロセスを停止される可能性があります。

### 修正

Receiver内で安全にDBを取得し、

```kotlin
val pendingResult = goAsync()
```

を使用して、finallyで `finish()`。

実端末再起動テスト必須。

---

## P1-05
### Notification / Calendar runtime permission処理不足

Manifestには、

```text
POST_NOTIFICATIONS
READ_CALENDAR
WRITE_CALENDAR
```

がありますが、MainActivityではCalendar/Notification権限要求がありません。

Android 13以上ではPOST_NOTIFICATIONSはruntime permissionで、新規インストールでは通知はデフォルトOFFです。

READ_CALENDARもdangerous permissionであり、実行時権限要求が必要です。

現在実装されているのはExact Alarm設定への誘導だけです。

### 影響

- カレンダー読み書き不可
- 通知が表示されない
- 「アラームは設定済みなのに通知されない」

状態が発生します。

---

## P1-06
### calendarId = 1 固定

対象:

`CalendarSyncManager.insertEvent()`

```kotlin
calendarId: Long = 1L
```

CreateEventCommandはcalendarIdを指定していないため、常に1です。

### 影響

端末によって、

- ID 1が存在しない
- 書込み不可
- 想定と違うGoogleアカウント

になる可能性があります。

REQUIREMENTS.mdの「複数Googleカレンダー対応」とも一致しません。

### 修正

Calendars Providerから書込み可能Calendar一覧を取得し、利用者選択/既定値を保存する。

---

## P1-07
### 不完全な操作が「成功」になる

対象:

`GenericConfirmationCommand`

```kotlin
execute(): Boolean = true
undo(): Boolean = true
```

UPDATE_EVENTなどのpayloadが不足するとGenericConfirmationCommandへ変換されます。

ユーザーが確認ボタンを押すと、

**何もしていないのに成功**

となります。

MainViewModelも、

> 操作を承認・実行しました。

と表示します。

REQUIREMENTS.mdの、

「成功確認前に完了表示しない」

に明確に反します。

### 修正

不完全な操作は、

- 再質問
- validation error

のどちらか。

No-op成功Commandは禁止。

---

## P1-08
### 重複検知が予定登録フローに接続されていない

`detectDuplicates()` 自体はあります。

しかし、

```kotlin
CreateEventCommand.execute()
```

では呼んでいません。

つまりユーザーが重複予定を作成しても、そのままinsertされます。

最新Codex再監査でもR4 Highとして指摘済みです。

また `getEvents()` は例外時にemptyListを返すため、

**「取得に失敗した」と「予定が0件」を区別できません。**

重複確認では危険です。

---

## P1-09
### Undoすると関係ないToDoを消す可能性

対象:

`MainViewModel.undo()`

```kotlin
if (currentTodoList.isNotEmpty()) {
    currentTodoList.removeAt(currentTodoList.size - 1)
}
```

Undo対象のCommand種類を確認していません。

例えば、

- ペルソナ変更
- カレンダー変更

をUndoしても、最後のToDoをUIから削除します。

RedoでもToDoを正しく戻していません。

### 修正

UI状態を手作業でundoしない。

DBを唯一の事実情報として、

```text
Command
↓
DB変更
↓
UIはDB再読込
```

に統一してください。

---

## P1-10
### 現在のUIはChatGPT会話ではなくJSON入力

MainActivityの送信ボタンは、

```kotlin
viewModel.handleAiJsonInput(text)
```

へ直接渡しています。

`OpenAiClient` はUIフローに接続されていません。

したがって現在は、

> 明日の15時に病院を入れて

ではなく、

構造化JSONをユーザー自身が入力するUIです。

フェーズ1の重要受入条件、

**「話すことで予定・行動管理ができる」**

にはまだ達していません。

---

# P2 / Medium

## P2-01
### REQUIREMENTS / OPERATION_SPEC / DECISIONSの役割定義が矛盾

`OPERATION_SPEC.md` は、

- ChatGPT = 通常レビュー
- Codex = 重点監査

ですが、

`REQUIREMENTS.md` と `DECISIONS.md` には旧来の、

- AntiGravity
- Codex

2体制の記述が残っています。

READMEではREQUIREMENTS.mdを最優先としているため、AIが旧ルールを採用する可能性があります。

運用仕様v2.0へ統一してください。

---

## P2-02
### REVIEW_REQUEST.mdがレビュー固定ルールを満たしていない

現在:

```text
Review Target Commit
<commit SHA>
```

のままです。

さらに、

```text
feature/calendar-alarm-integration
```

ブランチは現在確認できず、`main`のみ確認できました。

OPERATION_SPECが要求する「レビュー対象SHA固定」が実際には機能していません。

---

## P2-03
### README / ARCHITECTUREと実装が不一致

README:

```text
Jetpack Compose
```

実装:

- AppCompatActivity
- XML layout
- RecyclerView

Compose依存関係もありません。

Composeを採用するかXMLを採用するかではなく、

**現在の実装を文書に正確に書く**

ことが重要です。

---

## P2-04
### CIが確認できない

`.github/workflows` は現在確認できませんでした。

ローカルAIが、

> 38件PASS
> 58件PASS

と報告しても、GitHub側で独立検証されません。

最低限、

```text
assembleDebug
testDebugUnitTest
lintDebug
```

をGitHub Actions化すると、AIの誤報告をかなり防げます。

---

## P2-05
### AI Provider抽象化が未反映

現在、

```kotlin
OpenAiClient
```

へ直接依存する構造です。

今後Perplexity / Gemini / Grok / Local AIを追加する方針を考えると、

```text
AIProvider
 ├ OpenAIProvider
 ├ GeminiProvider
 ├ PerplexityProvider
 └ ...
```

の境界を先に設けた方がよいです。

ただし今すぐ全Providerを実装する必要はありません。

OpenAIProviderのみで十分です。

---

# セキュリティ

## APIキー保存

`SettingsManager` はEncryptedSharedPreferences生成に失敗すると、

```kotlin
context.getSharedPreferences(
    "user_settings_fallback_prefs",
    MODE_PRIVATE
)
```

へフォールバックします。

この状態で `setApiKey()` すると、APIキーを通常Preferencesへ保存します。

これはSECURITY.mdの方針と矛盾します。

### 修正

暗号化保存に失敗した場合は、

**平文へfallbackせず、API利用機能を停止してください。**

Fail-openではなくFail-closed。

---

## 秘密情報

今回確認した主要ファイル、およびGitHubコード検索では、

- `sk-`
- `AIza`
- PRIVATE KEY

等の明らかな実キーは検出できませんでした。

ただしGitHub検索は `incomplete_results=true` だったため、これを完全なSecret Scanとは扱いません。

GitHub Secret Scanning等を別途有効化してください。

---

# テスト

## 私が今回実行したもの

**実行していません。**

今回はGitHub上のコード・設定・既存テスト・レビュー証跡を静的レビューしています。

---

## リポジトリ内の既存結果

トップレベル文書には、

- 38 / 38 PASS

という記録があります。

一方最新の `codex_review_pack` 再監査では、

**過去に別ツリーのテスト結果を完成判定へ使用していた**

こと自体がHighとして訂正されています。

review packの正式ツリーでは、

- 58 / 58 PASS
- lint error 0
- assembleDebug成功

という新しい記録があります。

ただし、この58件PASSは**トップレベル `app/` の合格証明ではありません。**

ここを混同しないでください。

---

# 推奨方針

現時点では新機能を増やさない方が安全です。

まず、

```text
コード正本一本化
↓
Critical修正
↓
本番依存配線
↓
実Provider Calendarテスト
↓
Alarm rebootテスト
↓
ChatGPT統合
```

の順で進めます。

---

# 実装またはAntiGravityへの指示

次のSprintを、

**「Source of Truth統合＋P0修正Sprint」**

にしてください。

変更範囲を広げすぎず、順番は以下。

1. 正式製品コードをトップレベル `/app` に一本化
2. review packの安全なMigration / UndoManager改善を差分確認して移植
3. `fallbackToDestructiveMigration()` を廃止
4. SettingsManagerの平文fallbackを廃止
5. Calendar update/delete前にProviderから完全snapshot取得
6. snapshot取得失敗時は操作中止
7. `GenericConfirmationCommand` の成功no-op廃止
8. DBをMain UIへ実接続
9. runtime permission処理追加
10. BootReceiverを実DB＋goAsync方式へ修正

一度にUI刷新や新機能追加はしない。

---

# バックアップ・ロールバック

変更前に、

```text
git status
git branch
git rev-parse HEAD
```

を記録。

現在基準:

```text
8f366d1c97700b53bbb50f6eddf0313a215a2645
```

から、

```text
fix/source-of-truth-and-p0
```

のようなbranchを作成。

DB Migration変更前には、旧version DB fixtureも保存。

review packを削除・整理する場合も、最初は削除せず、

```text
archive/
```

へ移すか別branchへ保持してから整理する。

---

# 完了条件

次Sprint終了条件:

- P0 = 0
- source rootが1つ
- Room destructive migrationなし
- APIキー平文fallbackなし
- Calendar update/delete Undoが完全snapshot方式
- Main UIが暗号化DBへ接続
- Calendar runtime permission動作
- Notification runtime permission動作
- reboot後Alarm復元
- unit test成功
- instrumented test成功
- 実端末確認
- REVIEW_REQUEST.mdに実commit SHA記載

それまでは、

**「フェーズ1完成」「カレンダー統合完了」「安定版」**

とは判定しない。
</USER_REQUEST>
<ADDITIONAL_METADATA>
The current local time is: 2026-09-29T16:48:44+09:00.
</ADDITIONAL_METADATA>