# Проверяемый выпуск Android-приложения

Эта процедура связывает исходный коммит, проверку CI, неподписанную сборку, локальную подпись и опубликованный APK. Секретный ключ остаётся только у владельца.

## Один раз для репозитория

1. Включить **Settings → General → Releases → Release immutability**. Правило действует только на будущие выпуски.
2. Включить **Settings → Security → Advanced Security → Private vulnerability reporting**, Dependabot alerts и Dependabot security updates.
3. Защитить `main`: изменения через pull request и обязательная успешная проверка **Тесты, анализ и сборка**.
4. Настроить подпись тегов Git и проверить, что GitHub показывает метку **Verified**.

## Проверка исходного кода

1. Убедиться, что версия в `app/build.gradle.kts`, `CHANGELOG.md` и заметках выпуска совпадает.
2. Выполнить на чистом рабочем дереве:

```shell
./gradlew --no-daemon test lint assembleDebug assembleRelease
git diff --check
```

3. Слить прошедший ревью pull request в `main`.
4. Создать подписанный тег на точном коммите выпуска и отправить его:

```shell
git tag -s v1.8.1 -m "Чистый курс 1.8.1"
git push origin v1.8.1
```

## Получение и подпись APK

1. Дождаться успешной задачи GitHub Actions для тега.
2. Скачать артефакт `CleanRate-unsigned-COMMIT_SHA` из этой задачи.
3. Проверить его происхождение:

```shell
gh attestation verify app-release-unsigned.apk --repo Etogerman/clean-rate-android
```

4. Подписать APK локально штатным `apksigner`, не копируя ключ в репозиторий или CI:

```shell
apksigner sign --ks /БЕЗОПАСНЫЙ/ПУТЬ/release.jks --out CleanRate-1.8.1.apk app-release-unsigned.apk
apksigner verify --verbose --print-certs CleanRate-1.8.1.apk
```

Ожидаемый SHA-256-отпечаток сертификата:

```text
1798311602fa64b2f43832bcd16252d6ae338654653f9b149f4b33261b83ded0
```

5. Создать контрольную сумму:

```shell
shasum -a 256 CleanRate-1.8.1.apk > CleanRate-1.8.1-SHA256SUMS.txt
```

## Публикация

1. Создать черновик GitHub Release на уже проверенном теге `v1.8.1`.
2. Приложить `CleanRate-1.8.1.apk`, файл контрольной суммы и текст `RELEASE_NOTES_1.8.1.md`.
3. Ещё раз скачать файлы из черновика, сверить SHA-256, имя пакета, `versionCode`, `versionName` и сертификат.
4. Опубликовать выпуск. После публикации не заменять файлы; исправления выпускать новой версией.

Подписанный APK закономерно отличается по SHA-256 от неподписанного артефакта CI: блок подписи добавляется после сборки. Проверка сертификата подтверждает владельца, аттестация — происхождение исходной неподписанной сборки, а неизменяемый Release защищает опубликованные файлы от последующей замены.
