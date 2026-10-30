"""Execute production schema and predicates in SQLite; this is not Android scheduling."""
import re, sqlite3, json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
source=(root/'src/com/eidolon/sc/mobile/ReachPlanStore.java').read_text(encoding='utf-8')
unread=(root/'src/com/eidolon/sc/mobile/UnreadState.java').read_text(encoding='utf-8')
chat=(root/'src/com/eidolon/sc/mobile/ChatStore.java').read_text(encoding='utf-8')
db=sqlite3.connect(':memory:')
db.execute('CREATE TABLE messages(id INTEGER PRIMARY KEY,character TEXT,conversation TEXT,role TEXT,body TEXT,reach_at INTEGER DEFAULT 0)')
db.execute("INSERT INTO messages VALUES(1,'tenka','s','assistant','old',0)")
for code in (source,unread):
    for sql in re.findall(r'db.execSQL\("((?:CREATE|ALTER)[^"\n]+)"',code):db.execute(sql)
assert db.execute('SELECT unread FROM messages WHERE id=1').fetchone()==(0,),'v11 messages migrate read'
db.execute("INSERT INTO reach_days VALUES('2026-10-26','Asia/Shanghai',1,100)")
try:db.execute("INSERT INTO reach_days VALUES('2026-10-26','Asia/Shanghai',1,100)")
except sqlite3.IntegrityError:pass
else:raise AssertionError('day ledger must be unique')
db.execute("INSERT INTO reach_tasks(id,character,session,day,zone,due,expires,created,revision) VALUES('t','tenka','s','2026-10-26','Asia/Shanghai',200,1000,100,1)")
predicate=re.search(r'update\("reach_tasks",v,"([^"]+)"',source).group(1)
assert db.execute('UPDATE reach_tasks SET status=\'RUNNING\',token=\'a\' WHERE '+predicate,('t','200','200')).rowcount==1
assert db.execute('UPDATE reach_tasks SET status=\'RUNNING\',token=\'b\' WHERE '+predicate,('t','200','200')).rowcount==0,'second worker cannot claim'
assert db.execute("SELECT status,token FROM reach_tasks WHERE id='t'").fetchone()==('RUNNING','a')
db.commit()
# Actual message insert + task completion form one transaction; rollback preserves both.
db.execute('BEGIN')
db.execute("INSERT INTO messages VALUES(2,'tenka','s','assistant','new',300,1)")
db.execute("UPDATE reach_tasks SET status='COMPLETED',message_id=2 WHERE id='t' AND status='RUNNING' AND token='a'")
db.rollback()
assert db.execute('SELECT count(*) FROM messages WHERE id=2').fetchone()==(0,)
assert db.execute("SELECT status FROM reach_tasks WHERE id='t'").fetchone()==('RUNNING',)
db.execute("INSERT INTO messages VALUES(2,'tenka','s','assistant','new',300,1)")
db.execute("UPDATE reach_tasks SET status='COMPLETED',message_id=2 WHERE id='t' AND status='RUNNING' AND token='a'")
db.execute("INSERT INTO messages VALUES(3,'amana','s2','assistant','reply',0,1)")
count_sql=re.search(r'rawQuery\("(SELECT character,count\(\*\).*?GROUP BY character)"',unread).group(1)
assert dict(db.execute(count_sql))=={'tenka':1,'amana':1}
read_sql=re.search(r'execSQL\("(UPDATE messages SET unread=0[^"\n]+)"',unread).group(1)
db.execute(read_sql,(2,'amana','s2'))
assert dict(db.execute(count_sql))=={'tenka':1,'amana':1},'read cannot clear another conversation'
db.execute(read_sql,(2,'tenka','s'))
db.execute(read_sql,(2,'tenka','s'))
assert dict(db.execute(count_sql))=={'amana':1},'repeat read is idempotent'
db.execute("INSERT INTO reach_tasks(id,character,session,day,zone,due,expires,created,revision,status,token) VALUES('broken','tenka','s','2026-10-26','Asia/Shanghai',200,1000,100,1,'RUNNING','z')")
recover=re.search(r'execSQL\("(UPDATE reach_tasks SET status=\'FAILED\'[^"\n]+)"',source).group(1)
db.execute(recover,(500,))
assert db.execute("SELECT status,token FROM reach_tasks WHERE id='broken'").fetchone()==('FAILED',''),'interrupted requests do not auto repeat'
expire=re.search(r'execSQL\("(UPDATE reach_tasks SET status=\'SKIPPED\',finished=\?,error=\'已过有效期[^"\n]+)"',source).group(1)
db.execute("UPDATE reach_tasks SET status='PENDING',expires=400 WHERE id='broken'")
db.execute(expire,(500,500))
assert db.execute("SELECT status FROM reach_tasks WHERE id='broken'").fetchone()==('SKIPPED',)
assert 'message_id' in chat and 'if(oldVersion<12)' in chat and '.put("version",14)' in chat
assert 'v.put("unread",1);if(lifeEffects!=null)v.put("generated_at",lifeEffects.optLong("generatedAt"));long messageId=db.insertOrThrow' in chat
print('PASS SQLite v11→v12 history preservation, day uniqueness, conditional worker claims, transactional message/task rollback, unread isolation/idempotence, interrupted failure and expiry.')

# Production stop predicate invalidates a running token; completion must no longer match.
db.execute("UPDATE reach_tasks SET status='RUNNING',token='late' WHERE id='broken'")
stop=re.search(r'static void stop.*?execSQL\("([^"]+)"',source).group(1)
db.execute(stop,(600,'user disabled'))
assert db.execute("SELECT 1 FROM reach_tasks WHERE id='broken' AND status='RUNNING' AND token='late'").fetchone() is None
import tempfile
with tempfile.TemporaryDirectory() as d:
    saved=Path(d)/'eidolon.db';db.commit();disk=sqlite3.connect(saved);db.backup(disk);disk.close();restored=sqlite3.connect(saved)
    assert dict(restored.execute(count_sql))=={'amana':1}
    assert restored.execute("SELECT status,message_id FROM reach_tasks WHERE id='t'").fetchone()==('COMPLETED',2)
    restored.close()
print('PASS actual stop predicate fences old token; unread and completed message linkage persist across SQLite close/reopen. Android recovery remains unverified.')


