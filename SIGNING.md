# Signing & App Links fingerprint (issue #1)

App Links (D2 hướng C) cần Google verify app sở hữu `baokim-qa.com` qua
`/.well-known/assetlinks.json`. File đó chứa **SHA-256 fingerprint** của signing key.
Backend đọc từ `.env` (`APP_LINK_FINGERPRINT`, phân tách dấu phẩy — gồm cả debug lẫn release).

## Debug key (đã có — dùng khi dev)
Debug keystore chuẩn ở `~/.android/debug.keystore` (password `android`, alias `androiddebugkey`).
SHA-256 hiện tại của máy này:

```
29:38:9E:70:13:78:25:87:FE:B8:C8:46:EC:4C:4D:9E:7E:90:86:CA:E1:21:8E:42:17:20:53:A8:C6:9C:2D:54
```

Lấy lại bất cứ lúc nào:
```
keytool -list -v -keystore ~/.android/debug.keystore -storepass android -alias androiddebugkey
```
> ⚠ Debug key khác nhau giữa các máy dev. Máy khác build debug → phải thêm SHA-256 máy đó vào `.env`.

## Release key (tự tạo — GIỮ BÍ MẬT)
Chưa tạo. Khi cần build release:
```
keytool -genkeypair -v -keystore qa-release.jks -alias qa-release \
  -keyalg RSA -keysize 2048 -validity 10000
```
- Đặt `qa-release.jks` ở root repo (đã gitignore `*.jks`). **Backup an toàn — mất là không update app được.**
- Copy `keystore.properties.example` → `keystore.properties`, điền storePassword/keyPassword.
- Lấy SHA-256 release:
```
keytool -list -v -keystore qa-release.jks -alias qa-release
```

## Điền vào `.env` host (backend qa-dashboard)
```
APP_REDIRECT=https://baokim-qa.com/app/auth
APP_LINK_PACKAGE=vn.baokim.qa
APP_LINK_FINGERPRINT=29:38:9E:...:54,<SHA256 release khi có>
```
Rồi restart host. Kiểm tra: `curl https://baokim-qa.com/.well-known/assetlinks.json` phải trả JSON có package + fingerprint (không còn `[]`).

> Bỏ trống 3 biến → luồng mobile tắt, web chạy như cũ (fail-đóng).
