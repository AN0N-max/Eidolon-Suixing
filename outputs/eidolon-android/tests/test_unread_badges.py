"""Run production unread count/destination/read SQL; Android notification UI is not executed."""
import re, sqlite3
from pathlib import Path

root=Path(__file__).resolve().parents[1]/'src/com/eidolon/sc/mobile'
native=(root/'UnreadNotifications.java').read_text(encoding='utf-8')
unread=(root/'UnreadState.java').read_text(encoding='utf-8')
latest=re.search(r'String sql="([^"]+)";',native).group(1)
counts=re.search(r'rawQuery\("(SELECT character,count\(\*\).*?GROUP BY character)"',unread).group(1)
read=re.search(r'execSQL\("(UPDATE messages SET unread=0[^"\n]+)"',unread).group(1)
db=sqlite3.connect(':memory:')
db.execute('CREATE TABLE messages(id INTEGER PRIMARY KEY,character TEXT,conversation TEXT,role TEXT,body TEXT,sent_at INTEGER,reach_at INTEGER,unread INTEGER)')
db.executemany('INSERT INTO messages VALUES(?,?,?,?,?,?,?,?)',[
 (1,'tenka','old-session','assistant','older unread',0,0,1),
 (2,'tenka','new-session','assistant','latest unread',100,0,1),
 (3,'tenka','new-session','user','newer user does not change badge destination',200,0,0),
 (4,'amana','another-session','assistant','proactive unread',150,150,1),
 (5,'tenka','new-session','assistant','already read newest reply',300,0,0),
])
assert dict(db.execute(counts))=={'tenka':2,'amana':1}
assert {r[0]:(r[1],r[2]) for r in db.execute(latest)}=={'tenka':('new-session',100),'amana':('another-session',150)}
db.execute(read,(2,'amana','another-session'))
assert dict(db.execute(counts))=={'tenka':2,'amana':1},'reading another role cannot clear this badge'
db.execute(read,(2,'tenka','new-session'))
assert dict(db.execute(counts))=={'tenka':1,'amana':1}
assert {r[0]:r[1] for r in db.execute(latest)}['tenka']=='old-session','notification destination follows remaining unread session'
db.execute('DELETE FROM messages WHERE conversation=?',('old-session',))
assert dict(db.execute(counts))=={'amana':1},'delete removes only that conversation contribution'
assert [r[0] for r in db.execute(latest)]==['amana']
db.execute(read,(4,'amana','another-session'));db.execute(read,(4,'amana','another-session'))
assert dict(db.execute(counts))=={} and list(db.execute(latest))==[],'last read removes badge exactly once'
print('PASS production SQLite badge queries: grouped counts, newest unread destination, read isolation, partial read, session deletion and zero unread. No Android notification/launcher execution.')
