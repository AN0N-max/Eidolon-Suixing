"""Production SQL on real local SQLite; no Android lifecycle, model, weather or device calls."""
from pathlib import Path
import concurrent.futures,contextlib,json,re,sqlite3,tempfile,uuid
ROOT=Path(__file__).resolve().parent
SRC=ROOT.parent/'src/com/eidolon/sc/mobile'
sources={n:(SRC/(n+'.java')).read_text(encoding='utf-8') for n in ['SocialStore','ScheduleAttempts','ScheduleCandidates','ChatStore','SocialEngine']}
def strings(name):
 return [json.loads('"'+m+'"') for m in re.findall(r'"((?:[^"\\]|\\.)*)"',sources[name])]
def sql(name,prefix):
 return next(s for s in strings(name) if s.startswith(prefix))
ddl=[]
for name in ['SocialStore','ScheduleAttempts']:ddl.extend(s for s in strings(name) if s.startswith(('CREATE TABLE ','CREATE INDEX ')))
now=1793751600000;day='2026-11-04'
def connect(path):
 c=sqlite3.connect(path,timeout=15,isolation_level=None);c.execute('PRAGMA journal_mode=WAL');return c
def fresh(path):
 c=connect(path)
 for table in ['messages','personal','schedules','call_sessions','media_assets']:
  c.execute(f'CREATE TABLE {table}(id TEXT PRIMARY KEY, body TEXT NOT NULL)');c.execute(f'INSERT INTO {table} VALUES (?,?)',('fixture','private-preserved-'+table))
 c.execute('PRAGMA user_version=17');return c
def prior(c):return {n:c.execute(f'SELECT * FROM {n}').fetchall() for n in ['messages','personal','schedules','call_sessions','media_assets']}
def migrate(c):
 c.execute('BEGIN IMMEDIATE')
 try:
  for statement in ddl:c.execute(statement)
  c.execute('PRAGMA user_version=18');c.execute('COMMIT')
 except BaseException:c.execute('ROLLBACK');raise
def add_task(c,ident,kind='post',target='',who='mano'):
 c.execute("INSERT INTO social_tasks(id,character,kind,target,day,due,expires,state,body) VALUES (?,?,?,?,?,?,?,'PENDING','{}')",(ident,who,kind,target,day,now-1,now+3600000))
claim_where=sql('SocialStore',"id=? AND state='PENDING' AND due<=?")
finish_where=sql('SocialStore',"id=? AND state='RUNNING' AND token=?")
def claim(path,ident,limit=3):
 c=connect(path);token=str(uuid.uuid4());c.execute('BEGIN IMMEDIATE')
 try:
  row=c.execute('SELECT kind,character FROM social_tasks WHERE id=?',(ident,)).fetchone()
  used=c.execute(sql('SocialStore','SELECT count(*) FROM social_usage WHERE day='),(day,row[0])).fetchone()[0]
  if used>=limit:c.execute('ROLLBACK');return None
  changed=c.execute("UPDATE social_tasks SET state='RUNNING',token=?,started=? WHERE "+claim_where,(token,now,ident,now,now)).rowcount
  if changed!=1:c.execute('ROLLBACK');return None
  c.execute('INSERT INTO social_usage(id,day,kind,character,at) VALUES (?,?,?,?,?)',(ident,day,row[0],row[1],now));c.execute('COMMIT');return token
 except BaseException:c.execute('ROLLBACK');raise
 finally:c.close()
