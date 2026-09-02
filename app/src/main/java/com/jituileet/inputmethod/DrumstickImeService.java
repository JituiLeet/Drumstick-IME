package com.jituileet.inputmethod;

import android.content.*;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.os.*;
import android.view.*;
import android.view.inputmethod.InputConnection;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.widget.Button;
import java.util.*;

public class DrumstickImeService extends InputMethodService {
    @Override public boolean onEvaluateFullscreenMode() { return false; }
    @Override public boolean onEvaluateInputViewShown() { return true; }
    private DrumstickKeyboardView view;
    private ChineseEngine engine;
    private PhoneInputServer phoneServer;
    private boolean physicalMode;
    private boolean shift;
    private boolean caps;
    private boolean numericMode;
    private boolean physicalDetectionAnnounced;
    private View activePanel;
    private final BroadcastReceiver rimeReloadReceiver = new BroadcastReceiver(){ @Override public void onReceive(Context c, Intent i){ reloadRime(); } };

    @Override public void onCreate(){ super.onCreate();
        engine = new ChineseEngine(this, (cs, pre) -> { if(view!=null) view.setCandidates(cs, pre); InputConnection ic=getCurrentInputConnection(); if(ic!=null){ if(pre!=null && !pre.isEmpty() && !engine.isEnglish()) ic.setComposingText(pre,1); else ic.finishComposingText(); } });
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(rimeReloadReceiver, new IntentFilter("com.jituileet.inputmethod.RELOAD_RIME"), Context.RECEIVER_NOT_EXPORTED); else registerReceiver(rimeReloadReceiver, new IntentFilter("com.jituileet.inputmethod.RELOAD_RIME"));
        engine.setEnglish(!isChineseLanguage());
        phoneServer = new PhoneInputServer(this, text -> new Handler(Looper.getMainLooper()).post(() -> commitText(text)));
        if(Prefs.phone(this)) phoneServer.start();
    }
    @Override public void onFinishInputView(boolean finishingInput){ try{ InputConnection ic=getCurrentInputConnection(); if(ic!=null) ic.finishComposingText(); }catch(Throwable ignored){} super.onFinishInputView(finishingInput); }
    @Override public void onDestroy(){ try{ unregisterReceiver(rimeReloadReceiver); if(phoneServer!=null)phoneServer.stop(); if(engine!=null)engine.destroy(); }catch(Throwable ignored){} super.onDestroy(); }
    private boolean isChineseLanguage(){ String l=Prefs.language(this); return l.equals("zh") || (l.equals("auto") && Locale.getDefault().getLanguage().equals("zh")); }
    @Override public void onStartInputView(View info, boolean restarting){ super.onStartInputView(info,restarting); if(view!=null) view.setVisibility(View.VISIBLE); }
    @Override public View onCreateInputView(){
        view=new DrumstickKeyboardView(this);
        view.setColors(Prefs.color(this), Prefs.dark(this));
        view.setMicVisible(hasMicrophone());
        activePanel=null;
        return view;
    }
    @Override public void onStartInput(android.view.inputmethod.EditorInfo attribute, boolean restarting){ super.onStartInput(attribute,restarting); setExtractViewShown(false); physicalMode=false; physicalDetectionAnnounced=false; shift=false; caps=false; numericMode=false; if(engine!=null) engine.resetComposition(); if(view!=null){view.setPhysical(false);view.setCandidates(java.util.Collections.<String>emptyList(),"");} }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event){
        // The IME only owns D-pad navigation while its own input/panel UI is shown.
        // Never consume D-pad events globally when the IME is hidden; doing so steals
        // DOWN/UP/LEFT/RIGHT from the foreground TV application.
        if(keyCode==KeyEvent.KEYCODE_BACK && activePanel!=null){ restoreKeyboardView(); return true; }
        boolean dpad = keyCode==KeyEvent.KEYCODE_DPAD_LEFT || keyCode==KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode==KeyEvent.KEYCODE_DPAD_UP || keyCode==KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode==KeyEvent.KEYCODE_DPAD_CENTER;
        if(dpad){
            if((view==null || view.getVisibility()!=View.VISIBLE) && activePanel==null) return super.onKeyDown(keyCode,event);
            if(activePanel!=null) return dispatchPanelKey(event);
            if(view!=null) return view.dispatchKeyEvent(event);
            return super.onKeyDown(keyCode,event);
        }

        boolean hardware = PhysicalKeyboardDetector.isHardwareKeyboard(event);
        boolean physicalControl = keyCode==KeyEvent.KEYCODE_SHIFT_LEFT || keyCode==KeyEvent.KEYCODE_SHIFT_RIGHT
                || keyCode==KeyEvent.KEYCODE_CAPS_LOCK || keyCode==KeyEvent.KEYCODE_PAGE_UP
                || keyCode==KeyEvent.KEYCODE_PAGE_DOWN;
        if(Prefs.keyboard(this) && activePanel==null && isInputViewShown() && hardware && (physicalMode || PhysicalKeyboardDetector.isTypingKey(event))){
            if(!physicalDetectionAnnounced){
                physicalDetectionAnnounced=true;
                if(Prefs.hardwareNotice(this)) Toast.makeText(this, isChineseLanguage()?"检测到实体键盘，已切换键盘输入模式":"Physical keyboard detected, switched to keyboard input mode", Toast.LENGTH_SHORT).show();
            }
            if(!physicalMode){ physicalMode=true; if(view!=null)view.setPhysical(true); }
            if((event.isCtrlPressed()) && (keyCode==KeyEvent.KEYCODE_C || keyCode==KeyEvent.KEYCODE_X || keyCode==KeyEvent.KEYCODE_A || keyCode==KeyEvent.KEYCODE_V)){
                InputConnection ic=getCurrentInputConnection();
                if(ic!=null){
                    if(keyCode==KeyEvent.KEYCODE_C) ic.performContextMenuAction(android.R.id.copy);
                    else if(keyCode==KeyEvent.KEYCODE_X) ic.performContextMenuAction(android.R.id.cut);
                    else if(keyCode==KeyEvent.KEYCODE_A) ic.performContextMenuAction(android.R.id.selectAll);
                    else if(keyCode==KeyEvent.KEYCODE_V) ic.performContextMenuAction(android.R.id.paste);
                }
                return true;
            }
            if(keyCode==KeyEvent.KEYCODE_PAGE_UP || keyCode==KeyEvent.KEYCODE_PAGE_DOWN){
                if(view!=null) return view.dispatchKeyEvent(event);
            }
            return handlePhysicalKey(keyCode,event);
        }
        return super.onKeyDown(keyCode,event);
    }

    private boolean dispatchPanelKey(KeyEvent event){
        if(activePanel==null) return false;
        int k=event.getKeyCode();
        if(k==KeyEvent.KEYCODE_DPAD_CENTER || k==KeyEvent.KEYCODE_ENTER){
            View f=activePanel.findFocus();
            if(f!=null && f.isClickable()){ f.performClick(); return true; }
            return true;
        }
        int dir = k==KeyEvent.KEYCODE_DPAD_UP ? View.FOCUS_UP : k==KeyEvent.KEYCODE_DPAD_DOWN ? View.FOCUS_DOWN
                : k==KeyEvent.KEYCODE_DPAD_LEFT ? View.FOCUS_LEFT : k==KeyEvent.KEYCODE_DPAD_RIGHT ? View.FOCUS_RIGHT : 0;
        if(dir!=0){
            View f=activePanel.findFocus();
            if(f==null) f=activePanel;
            View next=f.focusSearch(dir);
            if(next!=null){ next.requestFocus(); return true; }
            // Let a scroll container consume vertical movement at its boundary.
            if((dir==View.FOCUS_DOWN || dir==View.FOCUS_UP) && activePanel instanceof android.widget.ScrollView){
                return activePanel.dispatchKeyEvent(event);
            }
            return true;
        }
        return false;
    }

    @Override public boolean onKeyUp(int keyCode, KeyEvent event){
        if(physicalMode && (keyCode==KeyEvent.KEYCODE_SHIFT_LEFT || keyCode==KeyEvent.KEYCODE_SHIFT_RIGHT || keyCode==KeyEvent.KEYCODE_CAPS_LOCK)) return true;
        return super.onKeyUp(keyCode,event);
    }
    private boolean isPhysicalTextKey(int k, KeyEvent e){ return (k>=KeyEvent.KEYCODE_A && k<=KeyEvent.KEYCODE_Z) || k==KeyEvent.KEYCODE_SPACE || k==KeyEvent.KEYCODE_DEL || k==KeyEvent.KEYCODE_ENTER || k==KeyEvent.KEYCODE_SHIFT_LEFT || k==KeyEvent.KEYCODE_SHIFT_RIGHT || k==KeyEvent.KEYCODE_CAPS_LOCK || k==KeyEvent.KEYCODE_COMMA || k==KeyEvent.KEYCODE_PERIOD || k==KeyEvent.KEYCODE_APOSTROPHE || k==KeyEvent.KEYCODE_SEMICOLON || k==KeyEvent.KEYCODE_SLASH || k==KeyEvent.KEYCODE_MINUS || k==KeyEvent.KEYCODE_EQUALS || (k>=KeyEvent.KEYCODE_0 && k<=KeyEvent.KEYCODE_9); }
    private boolean handlePhysicalKey(int k, KeyEvent e){
        if(k==KeyEvent.KEYCODE_SHIFT_LEFT||k==KeyEvent.KEYCODE_SHIFT_RIGHT){ engine.setEnglish(!engine.isEnglish()); if(view!=null)view.invalidate(); return true; }
        if(k==KeyEvent.KEYCODE_CAPS_LOCK){ caps=!caps; engine.setEnglish(true); if(view!=null)view.invalidate(); return true; }
        InputConnection ic=getCurrentInputConnection();
        if(k==KeyEvent.KEYCODE_DEL){
            if(!engine.isEnglish() && engine.hasComposing()) engine.backspace();
            else if(ic!=null) ic.deleteSurroundingText(1,0);
            return true;
        }
        if(k==KeyEvent.KEYCODE_ENTER){
            if(!engine.isEnglish() && engine.hasComposing()){ String out=engine.commitFirst(); if(out!=null&&!out.isEmpty()) commitText(out); else if(ic!=null) sendEnter(ic); }
            else if(ic!=null) sendEnter(ic);
            return true;
        }
        if(k==KeyEvent.KEYCODE_SPACE){
            if(engine.isEnglish()) commitText(" ");
            else if(engine.hasComposing()){ String out=engine.commitFirst(); if(out!=null&&!out.isEmpty()) commitText(out); }
            else commitText(" ");
            return true;
        }
        if(k==KeyEvent.KEYCODE_COMMA||k==KeyEvent.KEYCODE_PERIOD){
            String z=(k==KeyEvent.KEYCODE_COMMA)?(engine.isEnglish()?",":"，"):(engine.isEnglish()?".":"。");
            if(!engine.isEnglish()&&engine.hasComposing()){String out=engine.commitFirst();if(out!=null&&!out.isEmpty())commitText(out);}
            commitText(z); return true;
        }
        int meta=e.getMetaState();
        char c=(char)e.getUnicodeChar(meta);
        if(c!=0){
            if(!engine.isEnglish() && Character.isLetter(c)) { engine.input(String.valueOf(Character.toLowerCase(c))); return true; }
            if(!engine.isEnglish() && !Character.isLetterOrDigit(c)){
                if(engine.hasComposing()){String out=engine.commitFirst();if(out!=null&&!out.isEmpty())commitText(out);}
                commitText(String.valueOf(c)); return true;
            }
            if(engine.isEnglish()){ commitText(String.valueOf(c)); return true; }
        }
        return false;
    }
    private void sendEnter(InputConnection ic){ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER));ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER));}

    public void moveCursor(int direction){
        InputConnection ic=getCurrentInputConnection();
        if(ic==null)return;
        try{
            android.view.inputmethod.ExtractedTextRequest req=new android.view.inputmethod.ExtractedTextRequest();
            req.token=1;
            android.view.inputmethod.ExtractedText et=ic.getExtractedText(req,0);
            if(et!=null){
                int pos=et.selectionStart + direction;
                pos=Math.max(0, Math.min(et.text==null?0:et.text.length(), pos));
                ic.setSelection(pos,pos);
            }else{
                if(direction<0) ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_LEFT));
                else ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT));
            }
        }catch(Throwable ignored){}
    }

    public void press(String label){
        InputConnection ic=getCurrentInputConnection(); if(ic==null)return;
        if(label.equals("Settings")) label="设置"; if(label.equals("Copy")) label="复制"; if(label.equals("Clipboard")) label="剪贴板"; if(label.equals("Voice")) label="语音"; if(label.equals("Hide")) label="隐藏"; if(label.equals("Space")) label="空格"; if(label.equals("Enter")) label="回车"; if(label.equals("Backspace")) label="⌫"; if(label.equals("Enter")) label="↵"; if(label.equals("ZH/EN")) label="中/英";
        if(label.equals("设置")){ Toast.makeText(this,isChineseLanguage()?"已打开设置":"Settings opened",Toast.LENGTH_SHORT).show(); showSettingsPanel(); return; }
        if(label.equals("隐藏")){ hideInputViewTemporarily(); return; }
        if(label.equals("剪贴板")){ Toast.makeText(this,isChineseLanguage()?"已打开剪贴板":"Clipboard opened",Toast.LENGTH_SHORT).show(); showClipboardHistory(); return; }
        if(label.equals("复制")){ Toast.makeText(this,isChineseLanguage()?"正在读取可复制文字":"Reading copyable text",Toast.LENGTH_SHORT).show(); showCopyDialog(); return; }
        if(label.equals("语音")){ try {
            final android.speech.SpeechRecognizer sr=android.speech.SpeechRecognizer.createSpeechRecognizer(this);
            sr.setRecognitionListener(new android.speech.RecognitionListener(){
                public void onResults(Bundle r){ java.util.ArrayList<String> a=r.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION); if(a!=null&&!a.isEmpty()) commitText(a.get(0)); sr.destroy(); }
                public void onError(int e){ sr.destroy(); } public void onReadyForSpeech(Bundle b){} public void onBeginningOfSpeech(){} public void onRmsChanged(float v){} public void onBufferReceived(byte[] b){} public void onEndOfSpeech(){} public void onPartialResults(Bundle b){} public void onEvent(int a,Bundle b){}
            });
            Intent i=new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH); i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); sr.startListening(i);
        } catch(Exception e){ Toast.makeText(this,"设备不支持语音输入",Toast.LENGTH_SHORT).show(); } return; }
        if(label.equals("←")){ moveCursor(-1); return; }\n        if(label.equals("→")){ moveCursor(1); return; }\n        if(label.equals("⌫")){ ic.deleteSurroundingText(1,0); return; }
        if(label.equals("空格")){ if(engine.isEnglish()) commitText(" "); else { String out=engine.commitFirst(); if(out!=null&&!out.isEmpty()) commitText(out); } return; }
        if(label.equals("回车") || label.equals("↵")){ ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER)); ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER)); return; }
        if(label.equals("中/英")){ engine.setEnglish(!engine.isEnglish()); return; }
        if(label.equals("🌐")){ int m=(Prefs.keyboardLayout(this)+1)%3; Prefs.keyboardLayout(this,m); if(m==2) engine.setEnglish(true); else engine.setEnglish(false); if(view!=null)view.setKeyboardLayout(m); return; }
        if(label.equals("?123")){ numericMode=!numericMode; if(view!=null)view.setNumericMode(numericMode); return; }
        if(label.equals("ABC")){ numericMode=false; if(view!=null)view.setNumericMode(false); return; }
        if(label.equals("Shift")){ engine.setEnglish(!engine.isEnglish()); view.invalidate(); return; }
        if(label.equals("Caps")){ caps=!caps; engine.setEnglish(true); view.invalidate(); return; }
        if(label.equals("abc")||label.equals("def")||label.equals("ghi")||label.equals("jkl")||label.equals("mno")||label.equals("pqrs")||label.equals("tuv")||label.equals("wxyz")){ if(!engine.isEnglish()){ char first=label.charAt(0); engine.input(String.valueOf(first)); } else commitText(label); return; }
        if(label.length()==1 && Character.isLetter(label.charAt(0))){ if(engine.isEnglish()){ commitText(String.valueOf(caps?Character.toUpperCase(label.charAt(0)):label.charAt(0))); } else { String out=engine.input(label); if(out!=null&&!out.isEmpty()) commitText(out); } return; }
        commitText(label);
    }
    private void commitText(String text){ InputConnection ic=getCurrentInputConnection(); if(ic!=null){ ic.finishComposingText(); ic.commitText(text,1); } }
    public void commitEngineResult(String text){ if(text!=null&&!text.isEmpty()) commitText(text); }
    private boolean hasMicrophone(){ AudioManager a=(AudioManager)getSystemService(AUDIO_SERVICE); return a!=null && getPackageManager().hasSystemFeature("android.hardware.microphone"); }
    private void hideInputViewTemporarily(){
        try {
            activePanel=null;
            if(view!=null){ view.setVisibility(View.GONE); view.clearFocus(); }
        } catch(Throwable ignored) {}
    }

    private void showClipboardHistory(){
        ArrayList<String> items=ClipboardHistory.load(this);
        try { android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE); if(cm!=null&&cm.hasPrimaryClip()&&cm.getPrimaryClip()!=null&&cm.getPrimaryClip().getItemCount()>0){ CharSequence cs=cm.getPrimaryClip().getItemAt(0).coerceToText(this); if(cs!=null&&cs.length()>0){ClipboardHistory.add(this,cs.toString());items=ClipboardHistory.load(this);} } } catch(Throwable ignored) {}
        activePanel=new ImeClipboardPanel(this,items); setInputView(activePanel);
    }

    private void showCopyDialog(){
        // Copy intentionally reads screen content through the optional accessibility
        // service. InputConnection is not used as a fallback because it cannot read
        // arbitrary text outside the current editor.
        try {
            if(!DrumstickAccessibilityService.isEnabled(this)){
                activePanel=new ImeAccessibilityCopyPanel(this);
                setInputView(activePanel);
                return;
            }
            String text=DrumstickAccessibilityService.captureVisibleText(this);
            if(text==null || text.trim().isEmpty()){
                Toast.makeText(this,isChineseLanguage()?"没有检测到可复制的文字，请尝试更换页面。":"No copyable text was detected. Try another screen.",Toast.LENGTH_SHORT).show();
                return;
            }
            activePanel=new ImeCopySelectorPanel(this,text);
            setInputView(activePanel);
        }catch(Throwable e){
            Toast.makeText(this,isChineseLanguage()?"读取屏幕文字失败":"Failed to read screen text",Toast.LENGTH_SHORT).show();
        }
    }

    public void showSettingsPanel(){ activePanel=new ImeSettingsPanel(this); setInputView(activePanel); }
    public void showClipboardHistoryPublic(){ showClipboardHistory(); }
    public void showClipboardEdit(int index,String text){ activePanel=new ImeClipboardEditPanel(this,index,text); setInputView(activePanel); }
    public void restoreKeyboardView(){ activePanel=null; if(view==null){view=new DrumstickKeyboardView(this);view.setColors(Prefs.color(this),Prefs.dark(this));view.setMicVisible(hasMicrophone());} setInputView(view); view.requestFocus(); }
    public void refreshImeView(){ if(view!=null){view.setColors(Prefs.color(this),Prefs.dark(this));view.invalidate();} }
    public void openSystemImeSettings(){ try{startActivity(new Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(Throwable t){Toast.makeText(this,isChineseLanguage()?"无法打开系统设置":"Unable to open system settings",Toast.LENGTH_SHORT).show();} }
    public void commitClipboardText(String text){ if(text==null)return; InputConnection ic=getCurrentInputConnection(); if(ic!=null){ic.commitText(text,1);} }
    public String engineChoose(int index){ return engine.choose(index); }
    public boolean engineChangePage(boolean backward){ return engine.changePage(backward); }
    public void setPhoneInput(boolean on){ Prefs.phone(this,on); if(on)phoneServer.start();else phoneServer.stop(); }
    public String phoneUrl(){ return phoneServer.getUrl(); }
    public boolean isChineseLanguagePublic(){ return isChineseLanguage(); }
    public DrumstickKeyboardView keyboardView(){ return view; }
    public void reloadRime(){ if(engine!=null) engine.destroy(); engine=new ChineseEngine(this,(cs,pre)->{if(view!=null)view.setCandidates(cs,pre); InputConnection ic=getCurrentInputConnection(); if(ic!=null){if(pre!=null&&!pre.isEmpty()&&!engine.isEnglish()) ic.setComposingText(pre,1); else ic.finishComposingText();}}); engine.setEnglish(!isChineseLanguage()); }
    public void clearUsage(){ if(engine!=null) engine.clearUsage(); }
    public void startPhoneInput(){ if(phoneServer!=null) phoneServer.start(); }
    public void stopPhoneInput(){ if(phoneServer!=null) phoneServer.stop(); }
    public void openDictionaryPicker(){ startActivity(new Intent(this, SettingsActivity.class).putExtra("open_dictionary",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
}
