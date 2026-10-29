"""Offline SQL boundary checks; not Android device acceptance."""
import re
import sqlite3
from pathlib import Path

source=(Path(__file__).resolve().parents[1]/'src/com/eidolon/sc/mobile/ReachOut.java').read_text(encoding='utf-8')
query=re.search(r'rawQuery\("(SELECT coalesce\(max\(CASE WHEN reach_at>0.*?FROM messages WHERE conversation=\?)"',source).group(1)
db=sqlite3.connect(':memory:')
db.execute('CREATE TABLE messages(id INTEGER PRIMARY KEY,character TEXT,conversation TEXT,role TEXT,reach_at INTEGER)')
def insert(i,who,session,role,at=0):
    db.execute('INSERT INTO messages VALUES(?,?,?,?,?)',(i,who,session,role,at))
def waiting(session):
    reach,user=db.execute(query,(session,)).fetchone()
    return reach>user
insert(1,'mano','a','user')
insert(2,'mano','a','assistant',100)
assert waiting('a')
insert(3,'mano','a','gift')
assert waiting('a'), 'gift cannot masquerade as an answer to proactive chat'
insert(4,'hiori','b','user')
assert waiting('a') and not waiting('b'), 'reply fence must remain session-isolated'
insert(5,'mano','a','user')
assert not waiting('a')
insert(6,'hiori','b','assistant',200)
day_start=150
assert db.execute('SELECT count(*) FROM messages WHERE reach_at>=?',(day_start,)).fetchone()[0]==1
assert db.execute('SELECT count(*) FROM messages WHERE reach_at>=? AND character=?',(day_start,'mano')).fetchone()[0]==0
assert db.execute('SELECT count(*) FROM messages WHERE reach_at>=? AND character=?',(day_start,'hiori')).fetchone()[0]==1
print('PASS actual proactive-reply fence SQL: gift cannot restart an unanswered character; new user reply releases the fence; session isolation and per-character/day quota boundaries. SQLite only.')
