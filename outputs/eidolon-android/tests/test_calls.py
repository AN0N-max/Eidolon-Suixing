"""Production call SQL and rollback on synthetic SQLite; no native microphone or provider invocation."""
from pathlib import Path
import re,sqlite3,json,tempfile
root=Path(__file__).resolve().parents[1];src=root/'src/com/eidolon/sc/mobile'
calls=(src/'CallStore.java').read_text(encoding='utf-8');chat=(src/'ChatStore.java').read_text(encoding='utf-8')
with tempfile.TemporaryDirectory(prefix='eidolon-calls-') as folder:
 p=Path(folder)/'app.db';db=sqlite3.connect(p);db.execute('CREATE TABLE messages(id INTEGER PRIMARY KEY,role TEXT,body TEXT)');db.execute("INSERT INTO messages VALUES(1,'user','synthetic ASR utterance')")
 db.execute(re.search(r'db.execSQL\("(CREATE TABLE call_sessions[^"]+)"',calls).group(1));db.execute(re.search(r'db.execSQL\("(CREATE TABLE pending_replies[^"]+)"',chat).group(1));db.execute("INSERT INTO pending_replies(id,character,session,revision,first_user_id,last_user_id,last_at,status) VALUES('s','mano','s',7,1,1,100,'waiting')")
 row={'id':'c','character':'mano','state':'speaking','pending':'s','revision':7,'heard':False};db.execute('INSERT INTO call_sessions VALUES(?,?,?,?,?,?)',('c','mano','speaking','s',7,json.dumps(row)));db.commit()
 assert not json.loads(db.execute('SELECT body FROM call_sessions').fetchone()[0])['heard'];assert db.execute('SELECT count(*) FROM messages').fetchone()[0]==1
 # Playback ack + reply + effects are one transaction. A later failure must restore all of them.
 row['heard']=True;db.execute('UPDATE call_sessions SET body=?',(json.dumps(row),));db.execute("INSERT INTO messages VALUES(2,'assistant','synthetic fully heard answer')");db.execute("DELETE FROM pending_replies WHERE id='s' AND revision=7 AND status='waiting'");db.rollback()
 assert not json.loads(db.execute('SELECT body FROM call_sessions').fetchone()[0])['heard'];assert db.execute('SELECT count(*) FROM messages').fetchone()[0]==1
 db.execute("UPDATE pending_replies SET revision=revision+1,status='cancelled' WHERE id='s'");db.commit();assert db.execute("UPDATE pending_replies SET status='waiting' WHERE id='s' AND revision=7 AND status='generating'").rowcount==0
 assert db.execute('SELECT revision FROM pending_replies').fetchone()[0]==8;db.close();db=sqlite3.connect(p);assert db.execute('SELECT count(*) FROM messages').fetchone()[0]==1;db.close()
engine=(src/'ConversationEngine.java').read_text(encoding='utf-8');service=(src/'CallService.java').read_text(encoding='utf-8');player=(src/'VoicePlayer.java').read_text(encoding='utf-8');manager=(src/'ReplyManager.java').read_text(encoding='utf-8')
assert 'CallStore.held(store,row)' in manager and 'callBinding!=null&&!callBinding.optBoolean("heard")' in chat
assert 'remember=!voiceCall' in engine and 'promises=!voiceCall' in engine and '!voiceCall&&!PendingReplyJob.schedule' in engine
assert 'public void completed()' in service and service.index('put("heard",true)')<service.index('store.deliverPending')
assert 'ConversationEngine.stop' in service and 'player.stop()' in service and 'r.cancel();r.destroy()' in service
assert 'if(externalFocus)return current(s)' in player and 'getPlaybackHeadPosition()' in player
assert 'CallStore.recover(s)' in (src/'EidolonApp.java').read_text(encoding='utf-8') and '.put("version",19)' in chat
assert 'AudioDeviceCallback' in service and 'change<0' in service and 'START_NOT_STICKY' in service
print('PASS call-table migration preserves messages; unheard audio stays staged; transactional playback ack rolls back; cancellation revision persists across reconnect; shared pipeline, mic/audio cancellation and device-loss source hooks. SQLite/fake contracts only, not Android execution.')

incoming=(src/'CallIncoming.java').read_text(encoding='utf-8')
assert 'new ModelClient' not in incoming and 'Vault.read' not in incoming and 'SpeechRecognizer' not in incoming
assert 'nm.areNotificationsEnabled()' in incoming and '24*3600000L' in incoming and 'offersToday' in incoming
assert 'CallIncoming.answer' in service and 'epoch.commitOnce' in service and 'if(!recognizing(own))return' in service
print('PASS incoming invitation invokes no recognition/model API before acceptance; notification availability, cooldown and quota hooks exist; stale recognition and duplicate audio completion are fenced.')
