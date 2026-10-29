"""Execute the production schema and migration SQL against real SQLite.
This checks SQL/data invariants, not Android UI or SQLiteOpenHelper lifecycle.
"""
from pathlib import Path
import re, sqlite3, json

source=(Path(__file__).parents[1]/'src/com/eidolon/sc/mobile/ChatStore.java').read_text(encoding='utf-8')
statements=re.findall(r'db\.execSQL\("([^"\n]+)"\)',source)
sql=lambda prefix: next(s for s in statements if s.startswith(prefix))
db=sqlite3.connect(':memory:')
db.execute('CREATE TABLE messages(id INTEGER PRIMARY KEY AUTOINCREMENT, character TEXT NOT NULL, role TEXT NOT NULL, body TEXT NOT NULL)')
db.executemany('INSERT INTO messages(character,role,body) VALUES (?,?,?)', [('mano','user','你好'),('mano','assistant','こんにちは'),('hiori','user','hello'),('hiori','assistant','hi')])
before=db.execute('SELECT id,character,role,body FROM messages ORDER BY id').fetchall()
db.execute(sql('ALTER TABLE'))
db.execute(sql('CREATE TABLE conversations'))
db.execute(sql('CREATE INDEX conversation_messages'))
db.execute(sql('INSERT INTO conversations'))
db.execute(sql('UPDATE messages'))
db.execute(sql('CREATE TABLE personal'))
db.execute(sql('CREATE TABLE stories'))
db.execute(sql('CREATE VIRTUAL TABLE story_terms'))
assert db.execute('SELECT count(*) FROM personal').fetchone()[0]==0
assert before==db.execute('SELECT id,character,role,body FROM messages ORDER BY id').fetchall()
assert db.execute('SELECT count(*) FROM conversations').fetchone()[0]==2
assert db.execute("SELECT count(*) FROM messages WHERE conversation='legacy-mano'").fetchone()[0]==2
db.execute("INSERT INTO conversations VALUES ('new-mano','mano','新对话',10)")
assert db.execute("SELECT count(*) FROM messages WHERE conversation='new-mano'").fetchone()[0]==0
db.execute("DELETE FROM messages WHERE conversation='legacy-mano'")
db.execute("DELETE FROM conversations WHERE id='legacy-mano'")
assert db.execute("SELECT count(*) FROM messages WHERE character='hiori'").fetchone()[0]==2
db.commit()
try:
    with db:
        db.execute("INSERT INTO conversations VALUES ('rollback','mano','title',11)")
        db.execute("INSERT INTO messages(character,role,body,conversation) VALUES ('mano','user',NULL,'rollback')")
except sqlite3.IntegrityError:
    pass
assert db.execute("SELECT count(*) FROM conversations WHERE id='rollback'").fetchone()[0]==0
assets=Path(__file__).parents[1]/'assets'
characters=json.loads((assets/'characters.json').read_text(encoding='utf-8'))
assert len({c['id'] for c in characters})==len(characters)
for c in characters:
    assert c['sheet'].strip() and c['facts'].strip() and (assets/c['avatar']).is_file()
print(f'PASS: SQLite migration preserves rows; sessions isolated; deletion isolated; transaction rollback; {len(characters)} character resources valid.')

# Same native story schema: cast filter must not leak another character's episode.
for ident, cast in [('mano-story','|mano|'),('hiori-story','|hiori|')]:
    db.execute('INSERT INTO stories VALUES (?,?,?,?,?)',(ident,ident,'csv',cast,'{}'))
assert [r[0] for r in db.execute('SELECT id FROM stories WHERE instr(cast_ids,?)>0',('|mano|',))]==['mano-story']
db.execute('INSERT INTO personal VALUES (1,?)',('{"draws":7}',))
db.commit()
try:
    with db:
        db.execute("INSERT INTO messages(character,role,body,conversation) VALUES ('mano','user','atomic','new-mano')")
        db.execute('UPDATE personal SET body=? WHERE id=1',('{"draws":8}',))
        db.execute('INSERT INTO stories VALUES (?,?,?,?,?)',('bad',None,'csv','|mano|','{}'))
except sqlite3.IntegrityError:
    pass
assert db.execute("SELECT count(*) FROM messages WHERE body='atomic'").fetchone()[0]==0
assert db.execute('SELECT body FROM personal').fetchone()[0]=='{"draws":7}'
print('PASS: v4 story cast isolation; failed composite transaction rolls back message and reward state.')

db.execute('INSERT INTO story_terms VALUES (?,?)',('mano-story','公园 唱歌'))
db.execute('INSERT INTO story_terms VALUES (?,?)',('hiori-story','公园 练习'))
rows=db.execute('SELECT stories.id FROM stories JOIN story_terms ON stories.id=story_terms.id WHERE story_terms MATCH ? AND instr(cast_ids,?)>0',('"公园" OR "唱歌"','|mano|')).fetchall()
assert rows==[('mano-story',)]
print('PASS: SQLite FTS4 Chinese terms index retrieves only eligible character stories.')

