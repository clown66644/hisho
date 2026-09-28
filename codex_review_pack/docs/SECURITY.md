# SECURITY.md - セキュリティ & プライバシー要件

## 1. 原則
1. **APIキー秘匿**: ソースコード・ログ・バージョン管理へのAPIキー直書き禁止。Android KeyStore 領域へ暗号化保存。
2. **ローカル暗号化**: 端末内 DB (Room/SQLite) および Preferences は AES-256 (SQLCipher / EncryptedSharedPreferences) で暗号化。
3. **サブAI送信時の匿名化**: 外部サブAI (Perplexity, Gemini, Grok) へ個人情報・健康・家計・服薬データを無条件に送信しない。送出前にマスキングまたはユーザー許可を必須化。
4. **ログ出力禁止規定**: ログ（Logcat等）にAPIキー、認証トークン、暗号鍵、会話本文、個人識別情報を出力することを厳禁とする。

## 2. フェーズ1実装規則

- Room DBの32バイトランダムパスフレーズは、Android Keystore内の非エクスポート鍵でAES-GCM暗号化する。
- 暗号化済みパスフレーズだけが残りKeystore鍵が失われた場合、新しい鍵で上書きせず復旧エラーにする。
- APIキーは`ApiKeyStore`以外へ永続化せず、Android Keystore鍵によるAES-GCM暗号文だけを保存する。
  表示、列挙、ログ出力を行わない。
- DB、SharedPreferences、内部ファイルはOS標準バックアップと端末間転送から除外する。
- OpenAI API要求では`store=false`を指定し、個人情報ではない安定したハッシュ識別子を
  `safety_identifier`として送る。
- HTTPエラー本文、例外本文、Authorizationヘッダー、会話本文を利用者向けエラーへ含めない。