with tempfile.TemporaryDirectory(prefix='eidolon-social-') as temp,contextlib.ExitStack() as resources:
 assert Path(temp).resolve().parent==Path(tempfile.gettempdir()).resolve() and Path(temp).name.startswith('eidolon-social-')
 path=Path(temp)/'data.sqlite';c=fresh(path);resources.callback(c.close);before=prior(c);migrate(c)
 assert c.execute('PRAGMA user_version').fetchone()[0]==18 and before==prior(c)
 assert len([r for r in c.execute("SELECT name FROM sqlite_master WHERE type='table'") if r[0].startswith('social_') or r[0]=='schedule_attempts'])==8
 print('PASS v17-to18 production DDL adds8 tables and preserves all protected fixture rows')
 failed=Path(temp)/'rollback.sqlite';r=fresh(failed);resources.callback(r.close);original=prior(r)
 r.execute('BEGIN IMMEDIATE')
 try:
  for statement in ddl:r.execute(statement)
  r.execute(ddl[0]);raise AssertionError('duplicate schema accepted')
 except sqlite3.OperationalError:r.execute('ROLLBACK')
 assert original==prior(r) and r.execute('PRAGMA user_version').fetchone()[0]==17
 assert r.execute("SELECT count(*) FROM sqlite_master WHERE name='social_posts'").fetchone()[0]==0;r.close()
 print('PASS interrupted migration rolls back all additions; v17 data/version remain')
 add_task(c,'same')
 with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:winners=list(pool.map(lambda _:claim(path,'same'),range(8)))
 token=next(v for v in winners if v);assert sum(v is not None for v in winners)==1
 assert c.execute('SELECT count(*) FROM social_usage').fetchone()[0]==1
 assert c.execute("UPDATE social_tasks SET state='PUBLISHED',token='' WHERE "+finish_where,('same','wrong-token')).rowcount==0
 assert c.execute("UPDATE social_tasks SET state='PUBLISHED',token='' WHERE "+finish_where,('same',token)).rowcount==1
 print('PASS concurrent same-task claims create exactly one durable usage record; stale finish rejected')
 add_task(c,'interrupted');assert claim(path,'interrupted')
 c.execute(sql('SocialStore',"UPDATE social_tasks SET state='FAILED',token='',finished="),(now,))
 assert c.execute("SELECT state,token FROM social_tasks WHERE id='interrupted'").fetchone()==('FAILED','')
 assert c.execute(sql('SocialStore',"SELECT id FROM social_tasks WHERE state='PENDING' AND due<="),(str(now),str(now))).fetchone() is None
 assert c.execute('SELECT count(*) FROM social_usage').fetchone()[0]==2
 print('PASS process recovery marks unknown outcome failed without reopening budget or due task')
 for i in range(8):add_task(c,'quota-'+str(i),who='role-'+str(i))
 with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:more=list(pool.map(lambda i:claim(path,'quota-'+str(i)),range(8)))
 assert sum(v is not None for v in more)==1 and c.execute('SELECT count(*) FROM social_usage').fetchone()[0]==3
 print('PASS concurrent distinct-role claims cannot exceed daily quota; failures still count')
 # Unique post/day and one producer comment per post are actual schema constraints.
 p={'id':'p1','character':'mano','day':day,'published':now,'ja':'test','zh':'fixture','liked':False}
 c.execute('INSERT INTO social_posts VALUES (?,?,?,?,?)',('p1','mano',day,now,json.dumps(p)))
 try:c.execute('INSERT INTO social_posts VALUES (?,?,?,?,?)',('p2','mano',day,now,'{}'));raise AssertionError('same-day duplicate')
 except sqlite3.IntegrityError:pass
 comment={'id':'c1','post':'p1','text':'fixture','at':now}
 c.execute('INSERT INTO social_comments VALUES (?,?,?)',('c1','p1',json.dumps(comment)))
 try:c.execute('INSERT INTO social_comments VALUES (?,?,?)',('duplicate','p1','{}'));raise AssertionError('duplicate comment')
 except sqlite3.IntegrityError:pass
 add_task(c,'reply','reply','c1');reply_token=claim(path,'reply',10);assert reply_token
 delete_reply_sql=next(s for s in strings('SocialStore') if s.startswith("UPDATE social_tasks SET state='CANCELLED',token='',finished=") and 'WHERE target=?' in s)
 c.execute('BEGIN IMMEDIATE');c.execute('DELETE FROM social_comments WHERE id=?',('c1',));c.execute(delete_reply_sql,(now,'c1'));c.execute('COMMIT')
 assert c.execute("UPDATE social_tasks SET state='PUBLISHED',token='' WHERE "+finish_where,('reply',reply_token)).rowcount==0
 c.execute('INSERT INTO social_comments VALUES (?,?,?)',('c2','p1',json.dumps({'id':'c2','post':'p1','text':'new','at':now})))
 assert c.execute('UPDATE social_comments SET body=? WHERE id=?',('old-reply','c1')).rowcount==0
 assert json.loads(c.execute("SELECT body FROM social_comments WHERE id='c2'").fetchone()[0])['text']=='new'
 print('PASS delete/recomment invalidates late reply token/UUID while retaining paid usage')
 # Native queue ignores waiting/failed delivery rows; actual collecting/generating blocks it.
 c.execute('CREATE TABLE pending_replies(status TEXT)')
 busy_sql=sql('ScheduleCandidates',"SELECT 1 FROM pending_replies WHERE status IN")
 for status,expected in [('waiting',False),('failed',False),('collecting',True),('generating',True)]:
  c.execute('DELETE FROM pending_replies');c.execute('INSERT INTO pending_replies VALUES (?)',(status,));assert bool(c.execute(busy_sql).fetchone())==expected
 print('PASS waiting delivery does not occupy model work; collecting/generating still blocks queue')
 # Same schema protects schedule claims and token CAS across cancellation/manual retry.
 c.execute("INSERT INTO schedule_attempts VALUES ('mano/2026-11-04','mano',?,?,'RUNNING','old','')",(day,now))
 c.execute(sql('ScheduleAttempts',"UPDATE schedule_attempts SET state='FAILED',token=''"))
 assert c.execute('SELECT state,token FROM schedule_attempts').fetchone()==('FAILED','')
 c.execute("UPDATE schedule_attempts SET state='RUNNING',token='new' WHERE id='mano/2026-11-04'")
 schedule_where=sql('ScheduleAttempts',"id=? AND state='RUNNING' AND token=?")
 assert c.execute("UPDATE schedule_attempts SET state='DONE',token='' WHERE "+schedule_where,('mano/2026-11-04','old')).rowcount==0
 assert c.execute("UPDATE schedule_attempts SET state='DONE',token='' WHERE "+schedule_where,('mano/2026-11-04','new')).rowcount==1
 print('PASS interrupted schedule requires explicit new identity; old callback cannot finish retry')
 ledger=c.execute('SELECT * FROM social_usage ORDER BY id').fetchall()
 c.execute('DELETE FROM social_tasks')
 for row in ledger:c.execute('INSERT OR IGNORE INTO social_usage VALUES (?,?,?,?,?)',row)
 assert ledger==c.execute('SELECT * FROM social_usage ORDER BY id').fetchall() and c.execute('SELECT count(*) FROM social_tasks').fetchone()[0]==0
 assert before==prior(c)
 print('PASS restore-style union keeps existing usage, clears pending tasks and leaves protected rows untouched')
 c.close()
# Keep the behavioral contracts tied to owning Java, not just the test harness.
assert 'ScheduleAttempts.claim(store,who,day,manual' in (SRC/'ScheduleEngine.java').read_text(encoding='utf-8')
assert 'ScheduleQueue.refresh(context)' in (SRC/'ScheduleEngine.java').read_text(encoding='utf-8')
assert 'ReachOut.chatBusy' not in (SRC/'ScheduleEngine.java').read_text(encoding='utf-8')
assert 'if(oldVersion<18){SocialStore.create(db);ScheduleAttempts.create(db);}' in sources['ChatStore']
assert 'readState(s)' in sources['SocialStore'] and 'for(Iterator<String> it=r.keys()' not in sources['SocialStore']
assert not re.search(r'delete\("social_(usage|plans)"',sources['SocialStore'])
assert 'SocialData.day(now)' in sources['SocialEngine'] and '已过计划日期，未补发' in sources['SocialEngine']
print('PASS owning-source contracts: durable schedule admission, continuation, additive migration, bounded read IDs and retained ledger')
print('SQL checks passed. Local SQLite fixtures only; not Android database lifecycle or real paid requests.')
