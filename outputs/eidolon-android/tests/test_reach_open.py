"""Requested open proactive policy: production SQL and source boundaries; no Android/API."""
from pathlib import Path
import re,sqlite3
java=Path(__file__).resolve().parents[1]/'src/com/eidolon/sc/mobile'
plans=(java/'ReachPlans.java').read_text(encoding='utf-8');store=(java/'ReachPlanStore.java').read_text(encoding='utf-8');execution=(java/'ReachExecution.java').read_text(encoding='utf-8');chat=(java/'ChatStore.java').read_text(encoding='utf-8')
eligibility=plans[plans.index('static String ineligible'):plans.index('static long lastReach')]
commit=chat[chat.index('boolean addPlannedReach'):chat.index('private void createStories')]
for source in (eligibility,execution,commit):
    assert 'hasBlockingPending' not in source and 'awaitingReply' not in source and 'ReachOut.generating()' not in source
assert 'lastId(session)!=contact' in commit and 's.lastId(session)!=contact' in execution
assert 'quotaAllowed' in commit and 'ReachBudget.reserve' in execution and 'isRunning' in commit
assert '不代替正在等待的正式聊天回复' in execution
# Restrictions outside proactive chat retain their existing purpose.
assert 's.hasBlockingPending(who)' in (java/'AppealStore.java').read_text(encoding='utf-8')
assert 's.hasBlockingPending(who)' in (java/'LettersEngine.java').read_text(encoding='utf-8')
db=sqlite3.connect(':memory:')
for sql in re.findall(r'db.execSQL\("((?:CREATE)[^"\n]+)"',store):db.execute(sql)
db.executescript('CREATE TABLE pending_replies(id TEXT PRIMARY KEY,status TEXT,answer TEXT);CREATE TABLE messages(id INTEGER PRIMARY KEY,role TEXT,reach_at INTEGER);')
db.execute("INSERT INTO messages VALUES(1,'assistant',100)")
db.execute("INSERT INTO reach_tasks(id,character,session,day,zone,due,expires,created,revision,opportunity,status,attempts,message_id) VALUES('old','role','s','2026-10-28','Asia/Shanghai',100,1000,1,7,8,'COMPLETED',1,1)")
handled_sql=re.search(r'static boolean handled.*?rawQuery\("([^"]+)"',plans).group(1)
manual=re.search(r'static final String MANUAL="([^"]+)"',store).group(1)
args=('role','2026-10-28','Asia/Shanghai','8','8','7',manual)
claim=re.search(r'update\("reach_tasks",v,"([^"]+)"',store).group(1)
for i,state in enumerate(('collecting','generating','waiting','failed')):
    db.execute('INSERT OR REPLACE INTO pending_replies VALUES(?,?,?)',('s',state,'preserved normal reply'))
    assert db.execute(handled_sql,args).fetchone() is None,'unanswered completed proactive message must not consume every subsequent chance'
    id='next'+str(i)
    db.execute("INSERT INTO reach_tasks(id,character,session,day,zone,due,expires,created,revision,opportunity) VALUES(?,'role','s','2026-10-28','Asia/Shanghai',200,1000,1,7,8)",(id,))
    assert db.execute("UPDATE reach_tasks SET status='RUNNING',token='claim' WHERE "+claim,(id,'200','200')).rowcount==1
    assert db.execute("UPDATE reach_tasks SET status='RUNNING',token='duplicate' WHERE "+claim,(id,'200','200')).rowcount==0
    assert db.execute("SELECT status,answer FROM pending_replies WHERE id='s'").fetchone()==(state,'preserved normal reply')
    db.execute('DELETE FROM reach_tasks WHERE id=?',(id,))
db.execute("UPDATE reach_tasks SET status='FAILED' WHERE id='old'")
assert db.execute(handled_sql,args).fetchone() is not None,'unknown result still blocks blind automatic retries'
print('PASS production SQL: completed unanswered opportunity permits another plan in same day/revision; all four pending statuses coexist; ordinary reply stays intact; double claim fenced; unknown result still blocked. Source boundaries retain quotas, revision, stale-history checks and non-proactive guards.')