# Upgrade a populated 0.9 database without replacing its messages or personal row.
writing_source=(Path(__file__).parents[1]/'src/com/eidolon/sc/mobile/WritingStore.java').read_text(encoding='utf-8')
old_messages=db.execute('SELECT * FROM messages ORDER BY id').fetchall()
old_personal=db.execute('SELECT * FROM personal').fetchall()
for statement in re.findall(r'db\.execSQL\("(CREATE TABLE [^"\n]+)"\)',writing_source):
    db.execute(statement)
assert old_messages==db.execute('SELECT * FROM messages ORDER BY id').fetchall()
assert old_personal==db.execute('SELECT * FROM personal').fetchall()
row=('letter/mano/2026-10-23','letter','mano','2026-10-23','<ja>手紙</ja><zh>信</zh>',0)
db.execute('INSERT INTO writings VALUES(?,?,?,?,?,?)',row)
db.execute('INSERT OR IGNORE INTO writings VALUES(?,?,?,?,?,?)',row)
assert db.execute('SELECT count(*) FROM writings').fetchone()[0]==1
db.execute('UPDATE writings SET is_read=1 WHERE id=?',(row[0],))
assert db.execute('SELECT is_read FROM writings').fetchone()[0]==1
db.commit()
try:
    with db:
        db.execute("DELETE FROM writings")
        db.execute('INSERT INTO writings VALUES(?,?,?,?,?,?)',('bad',None,'mano','2026-10-23','x',0))
except sqlite3.IntegrityError:
    pass
assert db.execute('SELECT count(*) FROM writings').fetchone()[0]==1
print('PASS: v6 upgrade preserves old messages/profile; duplicate daily writing prevented; read state durable; failed writing replacement rolls back.')

schedule_source=(Path(__file__).parents[1]/'src/com/eidolon/sc/mobile/ScheduleStore.java').read_text(encoding='utf-8')
before_messages=db.execute('SELECT * FROM messages ORDER BY id').fetchall()
before_writing=db.execute('SELECT * FROM writings').fetchall()
for statement in re.findall(r'db\.execSQL\("(CREATE TABLE [^"\n]+)"\)',schedule_source):
    db.execute(statement)
assert db.execute('SELECT * FROM messages ORDER BY id').fetchall()==before_messages
assert db.execute('SELECT * FROM writings').fetchall()==before_writing
db.execute('INSERT INTO schedules VALUES(?,?,?,?)',('mano/2026-10-23','mano','2026-10-23','{"plan":null,"error":"failed"}'))
assert db.execute('SELECT count(*) FROM schedules WHERE date=?',('2026-10-23',)).fetchone()[0]==1
db.execute('INSERT OR REPLACE INTO schedules VALUES(?,?,?,?)',('mano/2026-10-23','mano','2026-10-23','{"plan":{"wake":"07:00"}}'))
assert db.execute('SELECT count(*) FROM schedules WHERE date=?',('2026-10-23',)).fetchone()[0]==1
db.commit()
try:
    with db:
        db.execute('DELETE FROM schedules')
        db.execute('INSERT INTO schedules VALUES(?,?,?,?)',('invalid','mano','2026-10-23',None))
except sqlite3.IntegrityError:
    pass
assert db.execute('SELECT count(*) FROM schedules').fetchone()[0]==1
routines=json.loads((assets/'routines.json').read_text(encoding='utf-8'))
assert len(routines['profiles'])==28 and set(routines['profiles']) <= {c['id'] for c in characters}
print('PASS: v7 upgrade keeps v6 messages/writings; one plan per day including failed attempts; failed restore rolls back; 28 Hibi routines match local characters.')

# v8 commitments live in personal JSON; diary events and message changes share its transaction.
prior_personal=db.execute('SELECT body FROM personal WHERE id=1').fetchone()[0]
prior_messages=db.execute('SELECT count(*) FROM messages').fetchone()[0]
db.commit()
try:
    with db:
        db.execute("INSERT INTO messages(character,role,body,conversation) VALUES ('mano','user','commitment reply','new-mano')")
        db.execute('UPDATE personal SET body=? WHERE id=1',('{"promises":[{"state":"completed"}],"affinity":{"mano":52}}',))
        db.execute('INSERT INTO writing_events VALUES(?,?,?,?)',('invalid','mano','2026-10-23',None))
except sqlite3.IntegrityError:
    pass
assert db.execute('SELECT body FROM personal WHERE id=1').fetchone()[0]==prior_personal
assert db.execute('SELECT count(*) FROM messages').fetchone()[0]==prior_messages
assert db.execute("SELECT count(*) FROM writing_events WHERE id='invalid'").fetchone()[0]==0
payload={"promises":[{"id":"p-1","character":"mano","state":"completed","evidence":"我明天给你买书"}]}
with db:
    db.execute('UPDATE personal SET body=? WHERE id=1',(json.dumps(payload,ensure_ascii=False),))
    db.execute('INSERT INTO writing_events VALUES(?,?,?,?)',('mano/2026-10-23','mano','2026-10-23','制作人兑现了约定：买书。'))
