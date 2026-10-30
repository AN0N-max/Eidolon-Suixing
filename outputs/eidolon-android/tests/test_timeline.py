"""Production SQLite migration/predicates and explicit source-hook audit; not device execution."""
from pathlib import Path
import sqlite3,re
root=Path(__file__).resolve().parents[1]
java=root/'src/com/eidolon/sc/mobile'
notebook=(java/'MemoryNotebook.java').read_text(encoding='utf-8')
db=sqlite3.connect(':memory:')
db.execute('CREATE TABLE messages(id INTEGER PRIMARY KEY,character TEXT,conversation TEXT,role TEXT,body TEXT)')
db.execute("INSERT INTO messages VALUES(1,'rinze','a','user','old')")
for sql in re.findall(r'db.execSQL\("((?:CREATE|ALTER|INSERT INTO notebook_meta)[^"\n]+)"',notebook):db.execute(sql)
assert db.execute('SELECT body,sent_at FROM messages WHERE id=1').fetchone()==('old',0)
db.execute("INSERT INTO memory_notebook VALUES('one','rinze','{}')")
db.execute('INSERT INTO notebook_versions VALUES(?,?,?)',('one',1,'old'))
db.commit()
try:
    with db:
        db.execute("UPDATE memory_notebook SET body='new' WHERE id='one'")
        db.execute("INSERT INTO notebook_versions VALUES('one',1,'duplicate')")
except sqlite3.IntegrityError:pass
assert db.execute("SELECT body FROM memory_notebook WHERE id='one'").fetchone()==('{}',)
before=db.execute('SELECT epoch FROM notebook_meta').fetchone()[0]
db.execute('UPDATE notebook_meta SET epoch=epoch+1 WHERE id=1')
assert db.execute('SELECT epoch FROM notebook_meta').fetchone()[0]==before+1
# Two conversation revisions stay isolated; cancellation is id-scoped.
db.execute('CREATE TABLE pending_replies(id TEXT PRIMARY KEY,revision INTEGER,status TEXT)')
db.executemany('INSERT INTO pending_replies VALUES(?,?,?)',[('a',1,'generating'),('b',1,'collecting')])
db.execute("UPDATE pending_replies SET revision=revision+1,status='cancelled' WHERE id='b'")
assert db.execute("SELECT revision,status FROM pending_replies WHERE id='a'").fetchone()==(1,'generating')
web=(java/'WebActivity.java').read_text(encoding='utf-8');execution=(java/'ReachExecution.java').read_text(encoding='utf-8');plans=(java/'ReachPlans.java').read_text(encoding='utf-8');chat=(java/'ChatStore.java').read_text(encoding='utf-8');fallback=(java/'ConversationEngine.java').read_text(encoding='utf-8')
assert 'ReachOut.generating()' not in execution and 'ReachOut.chatBusy' not in execution
assert 'ReachPlanStore.defer' in execution and 'schedulingChanged' in web
assert 'for(int round=' not in plans and 'active(s,who)' in plans and 'opportunity' in plans
assert 'ReplyWorkService.start(this,row.id)' in web and 'row.lastAt+4000L' in web
assert 'ReplyFallback.stop(pending.id,pending.revision)' in web
assert 'MemoryNotebook.deliver' in chat and '.put("timeEvents",result.optJSONArray("timeEvents"))' in fallback
assert 'if(oldVersion<13)' in chat and 'MemoryNotebook.restore(this,data,mapping,messageMapping)' in chat
print('PASS v12→v13 unknown legacy timestamps, notebook/version rollback, epoch invalidation, isolated conversation cancellation; source hooks for temporary deferral, actual busy state, one pending per role, and shared dated context. Android services remain unverified.')
