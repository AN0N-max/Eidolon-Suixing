"""Extract production schema into SQLite; synthetic media only, no Android codecs or providers."""
from pathlib import Path
import re,json,sqlite3,tempfile,hashlib
root=Path(__file__).resolve().parents[1];src=root/'src/com/eidolon/sc/mobile'
media=(src/'MediaStore.java').read_text(encoding='utf-8');chat=(src/'ChatStore.java').read_text(encoding='utf-8')
with tempfile.TemporaryDirectory(prefix='eidolon-media-') as folder:
 p=Path(folder)/'app.db';db=sqlite3.connect(p);db.execute('CREATE TABLE messages(id INTEGER PRIMARY KEY,character TEXT,conversation TEXT,role TEXT,body TEXT,sent_at INTEGER)')
 db.execute("INSERT INTO messages VALUES(1,'mano','s','user','legacy text',100)")
 for sql in re.findall(r'db.execSQL\("((?:ALTER TABLE messages|CREATE TABLE (?:media_|stickers))[^"\n]+)"\)',media):db.execute(sql)
 assert db.execute('SELECT body,sent_at,content FROM messages WHERE id=1').fetchone()==('legacy text',100,'[]')
 asset=hashlib.sha256(b'synthetic-static-image').hexdigest();sticker='s-'+asset
 db.execute('INSERT INTO media_assets VALUES(?,?,?,?,?)',(asset,'image/png',22,1,1));db.execute('INSERT OR IGNORE INTO media_assets VALUES(?,?,?,?,?)',(asset,'image/png',22,1,1));assert db.execute('SELECT count(*) FROM media_assets').fetchone()[0]==1
 row={'id':sticker,'asset':asset,'uses':0,'enabled':True};db.execute('INSERT INTO stickers VALUES(?,?,?)',(sticker,asset,json.dumps(row)))
 blocks=[{'type':'sticker','asset':asset,'stickerId':sticker,'description':'synthetic grin'},{'type':'text','text':'hello'}]
 db.execute('INSERT INTO messages VALUES(?,?,?,?,?,?,?)',(2,'mano','s','assistant','[sticker]\nhello',200,json.dumps(blocks)));db.commit();db.close();db=sqlite3.connect(p)
 assert [b['type'] for b in json.loads(db.execute('SELECT content FROM messages WHERE id=2').fetchone()[0])]==['sticker','text']
 row['uses']=1;db.execute('UPDATE stickers SET body=? WHERE id=?',(json.dumps(row),sticker));db.execute('DELETE FROM messages WHERE id=2');db.rollback()
 assert json.loads(db.execute('SELECT body FROM stickers').fetchone()[0])['uses']==0 and db.execute('SELECT count(*) FROM messages').fetchone()[0]==2
 db.execute('DELETE FROM stickers WHERE id=?',(sticker,));db.commit();assert db.execute('SELECT count(*) FROM media_assets WHERE id=?',(asset,)).fetchone()[0]==1 and db.execute('SELECT content FROM messages WHERE id=2').fetchone()[0]
 db.close()
assert 'if(oldVersion<17)MediaStore.create(db)' in chat and '.put("version",18)' in chat and '.put("content",MediaData.array(m.getString(9)))' in chat
engine=(src/'ConversationEngine.java').read_text(encoding='utf-8');context=(src/'MediaContext.java').read_text(encoding='utf-8')
assert 'remember=!voiceCall&&!mediaBatch' in engine and 'promises=!voiceCall&&!mediaBatch' in engine
assert 'mediaPending(p)' in chat and '服务可能已计费' in chat
assert 'MediaStore.delivered' in chat and 'MediaStore.filterDelivery' in chat and 'MediaStore.used(this,blocks,now)' in chat
assert 'MediaContext.attach' in engine and 'MediaContext.prepare' in engine and 'seenImages' in engine
assert 'eligible.contains(id)' in context and 'seen.contains(id)' in context and 'MediaData.knownSticker(b)' in context
assert 'CREATE TABLE media_analysis' in media and 'MAX_IMPORT' in media and '4096' in media and 'ExifInterface' in media
assert 'MediaReply.parse' in (src/'ReachExecution.java').read_text(encoding='utf-8') and 'voiceCall?new JSONArray():StickerEngine.candidates' in engine
web=(src/'WebActivity.java').read_text(encoding='utf-8');assert 'PICK_CHAT_MEDIA' in web and 'mediaPickSession' in web and 'onSaveInstanceState' in web and 'mediaTextRetry' in web
assert 'restore(appContext,this,data)' in chat and 'MediaStore.validateBackup' in (src/'BackupValidator.java').read_text(encoding='utf-8')
print('PASS production v17 schema preserves legacy text/time; one shared asset, ordered restart, transactional usage rollback, deleted catalogue retains history; bounded vision/role pipeline, permission recheck and backup hooks. SQLite/fake contracts only.')
