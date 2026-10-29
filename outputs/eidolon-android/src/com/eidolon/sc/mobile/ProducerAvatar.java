package com.eidolon.sc.mobile;

import android.content.Context;
import android.graphics.*;
import android.media.ExifInterface;
import android.net.Uri;
import java.io.*;

/** A private, sanitized local portrait; the selected URI is never exposed to WebView. */
final class ProducerAvatar {
    private static File file(Context c){return new File(c.getFilesDir(),"producer-avatar.jpg");}
    static String url(Context c){File f=file(c);return f.isFile()?"/personal/avatar.jpg?v="+f.lastModified():"";}
    static InputStream open(Context c)throws IOException{return new FileInputStream(file(c));}
    static void clear(Context c)throws IOException{File f=file(c);if(f.exists()&&!f.delete())throw new IOException("无法恢复默认头像，请重试");}
    static void save(Context c,Uri uri)throws Exception{
        byte[] raw;try(InputStream in=c.getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("无法读取所选图片");raw=StoryFiles.read(in,10*1024*1024);}
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(raw,0,raw.length,bounds);
        if(bounds.outWidth<=0||bounds.outHeight<=0||(long)bounds.outWidth*bounds.outHeight>200000000L)throw new IOException("请选择可读取的照片（文件不超过10MB）");
        BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>1024)options.inSampleSize*=2;options.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap decoded=null,oriented=null,square=null;File temp=new File(c.getFilesDir(),"producer-avatar.tmp");
        try{
            decoded=BitmapFactory.decodeByteArray(raw,0,raw.length,options);if(decoded==null)throw new IOException("图片格式不受支持，请换一张照片");
            int orientation=ExifInterface.ORIENTATION_NORMAL;try{orientation=new ExifInterface(new ByteArrayInputStream(raw)).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);}catch(IOException ignored){}
            Matrix m=new Matrix();switch(orientation){
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:m.setScale(-1,1);break;
                case ExifInterface.ORIENTATION_ROTATE_180:m.setRotate(180);break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL:m.setScale(1,-1);break;
                case ExifInterface.ORIENTATION_TRANSPOSE:m.setRotate(90);m.postScale(-1,1);break;
                case ExifInterface.ORIENTATION_ROTATE_90:m.setRotate(90);break;
                case ExifInterface.ORIENTATION_TRANSVERSE:m.setRotate(270);m.postScale(-1,1);break;
                case ExifInterface.ORIENTATION_ROTATE_270:m.setRotate(270);break;
            }
            oriented=m.isIdentity()?decoded:Bitmap.createBitmap(decoded,0,0,decoded.getWidth(),decoded.getHeight(),m,true);
            square=Bitmap.createBitmap(384,384,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(square);canvas.drawColor(Color.WHITE);
            int size=Math.min(oriented.getWidth(),oriented.getHeight()),left=(oriented.getWidth()-size)/2,top=(oriented.getHeight()-size)/2;
            canvas.drawBitmap(oriented,new Rect(left,top,left+size,top+size),new Rect(0,0,384,384),new Paint(Paint.FILTER_BITMAP_FLAG));
            try(FileOutputStream out=new FileOutputStream(temp)){if(!square.compress(Bitmap.CompressFormat.JPEG,90,out))throw new IOException("头像处理失败");out.getFD().sync();}
            android.system.Os.rename(temp.getAbsolutePath(),file(c).getAbsolutePath());
        }finally{if(square!=null)square.recycle();if(oriented!=null&&oriented!=decoded)oriented.recycle();if(decoded!=null)decoded.recycle();if(temp.exists())temp.delete();}
    }
    private ProducerAvatar(){}
}
