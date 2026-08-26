#!/bin/bash
# pg_dump_backup.sh — ежедневный логический бэкап lsFusion-базы (pg_dump -Fd) на шару/каталог.
# Ставится на сервер БД в /usr/local/sbin/, запускается из cron под postgres:
#   /etc/cron.d/pg-dump-backup:
#     MAILTO=<адрес админов>
#     5 16 * * * postgres /usr/local/sbin/pg_dump_backup.sh <база> <каталог бэкапов>
# Требует локального доступа psql к базе (peer) и стандартной политики именования БД lsFusion
# (FullDBNamingPolicy — колонки вида backup_threadcount в _auto).
#
# Управление — из lsFusion (форма «Резервная копия», хранится в БД, читается на каждом запуске):
#   Количество потоков при бэкапе       -> _auto.backup_threadcount            (-j; пусто = 8)
#   Максимальное число сохраняемых      -> _auto.backup_maxquantitybackups     (ротация)
#   Оставлять бэкап за понедельник      -> _auto.backup_savemondaybackups      (возраст 7..30 дней)
#   Оставлять бэкап за 1-е число        -> _auto.backup_savefirstdaybackups    (возраст > 30 дней)
#   Исключить (галочки на таблицах)     -> reflection_tables.backup_exclude_table
#   Исключить из оперативного копирования -> _auto.backup_extraexclude (список через запятую)
# Исключённые таблицы дампятся БЕЗ ДАННЫХ (--exclude-table-data), как в «оперативном копировании»
# lsFusion; выключается USE_EXCLUDES=0. Ротация повторяет DecimateBackupsAction платформы:
# новые-к-старым, лимит по количеству; старше 30 дней остаются только 1-е числа (если флаг);
# 7..30 дней — 1-е числа и понедельники (если флаги); если ни лимита, ни флагов — лимит 30.
#
# Соглашение с lsFusion-модулем BackupTools (регистрация бэкапов в интерфейсе Backup):
#   во время дампа: каталог <NAME>.part, лог <NAME>.log (пишется по ходу);
#   при исключениях первая строка лога: '# partial dump: ...' (модуль ставит признак partial);
#   успех:  <NAME>.part -> <NAME>;
#   ошибка: <NAME>.part -> <NAME>.failed (каталог создаётся, даже если pg_dump упал до его создания);
#   .part, оставшийся от прерванного запуска, переводится в .failed при следующем старте;
#   ротация — здесь; lsFusion по ней только помечает fileDeleted.

set -u
SCRIPT_VERSION=1.0         # печатается в лог дампа: в интерфейсе видно, какой версией скрипта снят бэкап
export LANG=C LC_ALL=C     # сообщения pg_dump в ASCII: лог читается в lsFusion как UTF-8
# управляющая сессия pg_dump -j висит в idle in transaction весь дамп — таймауты недопустимы
export PGOPTIONS='-c idle_in_transaction_session_timeout=0 -c statement_timeout=0'

DB=${1:-${PGDATABASE:-}}
BACKUP_DIR=${2:-/mnt/backup}
PGBIN=${PGBIN:-/usr/bin}   # /usr/bin/pg_dump на Debian — обёртка, берёт старший установленный PG
USE_EXCLUDES=${USE_EXCLUDES:-1}          # 0 = полный дамп, игнорировать исключения из lsFusion
FAILED_KEEP_DAYS=${FAILED_KEEP_DAYS:-30} # упавшие дампы и их логи старше — удаляются

[ -n "$DB" ] || { logger -t pg_dump_backup "не задана база (аргумент 1 или PGDATABASE)"; exit 1; }

TS=$(date +%Y%m%d_%H%M%S)
NAME="${DB}_${TS}"
PART="$BACKUP_DIR/$NAME.part"
LOG="$BACKUP_DIR/$NAME.log"

fail() { logger -t pg_dump_backup "$*"; echo "$(date '+%F %T') $*" >>"$LOG" 2>/dev/null; exit 1; }
q() { "$PGBIN/psql" -d "$DB" -Atqc "$1" 2>/dev/null; }

# не более одного дампа одновременно (лок локальный: flock на CIFS/NFS ненадёжен)
exec 9>"/tmp/pg_dump_backup_$DB.lock"
flock -n 9 || fail "предыдущий дамп ещё идёт — выход"

# каталог — смонтированная шара? (иначе зальём локальный диск и не увидим этого в lsFusion)
case "$(findmnt -no FSTYPE -T "$BACKUP_DIR" 2>/dev/null)" in
    cifs|nfs|nfs4) ;;
    *) fail "шара $BACKUP_DIR не смонтирована" ;;
esac

# похоронить .part от прерванного прошлого запуска — станет виден в lsFusion как упавший
for d in "$BACKUP_DIR/${DB}_"*.part; do
    [ -e "$d" ] || continue
    b=${d%.part}
    echo "$(date '+%F %T') FAILED: каталог .part остался от прерванного запуска (перезагрузка сервера?)" >>"$b.log"
    mv "$d" "$b.failed"
