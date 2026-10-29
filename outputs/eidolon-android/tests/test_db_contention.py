"""Real SQLite multi-connection contention and production replan SQL. Not Android runtime."""
from pathlib import Path
import sqlite3,re,tempfile,threading
root=Path(__file__).resolve().parents[1];java=root/'src/com/eidolon/sc/mobile'
store=(java/'ReachPlanStore.java').read_text(encoding='utf-8');plans=(java/'ReachPlans.java').read_text(encoding='utf-8')
reason=re.search(r'static final String MANUAL="([^"]+)"',store).group(1)
replace_sql=re.search(r'static void replacePending.*?execSQL\("([^"]+)"',store).group(1)
handled_sql=re.search(r'static boolean handled.*?rawQuery\("([^"]+)"',plans).group(1)
with tempfile.TemporaryDirectory(prefix='eidolon-contention-') as folder:
    path=Path(folder)/'test.db';db=sqlite3.connect(path,timeout=0,isolation_level=None)
    assert db.execute('PRAGMA journal_mode=WAL').fetchone()[0]=='wal'
    for sql in re.findall(r'db.execSQL\("((?:CREATE)[^"\n]+)"',store):db.execute(sql)
    db.executescript("CREATE TABLE request_budget(used INTEGER);INSERT INTO request_budget VALUES(4);CREATE TABLE messages(body TEXT);INSERT INTO messages VALUES('synthetic existing chat');")
    for id,state,attempts in [('old','PENDING',0),('failed','FAILED',1),('done','COMPLETED',1),('unknownRetry','PENDING',1)]:
        db.execute("INSERT INTO reach_tasks(id,character,session,day,zone,due,expires,created,revision,status,attempts,opportunity) VALUES(?,?,?,'2026-10-27','Asia/Shanghai',200,10000,1,7,?,?,8)",(id,id,'s',state,attempts))
    held=threading.Event();release=threading.Event();errors=[]
    def writer():
        try:
            connection=sqlite3.connect(path,isolation_level=None);connection.execute('BEGIN IMMEDIATE');connection.execute("UPDATE reach_tasks SET created=2 WHERE id='done'");held.set();assert release.wait(5);connection.commit();connection.close()
        except BaseException as error:errors.append(error);held.set()
    task=threading.Thread(target=writer);task.start();assert held.wait(5)
    try:
        try:db.execute('PRAGMA journal_mode=DELETE')
        except sqlite3.OperationalError as e:assert 'locked' in str(e)
        else:raise AssertionError('journal reconfiguration while another owner writes must reproduce lock')
        assert db.execute("SELECT status FROM reach_tasks WHERE id='old'").fetchone()==('PENDING',),'WAL read can continue during the writer'
        try:db.execute('BEGIN IMMEDIATE')
        except sqlite3.OperationalError as e:assert 'locked' in str(e)
        else:raise AssertionError('second writer must wait')
    finally:release.set();task.join(5)
    assert not errors and not task.is_alive()
    before=db.execute('SELECT * FROM reach_tasks ORDER BY id').fetchall()
    db.execute('BEGIN IMMEDIATE')
    try:
        db.execute(replace_sql,(500,reason));db.execute("INSERT INTO reach_tasks SELECT * FROM reach_tasks WHERE id='done'")
    except sqlite3.IntegrityError:db.rollback()
    else:raise AssertionError('injected creation failure did not happen')
    assert db.execute('SELECT * FROM reach_tasks ORDER BY id').fetchall()==before,'old plan and failed/completed history must survive a failed rebuild'
    reader=sqlite3.connect(path,isolation_level=None);reader.execute('BEGIN');reader.execute('SELECT count(*) FROM reach_tasks').fetchone()
    db.execute('BEGIN IMMEDIATE');db.execute(replace_sql,(500,reason))
    def handled(who):return db.execute(handled_sql,(who,'2026-10-27','Asia/Shanghai','8','8','7',reason)).fetchone() is not None
    assert not handled('old'),'manual replacement may recreate a never-requested opportunity in same revision'
    assert handled('failed') and handled('unknownRetry') and not handled('done'),'unknown attempts remain fenced; completed messages permit a fresh opportunity'
    db.execute("INSERT INTO reach_tasks(id,character,session,day,zone,due,expires,created,revision,opportunity) VALUES('new','old','s','2026-10-27','Asia/Shanghai',600,10000,500,7,8)");db.commit()
    assert reader.execute("SELECT status FROM reach_tasks WHERE id='old'").fetchone()==('PENDING',),'existing read snapshot remains consistent'
    reader.rollback();assert reader.execute("SELECT status FROM reach_tasks WHERE id='old'").fetchone()==('SKIPPED',)
    assert db.execute('SELECT used FROM request_budget').fetchone()==(4,)
    assert db.execute('SELECT body FROM messages').fetchone()==('synthetic existing chat',)
    reader.close();db.close()
print('PASS actual SQLite WAL reader/writer contention, journal reconfiguration lock reproduction, atomic rollback/replacement, unchanged revision, unknown-result fencing, unchanged chat/request budget.')
