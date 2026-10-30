from pathlib import Path
import re,sqlite3
base=Path(__file__).resolve().parents[1]
text=(base/'src/com/eidolon/sc/mobile/ReplyManager.java').read_text(encoding='utf-8')
sql=re.search(r'execSQL\("(UPDATE pending_replies SET due_at=.*?)",new Object',text).group(1)
db=sqlite3.connect(':memory:');db.execute('CREATE TABLE pending_replies(id TEXT,revision INTEGER,status TEXT,due_at INTEGER,immediate INTEGER,answer TEXT)')
db.execute("INSERT INTO pending_replies VALUES('s',1,'generating',0,0,'preserved')")
db.execute(sql,(100,'s',1));db.execute(sql,(200,'s',1));assert db.execute('SELECT due_at,answer FROM pending_replies').fetchone()==(100,'preserved')
db.execute("UPDATE pending_replies SET revision=2,due_at=0");db.execute(sql,(300,'s',1));assert db.execute('SELECT due_at FROM pending_replies').fetchone()[0]==0
db.execute('UPDATE pending_replies SET immediate=1');db.execute(sql,(400,'s',2));assert db.execute('SELECT due_at FROM pending_replies').fetchone()[0]==0
db.execute("UPDATE pending_replies SET immediate=0,status='waiting'");db.execute(sql,(500,'s',2));assert db.execute('SELECT due_at FROM pending_replies').fetchone()[0]==0
print('PASS production delay-freeze SQL: durable once, revision fencing, immediate receive, waiting answer preserved')
