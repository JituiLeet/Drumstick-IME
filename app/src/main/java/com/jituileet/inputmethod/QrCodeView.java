package com.jituileet.inputmethod;
import android.content.Context; import android.graphics.Bitmap; import android.graphics.Canvas; import android.graphics.Color; import android.graphics.Paint; import android.view.View;
import com.google.zxing.BarcodeFormat; import com.google.zxing.MultiFormatWriter; import com.google.zxing.common.BitMatrix;
public final class QrCodeView extends View {
    private Bitmap bitmap; private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    public QrCodeView(Context c){super(c);setFocusable(false);}
    public void setText(String text){try{BitMatrix m=new MultiFormatWriter().encode(text,BarcodeFormat.QR_CODE,420,420);Bitmap b=Bitmap.createBitmap(420,420,Bitmap.Config.ARGB_8888);for(int y=0;y<420;y++)for(int x=0;x<420;x++)b.setPixel(x,y,m.get(x,y)?Color.BLACK:Color.WHITE);bitmap=b;}catch(Exception ignored){bitmap=null;}invalidate();}
    protected void onDraw(Canvas c){super.onDraw(c);c.drawColor(Color.WHITE);if(bitmap!=null)c.drawBitmap(bitmap,null,new android.graphics.Rect(0,0,getWidth(),getHeight()),paint);}
}
