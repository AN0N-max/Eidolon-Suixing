"""Run production batch SQL and inspect indexes; synthetic local data only."""
from pathlib import Path
import sqlite3,re,time,json
root=Path(__file__).resolve().parents[1]
java=root/'src/com/eidolon/sc/mobile'
source=(java/'UiSnapshots.java').read_text(encoding='utf-8')
web=(java/'WebActivity.java').read_text(encoding='utf-8')
db=sqlite3.connect(':memory:')
db.executescript('CREATE TABLE messages(id INTEGER PRIMARY KEY,character TEXT,conversation TEXT,role TEXT,body TEXT);CREATE INDEX character_messages ON messages(character,id);CREATE INDEX conversation_messages ON messages(conversation,id);CREATE TABLE conversations(id TEXT PRIMARY KEY,character TEXT,updated INTEGER);CREATE TABLE writings(kind TEXT,character TEXT,is_read INTEGER,secret TEXT,secret_read INTEGER);')
ids=['role'+str(i) for i in range(28)]
for i,who in enumerate(ids):
    db.execute('INSERT INTO conversations VALUES(?,?,?)',(who,who,1000+i))
    db.executemany('INSERT INTO messages VALUES(?,?,?,?,?)',[(i*1000+j+1,who,who,'assistant' if j%2 else 'user','synthetic '+str(j)) for j in range(1000)])
db.execute("INSERT INTO conversations VALUES('empty','role0',999999)")
selected=' UNION ALL '.join('SELECT ? AS character' for _ in ids)
sql=re.search(r'String sql="([^"]+)"\+selected\+"([^"]+)"',source)
query=sql.group(1)+selected+sql.group(2)
start=time.perf_counter();batch={who:(role,text) for who,role,text in db.execute(query,ids)};elapsed=(time.perf_counter()-start)*1000
for who in ids:
    assert batch[who]==db.execute('SELECT role,body FROM messages WHERE character=? ORDER BY id DESC LIMIT 1',(who,)).fetchone()
plan=' '.join(str(row) for row in db.execute('EXPLAIN QUERY PLAN '+query,ids))
assert 'character_messages' in plan,'per-character latest lookup must use the existing index'
activity=re.search(r'String activitySql="([^"]+)"',source).group(1)
values=dict(db.execute(activity));assert values['role0']==1000,'empty conversation cannot move character activity'
db.execute("DELETE FROM messages WHERE character='role4'")
assert 'role4' not in dict((r[0],r[1:]) for r in db.execute(query,ids))
count_sql=re.search(r'String sql="(SELECT kind,character,is_read[^\n]+)";',source).group(1)
db.execute("INSERT INTO writings VALUES('diary','role0',0,'PRIVATE SENTINEL',0)")
assert db.execute(count_sql).fetchone()==('diary','role0',0,1,0)
assert 'PRIVATE SENTINEL' not in str(db.execute(count_sql).fetchall())
read=source[source.index('static JSONObject read('):source.index('static final java.util.concurrent.ExecutorService')]
assert 'savePersonal' not in read and 'Vault.read' not in read and 'deliverAvailable' not in read
assert 'UiSnapshots.READS.execute' in web and 'request.revision!=uiRevision' in web and 'final String payload=result.toString()' in web
print(f'PASS synthetic SQLite 28000 messages: batch preview matches per-role query, indexed lookup, empty/deleted history, metadata-only private unread. Batch SQL {elapsed:.2f} ms on host; not phone timing.')
