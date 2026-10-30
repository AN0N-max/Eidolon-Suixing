"""Exercise production ChatStore ownership with a fake Android helper, not Android SQL."""
from pathlib import Path
import subprocess,tempfile,os
base=Path(__file__).resolve().parents[1];root=base.parents[1]
jdk=Path(os.environ['JAVA_HOME']);android=Path(os.environ['ANDROID_HOME'])/'platforms/android-35/android.jar';json=Path(os.environ.get('JSON_JAR',str(root/'work/android-test-tools/json.jar')))
classes=root/'work/android-build-1.14/gradle/intermediates/javac/release/compileReleaseJavaWithJavac/classes'
sources={
'android/content/Context.java':'package android.content;public class Context {public Context getApplicationContext(){return this;}}',
'android/database/sqlite/SQLiteDatabase.java':'package android.database.sqlite;public class SQLiteDatabase {public interface CursorFactory{}}',
'android/database/sqlite/SQLiteOpenHelper.java':"""package android.database.sqlite;import android.content.Context;
public abstract class SQLiteOpenHelper implements AutoCloseable {
 public static int opens,closes,wals;private boolean opened;private final SQLiteDatabase db=new SQLiteDatabase();
 public SQLiteOpenHelper(Context c,String name,SQLiteDatabase.CursorFactory f,int version){}
 public void setWriteAheadLoggingEnabled(boolean enabled){if(enabled)wals++;}
 public synchronized SQLiteDatabase getWritableDatabase(){if(!opened){opened=true;opens++;}return db;}
 public synchronized SQLiteDatabase getReadableDatabase(){return getWritableDatabase();}
 public synchronized void close(){closes++;}
 public abstract void onCreate(SQLiteDatabase db);public abstract void onUpgrade(SQLiteDatabase db,int old,int next);
}""",
'android/database/sqlite/SQLiteDatabaseLockedException.java':'package android.database.sqlite;public class SQLiteDatabaseLockedException extends RuntimeException{}',
'android/database/sqlite/SQLiteTableLockedException.java':'package android.database.sqlite;public class SQLiteTableLockedException extends RuntimeException{}',
'com/eidolon/sc/mobile/SharedStoreProbe.java':"""package com.eidolon.sc.mobile;
import android.content.Context;import android.database.sqlite.*;import java.util.*;import java.util.concurrent.*;
public final class SharedStoreProbe {
 public static void main(String[] args)throws Exception{
  Context context=new Context();ChatStore a=new ChatStore(context),b=new ChatStore(context);
  if(SQLiteOpenHelper.opens!=0)throw new AssertionError("leases must not open an independent pool");
  SQLiteDatabase expected=a.getWritableDatabase();if(b.getReadableDatabase()!=expected)throw new AssertionError("different database owners");
  a.close();boolean closed=false;try{a.getReadableDatabase();}catch(IllegalStateException e){closed=true;}if(!closed)throw new AssertionError("closed lease reused");
  if(b.getReadableDatabase()!=expected)throw new AssertionError("closing A affected B");
  ExecutorService executor=Executors.newFixedThreadPool(16);List<Future<?>> work=new ArrayList<>();
  for(int i=0;i<64;i++)work.add(executor.submit(()->{for(int j=0;j<30;j++)try(ChatStore s=new ChatStore(context)){if(s.getReadableDatabase()!=expected||s.getWritableDatabase()!=expected)throw new AssertionError("split pool");}}));
  for(Future<?> task:work)task.get();executor.shutdown();if(!executor.awaitTermination(10,TimeUnit.SECONDS))throw new AssertionError("thread leak");b.close();
  if(SQLiteOpenHelper.opens!=1||SQLiteOpenHelper.wals!=1||SQLiteOpenHelper.closes!=0)throw new AssertionError("owner lifecycle: "+SQLiteOpenHelper.opens+"/"+SQLiteOpenHelper.wals+"/"+SQLiteOpenHelper.closes);
  if(!ReachPlanCheck.databaseBusy(new Exception(new SQLiteDatabaseLockedException()))||!ReachPlanCheck.databaseBusy(new SQLiteTableLockedException())||ReachPlanCheck.databaseBusy(new IllegalArgumentException()))throw new AssertionError("busy classification");
  System.out.println("PASS production ChatStore with fake helper: 1922 leases/16 threads share one opened owner; WAL configured once; close isolation; lock cause classification. Not Android connection-pool execution.");
 }
}"""}
parent=root/'work/android-tests';parent.mkdir(exist_ok=True)
with tempfile.TemporaryDirectory(prefix='shared-owner-',dir=parent) as folder:
    directory=Path(folder).resolve();assert directory.is_relative_to(parent.resolve())
    files=[]
    for name,source in sources.items():
        target=directory/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(source,encoding='utf-8');files.append(str(target))
    output=directory/'classes';output.mkdir()
    cp=';'.join(map(str,[classes,json,android]))
    subprocess.run([str(jdk/'bin/javac.exe'),'-encoding','UTF-8','-cp',cp,'-d',str(output),*files],check=True)
    subprocess.run([str(jdk/'bin/java.exe'),'-cp',str(output)+';'+cp,'com.eidolon.sc.mobile.SharedStoreProbe'],check=True)