done

# параметры из базы (пустая _auto / недоступная база -> значения по умолчанию)
JOBS=$(q "select coalesce(backup_threadcount,8) from _auto"); JOBS=${JOBS:-8}
MAXQ=$(q "select backup_maxquantitybackups from _auto")
SAVE_MON=$(q "select case when backup_savemondaybackups is not null then 1 else 0 end from _auto"); SAVE_MON=${SAVE_MON:-0}
SAVE_FIRST=$(q "select case when backup_savefirstdaybackups is not null then 1 else 0 end from _auto"); SAVE_FIRST=${SAVE_FIRST:-0}
EXTRA=$(q "select coalesce(backup_extraexclude,'') from _auto")
EXCLUDES=$(q "select coalesce(string_agg(reflection_sid_table,' '),'') from reflection_tables where backup_exclude_table is not null")

EXCL_ARGS=(); EXCL_LIST=""
if [ "$USE_EXCLUDES" = 1 ]; then
    for t in $EXCLUDES $(echo "$EXTRA" | tr ',' ' '); do
        EXCL_ARGS+=("--exclude-table-data=public.$t")
        EXCL_LIST="$EXCL_LIST $t"
    done
fi

if [ -n "$EXCL_LIST" ]; then
    echo "# partial dump: data of ${#EXCL_ARGS[@]} tables excluded:$EXCL_LIST" >"$LOG"
else
    : >"$LOG"
fi
echo "$(date '+%F %T') start: pg_dump_backup.sh v$SCRIPT_VERSION, pg_dump -Fd -j $JOBS --compress=zstd:3 $DB -> $NAME" >>"$LOG"
start=$(date +%s)
"$PGBIN/pg_dump" -Fd -j "$JOBS" --compress=zstd:3 --no-sync -v -f "$PART" "${EXCL_ARGS[@]}" "$DB" >>"$LOG" 2>&1
rc=$?
dur=$(( $(date +%s) - start ))

if [ "$rc" -eq 0 ]; then
    size=$(du -sm "$PART" 2>/dev/null | cut -f1)
    echo "$(date '+%F %T') OK: за $((dur/60)) мин $((dur%60)) с, размер ${size:-?} МБ" >>"$LOG"
    mv "$PART" "$BACKUP_DIR/$NAME"
else
    echo "$(date '+%F %T') FAILED: pg_dump rc=$rc, шёл $((dur/60)) мин" >>"$LOG"
    mkdir -p "$PART"
    mv "$PART" "$BACKUP_DIR/$NAME.failed"
    logger -t pg_dump_backup "pg_dump $DB failed rc=$rc, лог: $LOG"
    exit 1
fi

# ротация — только после успешного дампа, чтобы серия неудач не съела последние копии.
# Повторяет DecimateBackupsAction: если не задан ни лимит, ни флаги — лимит 30.
if [ -z "$MAXQ" ] && [ "$SAVE_MON" = 0 ] && [ "$SAVE_FIRST" = 0 ]; then MAXQ=30; fi
today=$(date +%s); count=0
for d in $(ls -1d "$BACKUP_DIR/${DB}_"[0-9]* 2>/dev/null | grep -E "/${DB}_[0-9]{8}_[0-9]{6}$" | sort -r); do
    ts=${d##*/}; ts=${ts#"${DB}_"}; day=${ts%%_*}
    age=$(( (today - $(date -d "$day" +%s)) / 86400 ))
    dow=$(date -d "$day" +%u); dom=${day:6:2}
    first=0; [ "$dom" = "01" ] && [ "$SAVE_FIRST" = 1 ] && first=1
    mon=0; [ "$dow" = 1 ] && [ "$SAVE_MON" = 1 ] && mon=1
    del=0
    if [ -n "$MAXQ" ] && [ "$count" -ge "$MAXQ" ]; then del=1
    elif [ "$age" -gt 30 ] && [ "$first" = 0 ]; then del=1
    elif [ "$age" -gt 7 ] && [ "$age" -lt 30 ] && [ "$first" = 0 ] && [ "$mon" = 0 ]; then del=1
    fi
    if [ "$del" = 1 ]; then
        rm -rf "$d" && rm -f "$d.log"
        echo "$(date '+%F %T') ротация: удалён $d" >>"$LOG"
    else
        count=$((count+1))
    fi
done
# упавшие дампы старше FAILED_KEEP_DAYS
cutoff=$(date -d "$FAILED_KEEP_DAYS days ago" +%Y%m%d)
ls -1d "$BACKUP_DIR/${DB}_"*.failed 2>/dev/null | while read -r d; do
    base=${d%.failed}; ts=${base##*/}; ts=${ts#"${DB}_"}; day=${ts%%_*}
    if [ "$day" -lt "$cutoff" ]; then
        rm -rf "$d" && rm -f "$base.log"
        echo "$(date '+%F %T') ротация: удалён $d" >>"$LOG"
    fi
done
exit 0
