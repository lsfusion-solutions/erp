# AGENTS.md

Памятка для AI-агентов и инженеров, работающих в этом репозитории.

## Redmine (support.luxsoft.by)

База: `http://support.luxsoft.by` — внутренний Redmine, протокол **HTTP** (не HTTPS).

API-ключ берётся из пользовательской переменной окружения `REDMINE_API_KEY`.
Передавать через заголовок `X-Redmine-API-Key`, а не через query-параметр `key=...`
(чтобы не уходил в логи прокси/историю).

### Чтение задачи

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

### Поиск и фильтры

```bash
# Все задачи проекта в статусе "Новая"
curl -sS -H "X-Redmine-API-Key: $REDMINE_API_KEY" \
  "http://support.luxsoft.by/issues.json?project_id=<slug>&status_id=1&limit=100"
```

Пагинация: `limit` (макс. 100) + `offset`.

### Скачать вложение

```bash
curl -sS -H "X-Redmine-API-Key: $REDMINE_API_KEY" \
  "http://support.luxsoft.by/attachments/download/<attachment_id>/<filename>" \
  -o <filename>
```

### Ограничения

- `WebFetch` на этот хост не работает (внутренний — `ECONNREFUSED` извне корпсети).
  Использовать `curl` через Bash с `$REDMINE_API_KEY`.
- API-ключ не логировать, не печатать в чат, не сохранять в файлы репозитория.

## MCP-серверы

- `lsfusion` — HTTP `http://localhost:63342/lsfusion` (плагин lsFusion в IDEA);
  обязательно использовать `mcp__lsfusion__*` для поиска элементов, документации и
  валидации синтаксиса при работе с `.lsf`-файлами.
- `jetbrains` — stdio `npx -y @jetbrains/mcp-proxy`;
  доступ к открытому проекту в IDEA (файлы, терминал, дебаггер).

## Конвенции репозитория

- Источник правды для lsFusion-кода: `*/src/main/lsfusion`. Файлы в `out/production` —
  генерируемые, править нельзя.
- Перед правкой `.lsf` подгружать `mcp__lsfusion__lsfusion_get_guidance` и следовать
  его правилам (правила NULL/BOOLEAN, naming, форм, сессий, импорта).
- Дедуп/выбор «канонической» строки в импортах — через `GROUP LAST ... BY <key>`
  (модульное свойство), а не через цепочки `LOCAL` + `GROUP MIN`.
