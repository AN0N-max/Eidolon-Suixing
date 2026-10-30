"""Production migration DDL and SQLite transaction model; not Android execution."""
import sqlite3,re,json
from pathlib import Path
src=Path(__file__).resolve().parents[1]/'src/com/eidolon/sc/mobile'
store=(src/'AppealStore.java').read_text(encoding='utf-8');chat=(src/'ChatStore.java').read_text(encoding='utf-8')
db=sqlite3.connect(':memory:')
db.execute('CREATE TABLE messages(id INTEGER PRIMARY KEY,character TEXT,conversation TEXT,role TEXT,body TEXT,reach_at INTEGER DEFAULT 0)')
db.execute("INSERT INTO messages VALUES(1,'mano','chat','assistant','old reply',0)")
ddl=re.search(r'db.execSQL\("(ALTER TABLE messages [^"]+)"\)',store).group(1);db.execute(ddl)
assert db.execute('SELECT body,judgment FROM messages').fetchone()==('old reply','')
db.execute('CREATE TABLE personal(body TEXT NOT NULL)');db.execute('INSERT INTO personal VALUES(?)',(json.dumps({'score':78,'used':0}),));db.commit()
record=json.dumps({'hurt':2,'appealed':False});db.execute('UPDATE messages SET judgment=?',(record,));db.commit()
try:
 with db:
  db.execute('UPDATE messages SET judgment=? WHERE id=1 AND judgment=?',(json.dumps({'hurt':2,'appealed':True}),record));db.execute('UPDATE personal SET body=?',(json.dumps({'score':80,'used':1}),));raise RuntimeError('simulated write failure')
except RuntimeError: pass
assert db.execute('SELECT judgment FROM messages').fetchone()[0]==record
assert json.loads(db.execute('SELECT body FROM personal').fetchone()[0])=={'score':78,'used':0}
with db:
 assert db.execute('UPDATE messages SET judgment=? WHERE id=1 AND judgment=?',('consumed',record)).rowcount==1
 db.execute('UPDATE personal SET body=?',(json.dumps({'score':80,'used':1}),))
assert db.execute('UPDATE messages SET judgment=? WHERE id=1 AND judgment=?',('twice',record)).rowcount==0
assert 'if(oldVersion<11)AppealStore.create(db)' in chat and 'null, 15)' in chat
assert chat.index('AppealData.record(effects,now)')>chat.index('if(lastUserId(p.session)!=p.lastUserId)')
for name in ('ConversationEngine.java',):
 code=(src/name).read_text(encoding='utf-8');assert 'chatJudgment' in code and 'AppealData.prompt' in code and 'appealExplanation' in code
assert 'db.beginTransaction()' in store and 's.hasBlockingPending(who)' in store and 'latest(s,who)!=id' in store
print('PASS v10 migration preserves old messages without inferred deductions; atomic rollback of refund/quota/message mark; once-only CAS and native latest/pending guards. SQLite model only.')

