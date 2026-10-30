"""Real two-connection SQLite races and failed restore rollback using production SQL.
Offline synthetic data; not a claim of Android process lifecycle or model language quality.
"""
from pathlib import Path
import sqlite3,tempfile,threading,re,json
root=Path(__file__).resolve().parents[1];src=root/'src/com/eidolon/sc/mobile'
chat=(src/'ChatStore.java').read_text(encoding='utf-8')
engine=(src/'ConversationEngine.java').read_text(encoding='utf-8')
web=(src/'WebActivity.java').read_text(encoding='utf-8')
runtime=(src/'RuntimeSettings.java').read_text(encoding='utf-8')
with tempfile.TemporaryDirectory(prefix='eidolon-architecture-') as folder:
 path=Path(folder)/'app.db'
 def connect():
  db=sqlite3.connect(path,timeout=10,isolation_level=None);db.execute('PRAGMA journal_mode=WAL');return db
 db=connect()
 db.execute(re.search(r'db.execSQL\("(CREATE TABLE pending_replies[^"]+)"',chat).group(1))
 db.execute(re.search(r'db.execSQL\("(CREATE TABLE role_runtime[^"]+)"',runtime).group(1))
 db.execute("INSERT INTO pending_replies(id,character,session,revision,first_user_id,last_user_id,last_at,status) VALUES('s','mano','s',1,1,1,1,'collecting')")
 claim=re.search(r'boolean markGenerating.*?update\("pending_replies",v,"([^"]+)"',chat).group(1)
 barrier=threading.Barrier(2);results=[];errors=[]
 def race():
  try:
   c=connect();barrier.wait(timeout=10);results.append(c.execute('UPDATE pending_replies SET status=? WHERE '+claim,('generating','s',1)).rowcount);c.close()
  except Exception as e:errors.append(e)
 threads=[threading.Thread(target=race) for _ in range(2)]
 for t in threads:t.start()
 for t in threads:t.join(timeout=20)
 assert not errors and all(not t.is_alive() for t in threads) and sorted(results)==[0,1],(results,errors)
 ready=re.search(r'boolean markReady.*?update\("pending_replies",v,"([^"]+)"',chat).group(1)
 db.execute("UPDATE pending_replies SET revision=2,status='collecting' WHERE id='s'")
 assert db.execute('UPDATE pending_replies SET status=? WHERE '+ready,('waiting','s',1)).rowcount==0
 config=json.dumps({'character':'mano','zone':'America/New_York','events':[]})
 db.execute('INSERT INTO role_runtime VALUES(?,?)',('mano',config))
 # Fail after deleting settings and replacing pending state. Neither replacement survives.
 db.execute('BEGIN');db.execute('DELETE FROM role_runtime');db.execute("UPDATE pending_replies SET status='cancelled'")
 try:db.execute('INSERT INTO missing_table VALUES(1)')
 except sqlite3.OperationalError:db.rollback()
 assert db.execute('SELECT body FROM role_runtime').fetchone()[0]==config
 assert db.execute('SELECT revision,status FROM pending_replies').fetchone()==(2,'collecting')
 db.close();db=connect();assert json.loads(db.execute('SELECT body FROM role_runtime').fetchone()[0])['zone']=='America/New_York';db.close()
start=web.index('void generatePending(');end=web.index('@Override protected void onActivityResult',start);front=web[start:end]
assert 'ConversationEngine.execute' in front and 'ConversationEngine.generate' in front
assert 'ModelClient.conversationRequest' not in front and 'Recall.write' not in front and 'markReady' not in front
assert 'ModelClient.conversationRequest' in engine and 'runOnUiThread' not in engine
assert 'JSONObject base=store.personal(),start=new JSONObject(base.toString())' in engine
assert 'JSONObject data=new JSONObject(start.toString())' in engine and '.put("base",base)' in engine
assert 'RuntimeSettings.restore(this,data)' in chat and 'RuntimeSettings.all(this)' in chat
assert chat.index('RuntimeSettings.restore(this,data)')<chat.index('db.setTransactionSuccessful();return sessions.length()')
assert 'roleZone/' not in (src/'TemporalRuntime.java').read_text(encoding='utf-8')
assert 'com.vivo.abe' in (src/'LauncherBadge.java').read_text(encoding='utf-8')
print('PASS two concurrent production claims yield one owner; stale generation fenced; failed settings+pending transaction rolls back; role timezone survives reconnect; shared worker pipeline and successful Vivo badge path retained. No paid calls.')
