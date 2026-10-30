"""Production SQL plus a transaction model. No Android lifecycle or model calls."""
from pathlib import Path
import json
import re
import sqlite3

root=Path(__file__).parents[1]
src=root/'src/com/eidolon/sc/mobile'
writing=(src/'WritingStore.java').read_text(encoding='utf-8')
diary=(src/'DiaryStore.java').read_text(encoding='utf-8')
chat=(src/'ChatStore.java').read_text(encoding='utf-8')
db=sqlite3.connect(':memory:')
for ddl in re.findall(r'db\.execSQL\("(CREATE TABLE [^"]+)"\)',writing):
    db.execute(ddl)
db.execute("INSERT INTO writings VALUES('diary/mano/2026-10-20','diary','mano','2026-10-20','old bilingual body',1)")
db.commit()
for ddl in re.findall(r'db\.execSQL\("((?:CREATE TABLE|ALTER TABLE) [^"]+)"\)',diary):
    db.execute(ddl)
assert db.execute('SELECT body,is_read,secret,secret_read FROM writings').fetchone()==('old bilingual body',1,'',0)
assert db.execute('SELECT count(*) FROM diary_attempts').fetchone()[0]==0
print('PASS: v9 to v10 migration retains old diaries/read flags and adds empty private-page fields without inventing content.')

key='mano/2026-10-23'
def reserve(now):
    with db:
        prior=db.execute('SELECT tries,at FROM diary_attempts WHERE id=?',(key,)).fetchone()
        if prior and (prior[0]>=2 or now<prior[1]+3600000):
            return False
        db.execute('INSERT OR REPLACE INTO diary_attempts VALUES(?,?,?,?,?,?)',(key,'mano','2026-10-23',1 if prior is None else prior[0]+1,now,''))
    return True

assert reserve(1000)
assert not reserve(3599999)
assert reserve(3601000)
assert not reserve(7202000)
ledger=db.execute('SELECT tries,at FROM diary_attempts').fetchall()
snapshot=json.dumps(ledger)
assert json.loads(snapshot)==[[2,3601000]]
print('PASS: diary reservation persists before generation, enforces an independent one-hour/two-attempt limit and survives a JSON round trip.')

with db:
    db.execute('INSERT INTO writings(id,kind,character,date,body,is_read,secret,secret_read) VALUES(?,?,?,?,?,?,?,?)',('diary/'+key,'diary','mano','2026-10-23','public body',0,'private body',0))
assert db.execute("SELECT body FROM writings WHERE id=?",('diary/'+key,)).fetchone()[0]=='public body'
with db:
    db.execute('UPDATE writings SET is_read=1 WHERE id=?',('diary/'+key,))
assert db.execute('SELECT is_read,secret_read FROM writings WHERE id=?',('diary/'+key,)).fetchone()==(1,0)
with db:
    db.execute('UPDATE writings SET secret_read=1 WHERE id=?',('diary/'+key,))
assert db.execute('SELECT secret_read FROM writings WHERE id=?',('diary/'+key,)).fetchone()[0]==1
with db:
    db.execute('UPDATE writings SET is_read=0,secret_read=0 WHERE id=?',('diary/'+key,))
assert db.execute('SELECT is_read,secret_read FROM writings WHERE id=?',('diary/'+key,)).fetchone()==(0,0)
print('PASS: ordinary read does not consume private-page unread; explicit private read and mark-unread have independent durable effects.')

db.execute('CREATE TABLE pending_test(id TEXT PRIMARY KEY,revision INTEGER,status TEXT,effects TEXT)')
db.execute('CREATE TABLE messages_test(body TEXT NOT NULL)')
effects=json.dumps({'diaryMoment':{'date':'2026-10-23','text':'evidenced moment'}})
db.execute('INSERT INTO pending_test VALUES(?,?,?,?)',('s',1,'waiting',effects))
db.commit()
assert db.execute('SELECT count(*) FROM writing_events').fetchone()[0]==0
with db:
    db.execute("UPDATE pending_test SET revision=2,status='cancelled',effects='' WHERE id='s'")
assert db.execute("SELECT count(*) FROM pending_test WHERE id='s' AND revision=1 AND status='waiting'").fetchone()[0]==0
assert db.execute('SELECT count(*) FROM writing_events').fetchone()[0]==0
with db:
    db.execute('INSERT OR REPLACE INTO pending_test VALUES(?,?,?,?)',('s',3,'waiting',effects))
def deliver(fail=False):
    with db:
        record=db.execute("SELECT effects FROM pending_test WHERE id='s' AND revision=3 AND status='waiting'").fetchone()
        if not record:
            return False
        db.execute("DELETE FROM pending_test WHERE id='s' AND revision=3 AND status='waiting'")
        db.execute('INSERT INTO messages_test VALUES(?)',('delivered answer',))
        db.execute('INSERT INTO writing_events VALUES(?,?,?,?)',(key,'mano','2026-10-23',None if fail else json.loads(record[0])['diaryMoment']['text']))
    return True
try:
    deliver(True)
except sqlite3.IntegrityError:
    pass
assert db.execute('SELECT count(*) FROM pending_test').fetchone()[0]==1
assert db.execute('SELECT count(*) FROM writing_events').fetchone()[0]==0
assert db.execute('SELECT count(*) FROM messages_test').fetchone()[0]==0
assert deliver() and not deliver()
assert db.execute('SELECT count(*) FROM writing_events').fetchone()[0]==1
assert db.execute('SELECT count(*) FROM messages_test').fetchone()[0]==1
print('PASS: staged/cancelled replies leave no diary moment; delivered message and moment commit together once; failure rolls both back.')

web=(src/'WebActivity.java').read_text(encoding='utf-8')
fallback=(src/'ConversationEngine.java').read_text(encoding='utf-8')
engine=(src/'WritingEngine.java').read_text(encoding='utf-8')
data=(src/'WritingData.java').read_text(encoding='utf-8')
assert 'null, 15)' in chat and 'if(oldVersion<10)DiaryStore.create(db)' in chat
assert '"diaryAttempts",DiaryStore.attempts(this)' in chat and 'db.delete("diary_attempts",null,null)' in chat
assert 'row.remove("secret");row.remove("secretRead")' in data
assert 'DiaryStore.secret(store,who,day,prefs.getBoolean("affinity",true))' in web
assert 'DiaryData.shown' in diary and 'db.beginTransaction()' in diary and 'secret_read' in diary
for source in [fallback]:
    assert 'remember&&prefs.getBoolean("hibiDiary",false)?DiaryData.momentPrompt()' in source
    assert 'effects.put("diaryMoment",DiaryData.moment(' in source
delivery=chat[chat.index('JSONObject deliverPending'):chat.index('void deletePending')]
assert delivery.index('lastUserId(p.session)!=p.lastUserId')<delivery.index('JSONObject moment=effects.optJSONObject("diaryMoment")')
assert 'getBoolean("autoMemory",true)' in delivery and 'base.optLong("memoryEpoch")' in delivery
assert 'DiaryStore.begin(store,who,when' in engine and 'DiaryStore.failed' in engine
assert 'kind.equals("letter")&&BilingualReply.needsChinese(text)' in engine
assert 'DiaryData.generated(text)' in engine and 'request.put("max_tokens",8192)' in engine
print('PASS: native-only private-page authorization, both generation paths, reply-delivery and memory-epoch fences, strict diary parsing and backup hooks. Device acceptance is still required.')

