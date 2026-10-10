package com.example.teleprompter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(14,17,20), PANEL = Color.rgb(23,27,31);
    private static final int TEXT = Color.rgb(245,237,222), MINT = Color.rgb(107,204,173);
    private static final int CORAL=Color.rgb(240,133,111), MUTED=Color.rgb(140,155,153), SURFACE=Color.rgb(29,36,39);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private final ArrayList<Draft> drafts = new ArrayList<>();
    private Draft current;
    private LinearLayout header, tools;
    private ScrollView scroll;
    private TextView reader, title, progress, speedValue, modeStatus;
    private ProgressBar progressTrack;
    private View guide;
    private Button play, undo;
    private boolean playing, counting, mirrored, showGuide, restoring, completed;
    private int fontSize, speed, margin, countdown;
    private long lastFrame, countdownEnd;
    private float position;
    private String undoText, undoId;
    private EditText activeEditor, activeTitle;
    private Draft editingDraft;
    private boolean landscape;

    private static class Draft {
        String id, title, text;
        int anchor;
        Draft(String id, String title, String text, int anchor) {
            this.id=id; this.title=title; this.text=text; this.anchor=anchor;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs=getSharedPreferences("teleprompter", MODE_PRIVATE);
        fontSize=prefs.getInt("fontSize",28);
        speed=Math.max(1,Math.min(10,prefs.getInt("speed",5)));
        margin=Math.max(24,Math.min(160,prefs.getInt("pageMargin",24)));
        mirrored=prefs.getBoolean("mirrored",false);
        showGuide=prefs.getBoolean("guide",true);
        countdown=prefs.getInt("countdown",3);
        undoText=prefs.getString("undoText",null); undoId=prefs.getString("undoId",null);
        loadDrafts();
        buildScreen();
    }

    private void loadDrafts() {
        String selected=prefs.getString("selected","");
        try {
            JSONArray list=new JSONArray(prefs.getString("drafts","[]"));
            for(int i=0;i<list.length();i++) {
                JSONObject o=list.getJSONObject(i);
                Draft d=new Draft(o.getString("id"),o.optString("title","未命名稿件"),o.optString("text",""),o.optInt("anchor",0));
                drafts.add(d); if(d.id.equals(selected)) current=d;
            }
        } catch(Exception e) {
            // Keep malformed storage for recovery instead of silently overwriting it.
            prefs.edit().putString("draftsRecovery",prefs.getString("drafts","[]")).apply();
        }
        if(drafts.isEmpty()) drafts.add(new Draft(UUID.randomUUID().toString(),"我的第一篇稿件",
                prefs.getString("script","今天，我们只需要\n把注意力放在眼前。\n让表达自然发生。"),0));
        if(current==null) current=drafts.get(0);
    }

    private void buildScreen() {
        landscape=getResources().getConfiguration().orientation==2;
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(android.os.Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets b=insets.getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());
                v.setPadding(b.left,b.top,b.right,b.bottom);
            } else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        header=new LinearLayout(this); header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(landscape?24:22),dp(landscape?4:12),dp(landscape?24:22),dp(landscape?4:12));
        LinearLayout brand=row(); brand.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark=label("句",17); mark.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        mark.setTextColor(CORAL); mark.setGravity(Gravity.CENTER); mark.setBackground(shape(Color.rgb(49,34,32),12));
        brand.addView(mark,new LinearLayout.LayoutParams(dp(36),dp(36)));
        LinearLayout name=new LinearLayout(this); name.setOrientation(LinearLayout.VERTICAL); name.setPadding(dp(10),0,0,0);
        TextView wordmark=label("句中",21); wordmark.setLetterSpacing(.08f); wordmark.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); name.addView(wordmark);
        if(!landscape) {TextView tagline=label("让表达，自然发生",11); tagline.setTextColor(MUTED); name.addView(tagline);}
        brand.addView(name,new LinearLayout.LayoutParams(landscape?dp(94):0,-2,landscape?0:1));
        if(landscape) {
            title=label("",13); title.setTextColor(MUTED); title.setSingleLine(); title.setEllipsize(TextUtils.TruncateAt.END);
            title.setPadding(dp(12),0,dp(12),0); title.setOnClickListener(v->showLibrary());
            brand.addView(title,new LinearLayout.LayoutParams(0,dp(48),1)); title.setGravity(Gravity.CENTER_VERTICAL);
            compactAction(brand,"稿件",this::showLibrary,MUTED,dp(64));
            compactAction(brand,"编辑",()->edit(current),TEXT,dp(64));
            compactAction(brand,"清空",this::clearDraft,Color.rgb(183,126,115),dp(64));
            undo=compactAction(brand,"撤销",this::undoClear,MINT,dp(64));
        } else {
            compactAction(brand,"稿件  ›",this::showLibrary,MINT,dp(76));
        }
        header.addView(brand,new LinearLayout.LayoutParams(-1,dp(landscape?48:54)));
        if(!landscape) {
            LinearLayout document=row(); document.setGravity(Gravity.CENTER_VERTICAL); document.setPadding(dp(14),0,dp(4),0); document.setBackground(shape(PANEL,14));
            title=label("",14); title.setSingleLine(); title.setEllipsize(TextUtils.TruncateAt.END); title.setGravity(Gravity.CENTER_VERTICAL);
            title.setOnClickListener(v->showLibrary()); document.addView(title,new LinearLayout.LayoutParams(0,dp(52),1));
            compactAction(document,"编辑",()->edit(current),MINT,dp(52));
            compactAction(document,"清空",this::clearDraft,Color.rgb(183,126,115),dp(52));
            undo=compactAction(document,"撤销",this::undoClear,MINT,dp(52));
            LinearLayout.LayoutParams docParams=new LinearLayout.LayoutParams(-1,dp(52)); docParams.topMargin=dp(14); header.addView(document,docParams);
        }
        root.addView(header);
        LinearLayout readingMeta=row(); readingMeta.setPadding(dp(landscape?28:26),0,dp(landscape?28:26),0); readingMeta.setGravity(Gravity.CENTER_VERTICAL);
        TextView preview=label("提词预览",11); preview.setTextColor(MUTED); preview.setLetterSpacing(.12f);
        readingMeta.addView(preview,new LinearLayout.LayoutParams(0,dp(26),1)); preview.setGravity(Gravity.CENTER_VERTICAL);
        modeStatus=label("",11); modeStatus.setTextColor(MINT); modeStatus.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);
        readingMeta.addView(modeStatus); root.addView(readingMeta);
        FrameLayout stage=new FrameLayout(this);
        guide=new View(this) {
            private final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(android.graphics.Canvas canvas) {
                super.onDraw(canvas); paint.setColor(MINT);
                android.graphics.Path arrow=new android.graphics.Path(); float cy=getHeight()/2f;
                arrow.moveTo(dp(6),cy-dp(4)); arrow.lineTo(dp(11),cy); arrow.lineTo(dp(6),cy+dp(4)); arrow.close(); canvas.drawPath(arrow,paint);
            }
        }; GradientDrawable band=shape(Color.argb(13,107,204,173),16); band.setStroke(dp(1),Color.argb(22,107,204,173)); guide.setBackground(band);
        FrameLayout.LayoutParams focus=new FrameLayout.LayoutParams(-1,dp(64),Gravity.CENTER_VERTICAL); focus.setMargins(dp(14),0,dp(14),0);
        stage.addView(guide,focus); guide.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        scroll=new ScrollView(this); scroll.setFillViewport(false); scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false); scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        reader=label("",fontSize); reader.setGravity(Gravity.CENTER_HORIZONTAL); reader.setLineSpacing(dp(12),1.12f);
        reader.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); reader.setIncludeFontPadding(false);
        reader.setTextIsSelectable(false); reader.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        scroll.addView(reader,new ScrollView.LayoutParams(-1,-2));
        stage.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        addFade(stage,Gravity.TOP); addFade(stage,Gravity.BOTTOM);
        root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        scroll.setOnClickListener(v->{if(playing||counting) pause();});
        scroll.setOnTouchListener((v,event)->{
            if(event.getAction()==android.view.MotionEvent.ACTION_DOWN) {completed=false; if(playing||counting) pause();}
            if(event.getAction()==android.view.MotionEvent.ACTION_UP) v.performClick();
            return false;
        });
        scroll.getViewTreeObserver().addOnScrollChangedListener(()->{ if(!restoring) captureAnchor(); updateProgress(); });
        scroll.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            if(r-l!=or-ol || b-t!=ob-ot) applyReadingLayout();
        });
        LinearLayout footer=new LinearLayout(this); footer.setOrientation(LinearLayout.VERTICAL); footer.setBackground(shape(PANEL,24));
        footer.setPadding(dp(landscape?20:18),dp(landscape?10:16),dp(landscape?20:18),dp(landscape?6:10));
        LinearLayout status=row(); status.setGravity(Gravity.CENTER_VERTICAL);
        progress=label("",11); progress.setTextColor(MUTED); status.addView(progress,new LinearLayout.LayoutParams(0,dp(24),1));
        progressTrack=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); progressTrack.setMax(100);
        progressTrack.setProgressTintList(ColorStateList.valueOf(MINT)); progressTrack.setProgressBackgroundTintList(ColorStateList.valueOf(SURFACE));
        status.addView(progressTrack,new LinearLayout.LayoutParams(dp(landscape?100:64),dp(3))); footer.addView(status);
        LinearLayout transport=row(); transport.setGravity(Gravity.CENTER_VERTICAL);
        play=addButton(transport,"开始",this::togglePlay); play.setTextColor(BG); play.setTextSize(16); play.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); styleButton(play,CORAL,16);
        LinearLayout.LayoutParams playParams=new LinearLayout.LayoutParams(landscape?dp(144):0,dp(54),landscape?0:1); playParams.rightMargin=dp(12); play.setLayoutParams(playParams);
        LinearLayout speedControl=row(); speedControl.setGravity(Gravity.CENTER_VERTICAL); speedControl.setBackground(shape(SURFACE,16));
        compactAction(speedControl,"−",()->changeSpeed(-1),TEXT,dp(48)).setContentDescription("降低滚动速度");
        speedValue=label("",14); speedValue.setTextColor(MINT); speedValue.setGravity(Gravity.CENTER); speedControl.addView(speedValue,new LinearLayout.LayoutParams(dp(44),dp(48)));
        compactAction(speedControl,"+",()->changeSpeed(1),TEXT,dp(48)).setContentDescription("提高滚动速度");
        transport.addView(speedControl,new LinearLayout.LayoutParams(-2,dp(54)));
        tools=row(); tools.setGravity(Gravity.CENTER_VERTICAL);
        addButton(tools,"回到开头",()->{pause(); completed=false; current.anchor=0; restoreAnchor(); play.setText("开始"); save();});
        addButton(tools,"显示设置",this::showSettings); addButton(tools,"横 / 竖",this::rotateScreen);
        if(landscape) {LinearLayout.LayoutParams t=new LinearLayout.LayoutParams(0,dp(48),1); t.leftMargin=dp(20); transport.addView(tools,t);}
        LinearLayout.LayoutParams transportParams=new LinearLayout.LayoutParams(-1,dp(54)); transportParams.topMargin=dp(landscape?4:12); footer.addView(transport,transportParams);
        if(!landscape) {LinearLayout.LayoutParams t=new LinearLayout.LayoutParams(-1,dp(48)); t.topMargin=dp(8); footer.addView(tools,t);}
        LinearLayout.LayoutParams footerParams=new LinearLayout.LayoutParams(-1,-2); footerParams.setMargins(dp(landscape?20:16),dp(8),dp(landscape?20:16),dp(10)); root.addView(footer,footerParams);
        if(landscape) buildLandscapeColumns(root,stage,speedControl);
        setContentView(root); root.requestApplyInsets(); showCurrent();
    }

    private void detach(View view) {if(view.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)view.getParent()).removeView(view);}
    private void sideItem(LinearLayout column,View view,int height,int top) {
        detach(view); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(height)); p.topMargin=dp(top); column.addView(view,p);
    }
    private void buildLandscapeColumns(LinearLayout root,FrameLayout stage,LinearLayout speedControl) {
        root.removeAllViews(); root.setOrientation(LinearLayout.HORIZONTAL);
        header=new LinearLayout(this); header.setOrientation(LinearLayout.VERTICAL); header.setPadding(dp(12),dp(12),dp(12),dp(12));
        TextView brand=label("句中",20); brand.setTextColor(CORAL); brand.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        sideItem(header,brand,30,0);
        title.setPadding(0,0,0,0); title.setTextColor(TEXT); sideItem(header,title,26,0);
        modeStatus.setGravity(Gravity.START|Gravity.CENTER_VERTICAL); sideItem(header,modeStatus,22,0);
        sideItem(header,play,48,8);
        TextView speedCaption=label("滚动速度",11); speedCaption.setTextColor(MUTED); sideItem(header,speedCaption,18,12);
        for(int i=0;i<speedControl.getChildCount();i++) speedControl.getChildAt(i).setLayoutParams(new LinearLayout.LayoutParams(0,dp(48),1));
        sideItem(header,speedControl,48,0);
        sideItem(header,progress,22,8); sideItem(header,progressTrack,3,0);
        LinearLayout reset=row(); addButton(reset,"回到开头",()->{pause(); completed=false; current.anchor=0; restoreAnchor(); play.setText("开始"); save();}); sideItem(header,reset,44,4);
        LinearLayout draftsRow=row(); addButton(draftsRow,"稿件",this::showLibrary); addButton(draftsRow,"编辑",()->edit(current)); sideItem(header,draftsRow,48,0);

        tools=new LinearLayout(this); tools.setOrientation(LinearLayout.VERTICAL); tools.setPadding(dp(12),dp(12),dp(12),dp(12));
        TextView display=label("显示调整",16); display.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); sideItem(tools,display,30,0);
        TextView sizeCaption=label("文字大小",11); sizeCaption.setTextColor(MUTED); sideItem(tools,sizeCaption,20,8);
        LinearLayout size=row(); addButton(size,"小字",()->adjustFont(-2)); addButton(size,"大字",()->adjustFont(2)); size.setBackground(shape(SURFACE,12)); sideItem(tools,size,48,0);
        TextView marginCaption=label("台词边距",11); marginCaption.setTextColor(MUTED); sideItem(tools,marginCaption,20,12);
        SeekBar marginBar=new SeekBar(this); marginBar.setMax(136); marginBar.setProgress(margin-24); marginBar.setContentDescription("台词边距");
        marginBar.setProgressTintList(ColorStateList.valueOf(MINT)); marginBar.setThumbTintList(ColorStateList.valueOf(MINT));
        marginBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int p,boolean user){if(user){pause(); margin=p+24; applyReadingLayout(); save();}}
            public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){}
        }); sideItem(tools,marginBar,40,0);
        LinearLayout options=row(); addButton(options,"镜像",()->{mirrored=!mirrored; reader.setScaleX(mirrored?-1:1); updateProgress(); save();}); addButton(options,"横 / 竖",this::rotateScreen); sideItem(tools,options,48,4);
        LinearLayout settings=row(); addButton(settings,"更多设置",this::showSettings); sideItem(tools,settings,44,0);
        LinearLayout clearing=row(); Button clear=addButton(clearing,"清空",this::clearDraft); clear.setTextColor(Color.rgb(183,126,115)); detach(undo); clearing.addView(undo,new LinearLayout.LayoutParams(0,dp(48),1)); sideItem(tools,clearing,48,0);

        addSideColumn(root,header); root.addView(stage,new LinearLayout.LayoutParams(0,-1,.60f)); addSideColumn(root,tools);
    }
    private void addSideColumn(LinearLayout root,LinearLayout column) {
        ScrollView panel=new ScrollView(this); panel.setFillViewport(true); panel.setVerticalScrollBarEnabled(false); panel.setBackground(shape(PANEL,18)); panel.addView(column,new ScrollView.LayoutParams(-1,-2));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,.20f); p.setMargins(dp(8),dp(8),dp(8),dp(8)); root.addView(panel,p);
    }
    private void adjustFont(int delta) {pause(); fontSize=Math.max(16,Math.min(64,fontSize+delta)); reader.setTextSize(fontSize); applyReadingLayout(); save();}

    private void showCurrent() {
        completed=false;
        restoring=true;
        title.setText(current.title);
        reader.setText(current.text); reader.setTextSize(fontSize); reader.setScaleX(mirrored?-1:1);
        guide.setVisibility(showGuide?View.VISIBLE:View.GONE);
        undo.setVisibility(undoText!=null && current.id.equals(undoId)?View.VISIBLE:View.GONE);
        play.setText(current.anchor>0?"继续":"开始");
        scroll.post(this::applyReadingLayout);
    }

    private void applyReadingLayout() {
        if(scroll.getWidth()==0 || scroll.getHeight()==0) return;
        restoring=true;
        // Always leave at least 60% of the viewport for text on narrow screens.
        int horizontal=Math.min(dp(margin),scroll.getWidth()/5);
        int half=readingTop();
        reader.setPadding(horizontal,half,horizontal,half);
        FrameLayout.LayoutParams band=(FrameLayout.LayoutParams)guide.getLayoutParams(); band.height=reader.getLineHeight()+dp(12); guide.setLayoutParams(band);
        reader.post(this::restoreAnchor);
    }

    private void captureAnchor() {
        if(restoring || activeEditor!=null || reader.getLayout()==null || current.text.isEmpty()) return;
        int y=Math.max(0,scroll.getScrollY()+readingTop()-reader.getTotalPaddingTop());
        int line=reader.getLayout().getLineForVertical(y);
        current.anchor=reader.getLayout().getLineStart(line);
    }

    private void restoreAnchor() {
        if(reader.getLayout()==null) {restoring=false; return;}
        int offset=Math.max(0,Math.min(current.anchor,reader.length()));
        int line=reader.getLayout().getLineForOffset(offset);
        int y=reader.getTotalPaddingTop()+reader.getLayout().getLineTop(line)-readingTop();
        scroll.scrollTo(0,Math.max(0,Math.min(maxScroll(),y)));
        position=scroll.getScrollY(); restoring=false; updateProgress();
    }

    private int maxScroll() { return Math.max(0,reader.getHeight()-scroll.getHeight()); }
    private int readingTop() {return Math.max(0,(scroll.getHeight()-reader.getLineHeight())/2);}
    private String speedLabel() {return String.format(Locale.CHINA,"%.1f×",speed/5f);}
    private void updateProgress() {
        if(progress==null) return;
        speedValue.setText(speedLabel());
        modeStatus.setText(mirrored?"镜像已开":(playing?"●  正在提词":counting?"准备开始":"●  准备就绪"));
        if(completed) {progress.setText("已读完 · 100%"); progressTrack.setProgress(100); return;}
        int percent=current.text.isEmpty()?0:(maxScroll()==0?0:Math.min(100,Math.round(scroll.getScrollY()*100f/maxScroll())));
        progressTrack.setProgress(percent);
        progress.setText(current.text.trim().isEmpty()?(landscape?"请先编辑台词":"点击编辑，写下你的台词"):("已读 "+percent+"%"+(landscape?"":playing?"  ·  轻触正文暂停":"  ·  按自己的节奏表达")));
    }
    private void changeSpeed(int delta) {speed=Math.max(1,Math.min(10,speed+delta)); updateProgress(); save();}

    private void togglePlay() {
        if(playing||counting) {pause(); return;}
        if(current.text.trim().isEmpty()) {Toast.makeText(this,"请先添加台词",Toast.LENGTH_SHORT).show(); return;}
        if(completed || scroll.getScrollY()>=maxScroll()) {current.anchor=0; restoreAnchor();}
        completed=false;
        ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(reader.getWindowToken(),0);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        captureAnchor();
        if(!landscape) {restoring=true; header.setVisibility(View.GONE); tools.setVisibility(View.GONE);}
        counting=true; countdownEnd=SystemClock.uptimeMillis()+countdown*1000L;
        updateProgress();
        handler.post(countdownTick);
    }
    private final Runnable countdownTick=new Runnable() {
        @Override public void run() {
            if(!counting) return;
            long remaining=countdownEnd-SystemClock.uptimeMillis();
            if(remaining>0) {play.setText("取消 ("+((remaining+999)/1000)+")"); handler.postDelayed(this,100);}
            else {
                counting=false; playing=true; play.setText("暂停"); position=scroll.getScrollY(); lastFrame=0;
                updateProgress();
                Choreographer.getInstance().postFrameCallback(frame);
            }
        }
    };
    private final Choreographer.FrameCallback frame=new Choreographer.FrameCallback() {
        @Override public void doFrame(long nanos) {
            if(!playing) return;
            if(lastFrame!=0 && !restoring) {
                float seconds=Math.min(0.1f,(nanos-lastFrame)/1000000000f);
                position+=getResources().getDisplayMetrics().density * 24f * (speed/5f) * seconds;
                scroll.scrollTo(0,Math.min(maxScroll(),Math.round(position)));
                if(position>=maxScroll()) {completed=true; pause(); play.setText("重新开始"); updateProgress(); return;}
            }
            lastFrame=nanos; Choreographer.getInstance().postFrameCallback(this);
        }
    };
    private void pause() {
        captureAnchor(); playing=false; counting=false; handler.removeCallbacks(countdownTick);
        Choreographer.getInstance().removeFrameCallback(frame);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(play!=null) {
            play.setText(current.anchor>0?"继续":"开始");
            restoring=true; header.setVisibility(View.VISIBLE); tools.setVisibility(View.VISIBLE);
            scroll.post(this::applyReadingLayout);
        }
        save();
    }

    private void clearDraft() {
        pause(); if(current.text.isEmpty()) return;
        undoText=current.text; undoId=current.id; current.text=""; current.anchor=0;
        save(); showCurrent(); Toast.makeText(this,"已清空，可点击撤销清空恢复",Toast.LENGTH_LONG).show();
    }
    private void undoClear() {
        pause(); if(undoText==null || !current.id.equals(undoId)) return;
        if(!current.text.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("恢复清空前的稿件？").setMessage("当前新内容会另存为一篇稿件。")
                .setNegativeButton("取消",null).setPositiveButton("恢复",(d,w)->{
                    drafts.add(new Draft(UUID.randomUUID().toString(),current.title+"（恢复前）",current.text,current.anchor)); restoreCleared();
                }).show();
        } else restoreCleared();
    }
    private void restoreCleared() { current.text=undoText; current.anchor=0; undoText=null; undoId=null; save(); showCurrent(); }

    private void showLibrary() {
        pause(); String[] names=new String[drafts.size()];
        for(int i=0;i<names.length;i++) names[i]=drafts.get(i).title+(drafts.get(i)==current?"  ✓":"");
        new AlertDialog.Builder(this).setTitle("我的稿件").setItems(names,(dialog,index)->{
            current=drafts.get(index); save(); showCurrent(); play.setText(current.anchor>0?"继续":"开始");
        }).setPositiveButton("新建稿件",(dialog,which)->{
            current=new Draft(UUID.randomUUID().toString(),"未命名稿件","",0); drafts.add(current); save(); showCurrent(); edit(current);
        }).setNegativeButton("关闭",null).show();
    }
    private final Runnable autosave=()->save();
    private void edit(Draft draft) {
        pause(); editingDraft=draft;
        LinearLayout content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(20),dp(8),dp(20),dp(8));
        activeTitle=new EditText(this); activeTitle.setSingleLine(true); activeTitle.setHint("稿件名称"); activeTitle.setText(draft.title); content.addView(activeTitle);
        activeEditor=new EditText(this); activeEditor.setGravity(Gravity.TOP); activeEditor.setHint("输入或粘贴台词，修改会自动保存"); activeEditor.setText(draft.text);
        activeEditor.setTextSize(18); content.addView(activeEditor,new LinearLayout.LayoutParams(-1,Math.min(dp(220),getResources().getDisplayMetrics().heightPixels/3)));
        TextWatcher watcher=new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){handler.removeCallbacks(autosave); handler.postDelayed(autosave,400);}
            public void afterTextChanged(Editable e){}
        };
        activeTitle.addTextChangedListener(watcher); activeEditor.addTextChangedListener(watcher);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("编辑稿件 · 自动保存").setView(content).setPositiveButton("完成",null).create();
        dialog.setOnDismissListener(d->{save(); activeEditor=null; activeTitle=null; editingDraft=null; showCurrent();});
        dialog.show(); dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private void showSettings() {
        pause(); LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20),dp(12),dp(20),dp(12));
        addSlider(box,"字号",fontSize,16,64,value->{captureAnchor(); fontSize=value; reader.setTextSize(fontSize); applyReadingLayout(); save();});
        addSlider(box,"左右留白",margin,24,160,value->{captureAnchor(); margin=value; applyReadingLayout(); save();});
        Switch mirror=new Switch(this); mirror.setText("镜像文字"); mirror.setTextColor(TEXT); mirror.setChecked(mirrored); box.addView(mirror,new LinearLayout.LayoutParams(-1,dp(52)));
        mirror.setOnCheckedChangeListener((v,checked)->{mirrored=checked; reader.setScaleX(checked?-1:1); updateProgress(); save();});
        Switch reference=new Switch(this); reference.setText("阅读参考线"); reference.setTextColor(TEXT); reference.setChecked(showGuide); box.addView(reference,new LinearLayout.LayoutParams(-1,dp(52)));
        reference.setOnCheckedChangeListener((v,checked)->{showGuide=checked; guide.setVisibility(checked?View.VISIBLE:View.GONE); save();});
        Switch delay=new Switch(this); delay.setText("开始前倒计时 3 秒"); delay.setTextColor(TEXT); delay.setChecked(countdown>0); box.addView(delay,new LinearLayout.LayoutParams(-1,dp(52)));
        delay.setOnCheckedChangeListener((v,checked)->{countdown=checked?3:0; save();});
        box.addView(label("留白会自动适应屏幕宽度，保证文字可读。",12));
        ScrollView container=new ScrollView(this); container.addView(box);
        new AlertDialog.Builder(this).setTitle("显示与播放").setView(container).setPositiveButton("完成",null).show();
    }
    private interface ValueChange {void apply(int value);}
    private void addSlider(LinearLayout box,String name,int value,int min,int max,ValueChange change) {
        TextView caption=label(name+"  "+value,16); box.addView(caption);
        SeekBar bar=new SeekBar(this); bar.setMax(max-min); bar.setProgress(Math.max(0,Math.min(max-min,value-min))); bar.setContentDescription(name);
        box.addView(bar,new LinearLayout.LayoutParams(-1,dp(48)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int p,boolean user){if(user){caption.setText(name+"  "+(p+min)); change.apply(p+min);}}
            public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){}
        });
    }
    private void save() {
        if(prefs==null || current==null) return;
        if(activeEditor!=null && editingDraft!=null) {
            String text=activeEditor.getText().toString();
            if(!text.equals(editingDraft.text)) editingDraft.anchor=0;
            editingDraft.text=text;
            String name=activeTitle.getText().toString().trim(); editingDraft.title=name.isEmpty()?"未命名稿件":name;
        }
        JSONArray list=new JSONArray();
        try { for(Draft d:drafts) {JSONObject o=new JSONObject(); o.put("id",d.id); o.put("title",d.title); o.put("text",d.text); o.put("anchor",d.anchor); list.put(o);} }
        catch(Exception e) {return;}
        prefs.edit().putString("drafts",list.toString()).putString("selected",current.id)
            .putString("script",current.text).putInt("fontSize",fontSize).putInt("speed",speed)
            .putInt("pageMargin",margin).putBoolean("mirrored",mirrored).putBoolean("guide",showGuide)
            .putInt("countdown",countdown).putString("undoText",undoText).putString("undoId",undoId).apply();
    }
    private LinearLayout row(){LinearLayout r=new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); return r;}
    private void rotateScreen(){pause(); captureAnchor(); save(); setRequestedOrientation(landscape?ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;}
    private void styleButton(Button b,int color,int radius){b.setBackgroundTintList(null); b.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(30,255,255,255)),shape(color,radius),shape(Color.WHITE,radius))); b.setStateListAnimator(null); b.setElevation(0);}
    private Button compactAction(LinearLayout row,String text,Runnable action,int color,int width){Button b=addButton(row,text,action); b.setTextColor(color); b.setLayoutParams(new LinearLayout.LayoutParams(width,dp(48))); return b;}
    private void addFade(FrameLayout stage,int gravity){View fade=new View(this); fade.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); fade.setBackground(new GradientDrawable(gravity==Gravity.TOP?GradientDrawable.Orientation.TOP_BOTTOM:GradientDrawable.Orientation.BOTTOM_TOP,new int[]{BG,Color.TRANSPARENT})); stage.addView(fade,new FrameLayout.LayoutParams(-1,dp(32),gravity));}
    private TextView label(String text,int size){TextView v=new TextView(this); v.setText(text); v.setTextColor(TEXT); v.setTextSize(size); return v;}
    private Button addButton(LinearLayout row,String text,Runnable action){
        Button b=new Button(this); b.setText(text); b.setTextSize(13); b.setAllCaps(false); b.setTextColor(TEXT); b.setPadding(dp(2),0,dp(2),0);
        b.setMinHeight(0); b.setMinimumHeight(0); b.setMinWidth(0); b.setMinimumWidth(0); b.setTextColor(MUTED); styleButton(b,Color.TRANSPARENT,12);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1); p.setMargins(dp(2),0,dp(2),0); row.addView(b,p); b.setOnClickListener(v->action.run()); return b;
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    @Override protected void onPause(){pause(); save(); super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null); Choreographer.getInstance().removeFrameCallback(frame); super.onDestroy();}
}
