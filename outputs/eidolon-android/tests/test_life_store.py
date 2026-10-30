"""Production v14 SQL, durable roundtrip and transactional rollback. No Android lifecycle claim."""
from pathlib import Path
import json,re,sqlite3,tempfile
base=Path(__file__).resolve().parents[1];java=base/'src/com/eidolon/sc/mobile'
life=(java/'LifeStore.java').read_text(encoding='utf-8');chat=(java/'ChatStore.java').read_text(encoding='utf-8')
schema=re.findall(r'db.execSQL\("((?:CREATE TABLE character_life|ALTER TABLE messages ADD COLUMN generated_at)[^"]+)"',life)
assert len(schema)==2 and 'if(oldVersion<14)LifeStore.create(db)' in chat
fixture=base.parents[1]/'work/life-sample-1.15.json'
state=json.loads(fixture.read_text(encoding='utf-8'))
with tempfile.TemporaryDirectory(prefix='eidolon-life-') as folder:
    path=Path(folder)/'app.db';db=sqlite3.connect(path,isolation_level=None);db.execute('PRAGMA journal_mode=WAL')
    db.execute('CREATE TABLE messages(id INTEGER PRIMARY KEY,character TEXT,role TEXT,body TEXT,unread INTEGER DEFAULT 0,sent_at INTEGER DEFAULT 0,time_zone TEXT)')
    db.execute("INSERT INTO messages(id,character,role,body) VALUES(1,'mano','user','synthetic original chat')")
    for sql in schema:db.execute(sql)
    assert db.execute('SELECT body,generated_at FROM messages WHERE id=1').fetchone()==('synthetic original chat',0)
    db.execute('INSERT INTO character_life VALUES(?,?,?)',('mano',state['revision'],json.dumps(state,ensure_ascii=False)))
    pending=re.search(r'db.execSQL\("(CREATE TABLE pending_replies[^"]+)"',chat).group(1);db.execute(pending)
    db.execute("INSERT INTO pending_replies(id,character,session,revision,first_user_id,last_user_id,last_at,status) VALUES('s','mano','s',4,1,1,1,'waiting')")
    old=db.execute('SELECT body FROM character_life').fetchone()[0]
    # The same conditional claim used by deliverPending fences stale/repeated delivery.
    claim="DELETE FROM pending_replies WHERE id=? AND revision=? AND status='waiting'"
    assert '"id=? AND revision=? AND status=\'waiting\'"' in chat
    db.execute('BEGIN');assert db.execute(claim,('s',3)).rowcount==0;db.rollback()
    db.execute('BEGIN');assert db.execute(claim,('s',4)).rowcount==1
    db.execute("INSERT INTO messages VALUES(2,'mano','assistant','synthetic answer',1,200,'Asia/Shanghai',100)")
    db.execute("UPDATE character_life SET revision=revision+1,body='changed' WHERE character='mano'")
    db.rollback()
    assert db.execute('SELECT count(*) FROM messages').fetchone()[0]==1
    assert db.execute('SELECT body FROM character_life').fetchone()[0]==old
    assert db.execute('SELECT status FROM pending_replies').fetchone()[0]=='waiting'
    db.execute('BEGIN');assert db.execute(claim,('s',4)).rowcount==1
    db.execute("INSERT INTO messages VALUES(2,'mano','assistant','synthetic answer',1,200,'Asia/Shanghai',100)")
    db.execute('UPDATE character_life SET revision=revision+1 WHERE character=?',('mano',));db.commit()
    assert db.execute(claim,('s',4)).rowcount==0
    db.close();db=sqlite3.connect(path)
    assert json.loads(db.execute('SELECT body FROM character_life').fetchone()[0])==state
    assert db.execute('SELECT generated_at,sent_at,unread FROM messages WHERE id=2').fetchone()==(100,200,1)
    assert db.execute('SELECT count(*) FROM pending_replies').fetchone()[0]==0
    db.close()
delivery=chat[chat.index('JSONObject deliverPending'):chat.index('void deletePending')]
assert delivery.index('db.beginTransaction()')<delivery.index('LifeStore.delivered')<delivery.index('db.setTransactionSuccessful();\n            return')
assert '.put("version",15)' in chat and 'LifeStore.restore(this,data,messageMapping)' in chat
for name in ['ConversationEngine.java','ReachExecution.java']:
    source=(java/name).read_text(encoding='utf-8');assert 'LifeStore.snapshot' in source and 'ChatEnvelope.parse' in source
    assert 'lifeAnswer.effects' in source
for name in ['LifeData.java','LifeStore.java','BehaviorPlanner.java','ChatEnvelope.java']:
    assert 'new ModelClient(' not in (java/name).read_text(encoding='utf-8')
print('PASS production SQLite v14 migration/data preservation, generated/delivered timestamps, restart, revision claim, rollback of message+life+pending, backup/delivery hooks and no new life-model client. Not Android lifecycle execution.')
