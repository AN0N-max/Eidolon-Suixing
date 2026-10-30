"""SQLite transaction model + production hooks. Does not emulate Android JobScheduler."""
import json
import re
import sqlite3
from pathlib import Path

root = Path(__file__).parents[1]
src = root / 'src/com/eidolon/sc/mobile'
plan_source = (src / 'LetterPlanStore.java').read_text(encoding='utf-8')
writing_source = (src / 'WritingStore.java').read_text(encoding='utf-8')
db = sqlite3.connect(':memory:')
for source in [writing_source, plan_source]:
    for ddl in re.findall(r'db\.execSQL\("(CREATE TABLE [^"]+)"\)', source):
        db.execute(ddl)

db.execute('CREATE TABLE messages(id INTEGER PRIMARY KEY,body TEXT)')
db.execute("INSERT INTO messages VALUES(1,'old chat remains')")
db.commit()
row = dict(character='mano', date='2026-10-24', state='ready', text='staged private body', deliverAt=1000, revision=0)
key = 'mano/2026-10-24'

with db:
    db.execute('INSERT INTO letter_plans VALUES(?,?,?)', (key,json.dumps(row),0))
    # INSERT OR IGNORE prevents app reopens from redrawing the same recipient/day.
    ignored=db.execute('INSERT OR IGNORE INTO letter_plans VALUES(?,?,?)', (key,'redraw',0)).rowcount
    assert ignored == 0
assert db.execute('SELECT count(*) FROM writings').fetchone()[0] == 0
assert db.execute('SELECT count(*) FROM messages').fetchone()[0] == 1
assert db.execute('SELECT count(*) FROM writings WHERE is_read=0').fetchone()[0] == 0
assert json.loads(db.execute('SELECT body FROM letter_plans').fetchone()[0])['text'] == 'staged private body'
print('PASS: schema addition preserves chat; staged text has no inbox/unread presence; daily draw persists once.')

def deliver(now, fail=False):
    with db:
        stored=db.execute('SELECT body,revision FROM letter_plans WHERE id=?',(key,)).fetchone()
        letter=json.loads(stored[0])
        if letter['state'] != 'ready' or letter['deliverAt'] > now:
            return False
        next_row=dict(letter, state='delivered', text='', revision=stored[1]+1)
        changed=db.execute('UPDATE letter_plans SET body=?,revision=? WHERE id=? AND revision=?',
                           (json.dumps(next_row),next_row['revision'],key,stored[1])).rowcount
        assert changed == 1
        db.execute('INSERT INTO writings VALUES(?,?,?,?,?,?)',
                   ('letter/'+key,'letter','mano','2026-10-24',None if fail else letter['text'],0))
    return True

assert not deliver(999)
try:
    deliver(1000, fail=True)
except sqlite3.IntegrityError:
    pass
else:
    raise AssertionError('broken body was inserted')
assert json.loads(db.execute('SELECT body FROM letter_plans').fetchone()[0])['state'] == 'ready'
assert db.execute('SELECT count(*) FROM writings').fetchone()[0] == 0
assert deliver(1000)
assert not deliver(1001)
assert db.execute('SELECT count(*) FROM writings WHERE is_read=0').fetchone()[0] == 1
assert db.execute('SELECT body FROM writings').fetchone()[0] == 'staged private body'
assert json.loads(db.execute('SELECT body FROM letter_plans').fetchone()[0])['text'] == ''
assert db.execute('UPDATE letter_plans SET body=? WHERE id=? AND revision=0',('late generation',key)).rowcount == 0
print('PASS: due boundary; delivery rollback preserves staged letter; one-shot delivery and revision fencing; unread becomes durable only on arrival.')

with db:
    db.execute('UPDATE writings SET is_read=1')
payload=json.loads(json.dumps([json.loads(x[0]) for x in db.execute('SELECT body FROM letter_plans')]))
with db:
    db.execute('DELETE FROM letter_plans')
    for r in payload:
        db.execute('INSERT INTO letter_plans VALUES(?,?,?)',(r['character']+'/'+r['date'],json.dumps(r),r['revision']))
assert not deliver(2000)
assert db.execute('SELECT is_read FROM writings').fetchone()[0] == 1

# Fresh restore of prepared data starts without inserting into the delivered inbox.
with db:
    db.execute('DELETE FROM writings')
    db.execute('DELETE FROM letter_plans')
    db.execute('INSERT INTO letter_plans VALUES(?,?,?)',(key,json.dumps(row),0))
assert db.execute('SELECT count(*) FROM writings').fetchone()[0] == 0
assert deliver(2000)
assert not deliver(2001)
print('PASS: restored delivered plans cannot duplicate unread; restored prepared text waits for a single transactional arrival.')

engine=(src/'WritingEngine.java').read_text(encoding='utf-8')
letters=(src/'LettersEngine.java').read_text(encoding='utf-8')
job=(src/'LetterJob.java').read_text(encoding='utf-8')
chat=(src/'ChatStore.java').read_text(encoding='utf-8')
receiver=(src/'ReplyRescheduleReceiver.java').read_text(encoding='utf-8')
web=(src/'WebActivity.java').read_text(encoding='utf-8')
assert 'null, 15)' in chat and 'if(oldVersion<9)LetterPlanStore.create(db)' in chat
assert '"letterPlans",LetterPlanStore.all(this)' in chat and 'db.delete("letter_plans",null,null)' in chat
assert 'LetterPlanStore.ready(store,letterPlan,text,epoch)' in engine
assert 'false,attempt,epoch)' in letters and 'JSONObject letterPlan,long epoch)' in engine
assert engine.index('if(letterPlan!=null){if(!LetterPlanStore.ready') < engine.index('WritingStore.save(store,row,epoch)')
ui=(src/'UiSnapshots.java').read_text(encoding='utf-8')
assert 'LettersEngine.deliverAvailable(c,s)' in ui and 'LetterPlanStore.all' not in web
assert 'if(network)b.setRequiredNetworkType' in job and 'setPersisted(true)' in job and 'setMinimumLatency' in job
assert 'params.getJobId()==WRITE' in job and 'Thread.currentThread().isInterrupted()' in engine
assert 'LettersEngine.refresh(context)' in receiver and 'TIMEZONE_CHANGED' in receiver
assert 'revision=?' in plan_source and 'expectedEpoch!=epoch.get()' in writing_source
assert 'WritingStore.context' not in letters and 'ModelClient' not in (src/'LetterPlanData.java').read_text(encoding='utf-8')
assert (root/'AndroidManifest.xml').read_text(encoding='utf-8').count('com.eidolon.sc.mobile.LetterJob') == 1
print('PASS: offline delivery, separate persistent jobs, stop/data epoch fences, backup clearing and boot/time recovery hooks. Android behavior still needs device acceptance.')

