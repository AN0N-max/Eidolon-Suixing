"""Run production SQL for overdue wakes, reservations and replan failure fencing."""
from pathlib import Path
import re,sqlite3
java=Path(__file__).resolve().parents[1]/'src/com/eidolon/sc/mobile'
store=(java/'ReachPlanStore.java').read_text(encoding='utf-8')
plans=(java/'ReachPlans.java').read_text(encoding='utf-8')
db=sqlite3.connect(':memory:')
for sql in re.findall(r'db.execSQL\("((?:CREATE)[^"\n]+)"',store):db.execute(sql)
def insert(id,state,due,revision=1,opportunity=8):
    db.execute("INSERT INTO reach_tasks(id,character,session,day,zone,due,expires,created,revision,status,opportunity) VALUES(?,?,'s','2026-10-27','Asia/Shanghai',?,10000,1,?,?,?)",(id,id,due,revision,state,opportunity))
insert('eligible','PENDING',200)
insert('failed','FAILED',100)
insert('done','COMPLETED',100)
next_sql=re.search(r'static long next.*?rawQuery\("([^"]+)"',store).group(1)
assert db.execute(next_sql).fetchone()[0]==200,'overdue task retains due time instead of expiry'
budget_sql=re.search(r'static int remaining.*?rawQuery\("([^"]+)"',plans,re.S).group(1)
assert db.execute(budget_sql,('2026-10-27','Asia/Shanghai')).fetchone()[0]==1
stop_sql=re.search(r'static void stop.*?execSQL\("([^"]+)"',store).group(1)
db.execute(stop_sql,(500,'manual replan'))
assert db.execute(next_sql).fetchone()[0]==0
assert db.execute("SELECT status FROM reach_tasks WHERE id='failed'").fetchone()[0]=='FAILED'
handled_sql=re.search(r'static boolean handled.*?rawQuery\("([^"]+)"',plans).group(1)
def handled(who,revision):
    return db.execute(handled_sql,(who,'2026-10-27','Asia/Shanghai','8','8',str(revision),re.search(r'static final String MANUAL="([^"]+)"',store).group(1))).fetchone() is not None
assert not handled('eligible',2),'new revision can replace a never-sent skipped plan'
assert handled('failed',2),'unknown-result request remains fenced across replans'
assert not handled('done',2),'completed proactive message permits a new opportunity after time and quota checks'
insert('replacement','PENDING',600,2)
assert db.execute(budget_sql,('2026-10-27','Asia/Shanghai')).fetchone()[0]==1
print('PASS production SQL: overdue wake, empty plan, reserved capacity, replaceable pending opportunity, completed and unknown-result fencing.')
