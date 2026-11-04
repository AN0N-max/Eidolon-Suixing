"""Production query/CAS fixtures on local SQLite; no Android or paid provider."""
from pathlib import Path
import re,json,sqlite3,tempfile,uuid,concurrent.futures
source=(Path(__file__).resolve().parents[1]/'src/com/eidolon/sc/mobile/SocialStore.java').read_text(encoding='utf-8')
strings=[json.loads('"'+s+'"') for s in re.findall(r'"((?:[^"\\]|\\.)*)"',source)]
def sql(prefix):return next(s for s in strings if s.startswith(prefix))
def proof(d):return bool(d) and (d.get('requestAttempted') is False or d.get('stage')=='prepare' and d.get('category')=='format' and d.get('exception')=='JSONException')
with tempfile.TemporaryDirectory(prefix='eidolon-social-admission-') as tmp:
 path=Path(tmp)/'db.sqlite'
 def conn():return sqlite3.connect(path,timeout=10,isolation_level=None)
 c=conn()
 for s in strings:
  if s.startswith(('CREATE TABLE ','CREATE INDEX ')):c.execute(s)
 def add(ident,body,state='RUNNING',token='owner'):
  c.execute('INSERT INTO social_tasks(id,character,kind,target,day,due,expires,state,token,body) VALUES (?,\'mano\',\'post\',\'\',\'2026-11-04\',1,9999999999999,?,?,?)',(ident,state,token,json.dumps(body)))
 add('old-local',{'diagnostic':{'stage':'prepare','category':'format','exception':'JSONException'}},'FAILED')
 add('real-parse',{'diagnostic':{'stage':'parse','category':'format','exception':'JSONException'}},'FAILED')
 add('unknown',{},'FAILED')
 for ident in ['old-local','real-parse','unknown']:c.execute("INSERT INTO social_usage VALUES (?,'2026-11-04','post','mano',1)",(ident,))
 def used(db):
  total=db.execute(sql('SELECT count(*) FROM social_usage WHERE day='),('2026-11-04','post')).fetchone()[0]
  for (body,) in db.execute(sql('SELECT t.body FROM social_tasks t JOIN'),('2026-11-04','post')):
   try:total-=int(proof(json.loads(body).get('diagnostic')))
   except (ValueError,TypeError):pass
  return total
 assert used(c)==2 and c.execute('SELECT count(*) FROM social_usage').fetchone()[0]==3
 print('PASS proof-only preflight quota credit keeps all original ledger/task records; paid/unknown failures still count')
 add('unprepared',{})
 assert c.execute(sql('SELECT count(*) FROM social_usage WHERE id='),('unprepared',)).fetchone()[0]==0
 c.execute("UPDATE social_tasks SET state='FAILED',body=? WHERE id='unprepared'",(json.dumps({'diagnostic':{'requestAttempted':False,'stage':'prepare_places'}}),))
 assert used(c)==2
 print('PASS failed non-billable preparation does not consume a model-request slot')
 add('a',{});add('b',{})
 task_query=sql('SELECT id,character,kind,target,day,due,expires,state,token,body,error,started,finished FROM social_tasks WHERE id=')
 def reserve(ident,token='owner',limit=3):
  db=conn();db.execute('BEGIN IMMEDIATE')
  try:
   row=db.execute(task_query,(ident,)).fetchone()
   if row is None or row[7]!='RUNNING' or row[8]!=token or used(db)>=limit or db.execute(sql('SELECT count(*) FROM social_usage WHERE id='),(ident,)).fetchone()[0]:db.execute('ROLLBACK');return False
   db.execute("INSERT INTO social_usage VALUES (?,'2026-11-04','post','mano',2)",(ident,));db.execute('COMMIT');return True
  finally:db.close()
 with concurrent.futures.ThreadPoolExecutor(max_workers=2) as ex:results=list(ex.map(reserve,['a','b']))
 assert sum(results)==1 and used(c)==3
 winner='a' if results[0] else 'b';assert not reserve(winner,limit=10) and not reserve('a','stale-token',10)
 print('PASS concurrent prepared tasks respect quota atomically; repeated identity and stale cancellation token cannot resend')
 assert c.execute('SELECT count(*) FROM social_tasks').fetchone()[0]==6
 c.close()
