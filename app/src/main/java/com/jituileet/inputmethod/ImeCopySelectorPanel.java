package com.jituileet.inputmethod;
import android.content.*;import android.graphics.Color;import android.view.*;import android.widget.*;
/** Copy selection UI hosted inside the IME window; avoids Dialog window-token crashes on TV. */
public final class ImeCopySelectorPanel extends LinearLayout {
 private final DrumstickImeService service; private final String text; private final boolean zh; private int start=0,end=0; private final TextView preview;
 public ImeCopySelectorPanel(DrumstickImeService s,String t){super(s);service=s;text=t==null?"":t;zh=s.isChineseLanguagePublic();end=text.length();setOrientation(VERTICAL);setPadding(18,8,18,18);setFocusable(true);setFocusableInTouchMode(true);setBackgroundColor(Prefs.dark(s)?0xFF202124:Color.WHITE);
  TextView title=new TextView(s);title.setText(zh?"选择要复制的文字":"Select text to copy");title.setTextSize(21);title.setTextColor(Prefs.dark(s)?0xFFF1F3F4:0xFF202124);title.setPadding(18,18,18,10);addView(title,new LayoutParams(-1,60));
  preview=new TextView(s);preview.setTextSize(18);preview.setTextColor(Prefs.dark(s)?0xFFF1F3F4:0xFF202124);preview.setPadding(18,10,18,10);addView(preview,new LayoutParams(-1,0,1));update();
  LinearLayout row=new LinearLayout(s);row.setOrientation(HORIZONTAL);String[] labels={zh?"左移":"Left",zh?"右移":"Right",zh?"全部":"All",zh?"复制":"Copy",zh?"取消":"Cancel"};for(String l:labels){Button b=new Button(s);b.setText(l);b.setAllCaps(false);b.setFocusable(true);row.addView(b,new LinearLayout.LayoutParams(0,64,1));if(l.equals(labels[0]))b.setOnClickListener(v->{start=Math.max(0,start-1);update();});else if(l.equals(labels[1]))b.setOnClickListener(v->{end=Math.min(text.length(),end+1);update();});else if(l.equals(labels[2]))b.setOnClickListener(v->{start=0;end=text.length();update();});else if(l.equals(labels[3]))b.setOnClickListener(v->copy());else b.setOnClickListener(v->service.restoreKeyboardView());}addView(row);
  postDelayed(()->row.getChildAt(0).requestFocus(),70);
 }
 private void update(){int a=Math.min(start,end),b=Math.max(start,end);preview.setText(text.substring(0,a)+"["+text.substring(a,b)+"]"+text.substring(b));}
 private void copy(){int a=Math.min(start,end),b=Math.max(start,end);String out=text.substring(a,b);if(out.isEmpty())out=text;try{android.content.ClipboardManager cm=(android.content.ClipboardManager)service.getSystemService(Context.CLIPBOARD_SERVICE);if(cm==null)throw new IllegalStateException();cm.setPrimaryClip(android.content.ClipData.newPlainText("Drumstick",out));ClipboardHistory.add(service,out);Toast.makeText(service,zh?"已复制":"Copied",Toast.LENGTH_SHORT).show();service.restoreKeyboardView();}catch(Throwable e){Toast.makeText(service,zh?"复制失败":"Copy failed",Toast.LENGTH_SHORT).show();}}
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        if(e.getAction()!=KeyEvent.ACTION_DOWN) return true;
        int k=e.getKeyCode();
        if(k==KeyEvent.KEYCODE_DPAD_CENTER || k==KeyEvent.KEYCODE_ENTER){ View f=findFocus(); if(f!=null && f.isClickable()){ f.performClick(); return true; } return true; }
        int dir=k==KeyEvent.KEYCODE_DPAD_UP?View.FOCUS_UP:k==KeyEvent.KEYCODE_DPAD_DOWN?View.FOCUS_DOWN:k==KeyEvent.KEYCODE_DPAD_LEFT?View.FOCUS_LEFT:k==KeyEvent.KEYCODE_DPAD_RIGHT?View.FOCUS_RIGHT:0;
        if(dir!=0){ View f=findFocus(); if(f==null) f=this; View n=f.focusSearch(dir); if(n!=null){n.requestFocus(); return true;} }
        return super.dispatchKeyEvent(e);
    }

}
