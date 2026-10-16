# XLinkFixer

クリップボードに `x.com` / `twitter.com` のツイート URL がコピーされたら、
`fixupx.com`（または `fxtwitter.com` / `vxtwitter.com`）に自動で書き換えるツール。
Windows 版と Android 版があり、GitHub Actions でタグを打つだけで両方ビルドされます。

```
https://x.com/user/status/1234567890?s=20&t=abc   →   https://fixupx.com/user/status/1234567890
```

書き換え対象はツイート本体の URL（`/status/` を含むもの）だけです。プロフィールなどはそのまま。
末尾の `?s=…&t=…` といったトラッキング用クエリは捨てます。

---

## ビルドのさせ方（GitHub Actions）

1. このフォルダを GitHub にリポジトリとして push する
2. タグを打つ

   ```bash
   git tag v0.1.0
   git push origin v0.1.0
   ```

3. Actions が走り、Releases に `XLinkFixer.exe` と `XLinkFixer-android.apk` が添付される

Actions タブの「Run workflow」から手動でも回せます（その場合は Release は作られず、Artifacts に成果物が置かれます）。

### Android の署名鍵（任意だが推奨）

Secrets を設定しなければ debug 鍵で署名されます。インストールはできますが、鍵がビルドごとに変わるため
**上書き更新ができません**（毎回アンインストール→再インストールになります）。
一度鍵を作って Secrets に入れておくと、以降は上書き更新できます。

```bash
keytool -genkeypair -v -keystore release.jks -alias xlinkfixer -keyalg RSA -keysize 2048 -validity 10000
```

できた `release.jks` を base64 にして、リポジトリの Settings → Secrets and variables → Actions に登録:

| Secret 名            | 値                                  |
| -------------------- | ----------------------------------- |
| `KEYSTORE_BASE64`    | `base64 -w0 release.jks` の出力      |
| `KEYSTORE_PASSWORD`  | keytool で入れたストアのパスワード    |
| `KEY_ALIAS`          | `xlinkfixer`（上の例の場合）             |
| `KEY_PASSWORD`       | キーのパスワード（省略時はストアと同じ）|

PowerShell の場合の base64:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks")) | Set-Clipboard
```

`release.jks` 自体はリポジトリに入れないこと（`.gitignore` 済み）。

---

## Windows 版

- `XLinkFixer.exe` をどこかに置いて実行するだけ。タスクトレイに常駐します（.NET のインストール不要）
- トレイアイコンの右クリックメニュー:
  - **有効** … 一時的に止めたいとき
  - **置き換え先** … fixupx / fxtwitter / vxtwitter
  - **書き換え時に通知** … バルーン通知の ON/OFF
  - **Windows 起動時に実行** … スタートアップ登録（レジストリ `HKCU\...\Run`）
- 設定は `%APPDATA%\XLinkFixer\settings.json` に保存されます
- 署名していない exe なので、初回は SmartScreen に止められます。「詳細情報」→「実行」で通ります

## Android 版

### インストール時の注意（Play プロテクト）

Play ストア経由ではないアプリで、かつアクセシビリティ権限を使うため、
インストール時に **Google Play プロテクト** に「不明なデベロッパーのアプリをブロックしました」と止められます。

一時的にスキャンを切ってからインストールしてください。

1. Play ストアを開く → 右上のプロフィールアイコン → **Play プロテクト**
2. 右上の歯車 → **「Play プロテクトによるアプリのスキャン」をオフ**（確認ダイアログで「オフにする」）
3. この状態で `XLinkFixer-android.apk` を開いてインストール
4. インストールが終わったら、同じ場所で **オンに戻す**（戻しても入れたアプリは消されません）

ブロックのダイアログに「詳細」や「それでもインストール」のリンクが出る場合は、そこから進んでも構いません。
ただし更新のたびに出るので、上の手順のほうが結局早いです。

### アクセシビリティをオンにできないとき（制限付き設定）

Android 13 以降では、ブラウザやファイルマネージャーから入れたアプリのアクセシビリティを
オンにしようとすると **「制限付き設定」** で弾かれます。その場合は

1. 設定 → アプリ → **XLinkFixer** を開く
2. 右上の「︙」→ **「制限付き設定を許可」**
3. アプリに戻って「アクセシビリティ設定を開く」をやり直す

これを済ませれば、以降は通常どおり ON にできます。

### 仕組みと制約

Android 10 以降、バックグラウンドのアプリはクリップボードを読めません（読めるのは前面のアプリとキーボードだけ）。
そこで XLinkFixer は

1. アクセシビリティサービスで「コピーが起きた瞬間」を検知し
   （Android 13+ のシステムの「クリップボードにコピーしました」表示、各アプリのトースト、共有シートの「リンクをコピー」タップ）
2. 1px の透明なオーバーレイを一瞬出してフォーカスを取り、その隙にクリップボードを読んで書き換え、すぐ消す

という手順を踏みます。正攻法ではないので、機種や OS バージョンによっては動かないことがあります。
その場合は共有シートの **「XLinkFixer でリンクを書き換えてコピー」** を使ってください（こちらは確実に動きます）。

### セットアップ

1. `XLinkFixer-android.apk` をインストール（「提供元不明のアプリ」を許可）
2. アプリを開いて、上から順に
   - **アクセシビリティ設定を開く** → XLinkFixer を ON（必須）
   - **オーバーレイ権限を開く** → 許可（推奨。無い場合はアクセシビリティ用オーバーレイで代用を試みます）
   - **電池の最適化から除外する**（Xiaomi / OPPO / Huawei などバックグラウンドを殺しがちな機種では必須級）
3. X アプリでツイートの「共有」→「リンクをコピー」して、どこかに貼り付けて確認

### 動作確認・トラブル時

- アプリ内の **ログ** に、検知・書き換え・失敗が記録されます
- **クリップボードを今すぐ書き換える（テスト）** は、検知を介さず今の内容を直接書き換えます。書き換え自体の確認用
- 反応しないときは **検知を強める** を ON にしてみてください（SystemUI のウィンドウ変化すべてで読みに行くようになります。誤爆が増える代わりに拾いやすくなります）
- Android 12 以降では、書き換えのたびにシステムが「XLinkFixer がクリップボードから貼り付けました」と表示します。他アプリがクリップボードを読んだときに必ず出る仕様で、消せません
- Xiaomi (MIUI/HyperOS) は追加で「バックグラウンドでのポップアップ」「自動起動」の許可が要ることがあります

---

## 構成

```
.github/workflows/build.yml   タグ push で Windows / Android を並列ビルド → Release
windows/                      C# (.NET 8, WinForms) トレイ常駐アプリ
android/                      Kotlin アプリ（Gradle wrapper は同梱していません。CI は setup-gradle、
                              手元では Android Studio で開けば自動で用意されます）
```

URL の書き換え正規表現は `windows/Rewriter.cs` と `android/.../Rewriter.kt` で同じものを使っています。
直すときは両方を揃えてください。