assert json.loads(db.execute('SELECT body FROM personal WHERE id=1').fetchone()[0])==payload
assert db.execute("SELECT count(*) FROM writing_events WHERE character='mano'").fetchone()[0]==1
print('PASS: v8 promise JSON persists with evidence; failing diary insert rolls back message, commitment and affinity together.')

# v9 delayed replies: durable rows, revision fencing, and one-shot transactional delivery.
pending_ddl=sql('CREATE TABLE pending_replies')
db.execute(pending_ddl)
for ix in statements:
    if ix.startswith('CREATE INDEX pending_due'):
        db.execute(ix)
# User input is committed before generation and a later batch supersedes the old revision.
db.execute("INSERT INTO messages(character,role,body,conversation) VALUES ('mano','user','第一条','new-mano')")
first_id=db.execute('SELECT max(id) FROM messages').fetchone()[0]
db.execute("INSERT INTO pending_replies(id,character,session,revision,first_user_id,last_user_id,last_at,status) VALUES ('new-mano','mano','new-mano',1,?,?,100,'collecting')",(first_id,first_id))
db.execute("INSERT INTO messages(character,role,body,conversation) VALUES ('mano','user','第二条','new-mano')")
last_id=db.execute('SELECT max(id) FROM messages').fetchone()[0]
db.execute("UPDATE pending_replies SET revision=revision+1,last_user_id=?,last_at=200,status='collecting',answer='',effects='',due_at=0 WHERE id='new-mano'",(last_id,))
assert db.execute("SELECT revision,status FROM pending_replies WHERE id='new-mano'").fetchone()==(2,'collecting')
# A late model answer for revision 1 cannot transition revision 2 or overwrite its payload.
assert db.execute("UPDATE pending_replies SET status='waiting',answer='stale' WHERE id='new-mano' AND revision=1 AND status='generating'").rowcount==0
assert db.execute("SELECT answer FROM pending_replies WHERE id='new-mano'").fetchone()[0]==''
# Delivery removes exactly one matching waiting row in the same transaction as assistant insertion.
db.execute("UPDATE pending_replies SET status='waiting',answer='reply',effects='{}',due_at=10 WHERE id='new-mano' AND revision=2")
with db:
    claimed=db.execute("DELETE FROM pending_replies WHERE id='new-mano' AND revision=2 AND status='waiting'").rowcount
    assert claimed==1
    db.execute("INSERT INTO messages(character,role,body,conversation) VALUES ('mano','assistant','reply','new-mano')")
assert db.execute("DELETE FROM pending_replies WHERE id='new-mano' AND revision=2 AND status='waiting'").rowcount==0
assert db.execute("SELECT count(*) FROM messages WHERE conversation='new-mano' AND role='assistant'").fetchone()[0]==1
assert db.execute("SELECT group_concat(body,' ') FROM messages WHERE conversation='new-mano' AND role='user'").fetchone()[0]=='第一条 第二条'
# Tests also guard the platform edges the SQLite model cannot emulate.
root=Path(__file__).parents[1]
manifest=(root/'AndroidManifest.xml').read_text(encoding='utf-8')
job=(root/'src/com/eidolon/sc/mobile/PendingReplyJob.java').read_text(encoding='utf-8')
receiver=(root/'src/com/eidolon/sc/mobile/ReplyRescheduleReceiver.java').read_text(encoding='utf-8')
fallback=(root/'src/com/eidolon/sc/mobile/ReplyFallback.java').read_text(encoding='utf-8')
for name in re.findall(r'readArray\(c,"([^"]+)"\)',fallback):
    assert isinstance(json.loads((root/'assets'/name).read_text(encoding='utf-8')),list),f'Background generation must read {name} using its JSON type'
web=(root/'src/com/eidolon/sc/mobile/WebActivity.java').read_text(encoding='utf-8')
assert 'setPersisted(true)' in job and 'setMinimumLatency' in job
assert 'BOOT_COMPLETED' in receiver and 'TIMEZONE_CHANGED' in receiver and 'requeueInterrupted' in receiver
assert 'pending.revision!=revision' in web and 'markReady(id,revision' in web
assert 'ReplyFallback.stop(id,row.revision)' in job and 'requeueInterrupted(id,row.revision' in job
assert 'client.cancel()' in fallback and 'row.revision!=revision' in fallback and 'STOPPED.contains(key)' in fallback
assert 'JevClient.ask(url,token,model,state,questions,client)' in fallback
store_java=(root/'src/com/eidolon/sc/mobile/ChatStore.java').read_text(encoding='utf-8')
assert "status='waiting' AND answer<>''" in store_java and "status='failed' AND answer<>''" in store_java
assert 'markScheduleFailed' in job and 'markScheduleFailed' in web and 'retryReady(conversation,pending.revision' in web
assert 'PendingReplyJob' in manifest and 'RECEIVE_BOOT_COMPLETED' in manifest
assert "status IN ('collecting','generating','waiting','failed')" in source
print('PASS: v9 pending-reply schema, immediate user persistence, latest revision wins, stale response fenced, one-shot delivery, persisted recovery, stopped HTTP cancellation, visible scheduler failures and proactive-chat guard.')
