# Redmine: payload с комментарием, вложением, кириллицей

> Вынесено из [REDMINE.md](REDMINE.md). Читать **только по необходимости** —
> когда нужно отправить вместе с комментарием файл (вложение) или JSON с
> кириллицей/переводами строк в `notes`/`description`/`custom_fields`.

База, API-ключ, чтение задачи, поиск, голая смена статуса без кириллицы и
прочие базовые операции — см. [REDMINE.md](REDMINE.md).

## Комментарий / поля с русским текстом

JSON-payload с кириллицей или переводами строк в полях `notes`,
`description`, `custom_fields` и т.п. передавать **через файл**, не inline
`-d '{...}'` — на Windows-Bash одинарные кавычки ломают экранирование
кириллицы/`\n`, и Redmine отдаёт `HTTP 500` без подробностей. Файл писать
Write-инструментом в UTF-8 (heredoc и `echo` зависят от shell и тоже могут
ломаться).

```bash
curl -sS -X PUT \
  -H "X-Redmine-API-Key: $REDMINE_API_KEY" \
  -H "Content-Type: application/json" \
  --data-binary @payload.json \
  "http://support.luxsoft.by/issues/<ID>.json"
```

Универсальный `payload.json` — комментарий + смена статуса + вложение
одним вызовом:

```json
{
  "issue": {
    "status_id": 3,
    "notes": "Краткое описание для клиента.",
    "uploads": [
      {"token": "<token>", "filename": "screenshot.png", "content_type": "image/png"}
    ]
  }
}
```

`notes` — новый комментарий, ранее опубликованные не переписываются.
`uploads[].token` получается отдельным шагом — см. «Прикрепить файл».

Для payload **без** кириллицы и переводов строк (голая смена статуса и
т.п.) inline `-d '{...}'` работает — пример есть в [REDMINE.md](REDMINE.md)
в разделе «Завершение задачи».

## Прикрепить файл (вложение)

Двухшаговая схема: загрузить тело → получить `token` → сослаться на него в
PUT/POST issue.

```bash
curl -sS -X POST \
  -H "X-Redmine-API-Key: $REDMINE_API_KEY" \
  -H "Content-Type: application/octet-stream" \
  --data-binary @<file> \
  "http://support.luxsoft.by/uploads.json?filename=<name>"
```

Ответ: `{"upload":{"id":..., "token":"..."}}`. Этот `token` передаётся в
`uploads[]` PUT-а (см. выше).
