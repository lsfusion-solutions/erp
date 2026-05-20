# Redmine (support.luxsoft.by)

> Вынесено из [AGENTS.md](../AGENTS.md). Читать **только по необходимости** —
> когда задача действительно требует работы с Redmine (чтение задачи,
> поиск, скачивание вложений, комментарий, перевод статуса).

База: `http://support.luxsoft.by` — внутренний Redmine, протокол **HTTP** (не HTTPS).

API-ключ берётся из пользовательской переменной окружения `REDMINE_API_KEY`.
Передавать через заголовок `X-Redmine-API-Key`, а не через query-параметр `key=...`
(чтобы не уходил в логи прокси/историю).

## Чтение задачи

```bash
curl -sS \
  -H "X-Redmine-API-Key: $REDMINE_API_KEY" \
  "http://support.luxsoft.by/issues/<ID>.json?include=journals,attachments,relations,children"
```

`include`-параметры:

| Значение | Что добавляет |
|---|---|
| `journals` | комментарии и историю изменений атрибутов |
| `attachments` | список вложений (с `content_url` для скачивания) |
| `relations` | связанные задачи (blocked/blocks/duplicates/...) |
| `children` | подзадачи (с их `subject` и `tracker`) |

### Полезные поля ответа

- `issue.subject`, `issue.description` — заголовок и описание.
- `issue.project.name`, `issue.tracker.name`, `issue.status.name`, `issue.priority.name`.
- `issue.author.name`, `issue.assigned_to.name` (может отсутствовать).
- `issue.parent.id` — родительская задача (читать рекурсивно при необходимости).
- `issue.journals[].notes` — текст комментария; `issue.journals[].details[]` — изменения атрибутов (description, status_id, assigned_to_id и т.п.).
- `issue.custom_fields[]` — пользовательские поля проекта.

## Поиск и фильтры

```bash
# Все задачи проекта в статусе "Новая"
curl -sS -H "X-Redmine-API-Key: $REDMINE_API_KEY" \
  "http://support.luxsoft.by/issues.json?project_id=<slug>&status_id=1&limit=100"
```

Пагинация: `limit` (макс. 100) + `offset`.

## Скачать вложение

```bash
curl -sS -H "X-Redmine-API-Key: $REDMINE_API_KEY" \
  "http://support.luxsoft.by/attachments/download/<attachment_id>/<filename>" \
  -o <filename>
```

## Завершение задачи: комментарий + перевод в «Решена»

После того как изменения закоммичены и в задачу добавлен комментарий с
кратким описанием для клиента (без технических подробностей — в том
числе идентификаторов и других имён элементов; ревизию указывать тоже
не нужно; только видимые пользователю заголовки), **сразу же предложить
пользователю** также перевести задачу в статус **«Решена»**
(`status_id=3`).

Принятая в команде конвенция: разработчик переводит в `Решена` сразу
после готовности изменений (изменения в svn + комментарий); клиент далее сам закрывает задачу или возвращает в работу.

Перевод статуса:

```bash
curl -sS -X PUT \
  -H "X-Redmine-API-Key: $REDMINE_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"issue":{"status_id":3}}' \
  "http://support.luxsoft.by/issues/<ID>.json"
```

Полный список статусов (`id` ↔ `name`) — `GET /issue_statuses.json`.

## Комментарий с вложением / JSON с кириллицей

Отправка payload с прикреплённым файлом, а также JSON с кириллицей или
переводами строк в `notes`/`description`/`custom_fields` (inline
`-d '{...}'` тут ломается → `HTTP 500`) описана в отдельном файле —
[REDMINE-PAYLOAD.md](REDMINE-PAYLOAD.md). Читать его **только по
необходимости**, когда нужно вместе с комментарием передать файл или
payload с кириллицей.

## Ограничения

- `WebFetch` на этот хост не работает (внутренний — `ECONNREFUSED` извне корпсети).
  Использовать `curl` через Bash с `$REDMINE_API_KEY`.
- API-ключ не логировать, не печатать в чат, не сохранять в файлы репозитория.
