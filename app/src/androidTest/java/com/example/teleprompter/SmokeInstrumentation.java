package com.example.teleprompter;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.*;
import java.lang.reflect.*;

/** Runs only in the disposable emulator; exercises actual UI and lifecycle. */
public class SmokeInstrumentation extends Instrumentation {
    private Activity activity;
    private int checks;
    private boolean preview;
    private java.util.Map<String,?> original;
    private void restoreOriginal() {
        if(activity!=null) {runOnMainSync(()->activity.finish()); settle();}
        android.content.SharedPreferences.Editor e=getTargetContext().getSharedPreferences("teleprompter",0).edit().clear();
        for(java.util.Map.Entry<String,?> item:original.entrySet()) {
            Object v=item.getValue(); String k=item.getKey();
            if(v instanceof String)e.putString(k,(String)v);
            else if(v instanceof Integer)e.putInt(k,(Integer)v);
            else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);
            else if(v instanceof Long)e.putLong(k,(Long)v);
            else if(v instanceof Float)e.putFloat(k,(Float)v);
        }
        e.commit();
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); preview=args!=null && "true".equals(args.getString("preview")); start(); }
    private Object field(Object o,String name) {
        try {Field f=o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o);} catch(Exception e){throw new RuntimeException(e);}
    }
    private void set(Object o,String name,Object value) {
        try {Field f=o.getClass().getDeclaredField(name); f.setAccessible(true); f.set(o,value);} catch(Exception e){throw new RuntimeException(e);}
    }
    private void invoke(String name) {
        try {Method m=activity.getClass().getDeclaredMethod(name); m.setAccessible(true); m.invoke(activity);} catch(Exception e){throw new RuntimeException(e);}
    }
    private void check(boolean b,String message) {if(!b)throw new AssertionError(message); checks++;}
    private void settle() {runOnMainSync(()->{}); SystemClock.sleep(250); runOnMainSync(()->{});}
    private void launch() {activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); settle();}
    private void screenshot(String name) throws Exception {
        android.graphics.Bitmap bitmap=getUiAutomation().takeScreenshot();
        java.io.File file=new java.io.File(getTargetContext().getExternalFilesDir("review"),name+".png");
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}
        bitmap.recycle();
    }
    private void rotate(int requested) {
        if(activity.getResources().getConfiguration().orientation==(requested==1?1:2))return;
        ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);
        runOnMainSync(()->activity.setRequestedOrientation(requested));
        Activity next=waitForMonitorWithTimeout(monitor,10000); removeMonitor(monitor);
        if(next==null)throw new AssertionError("Preview rotation failed"); activity=next; settle();
    }
    private void capturePreview() throws Exception {
        String text="把今天，讲给你听。\n\n有时候，好的表达，\n从一次深呼吸开始。\n\n不用急着说完所有的话，\n也不必追赶别人的节奏。\n\n把目光放在眼前，\n把想法慢慢说清楚。\n\n留一点停顿，\n让每一句话都有回声。\n\n此刻，只管自然表达。\n句中，陪你从容开口。";
        getTargetContext().getSharedPreferences("teleprompter",0).edit().clear().putString("script",text).putInt("fontSize",28).putInt("pageMargin",32).commit();
        launch(); rotate(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        runOnMainSync(()->{Object d=field(activity,"current"); set(d,"title","把今天，讲给你听"); set(d,"anchor",text.indexOf("不用急着")); invoke("showCurrent"); invoke("save");}); settle();
        screenshot("ui-portrait");
        rotate(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE); settle(); screenshot("ui-landscape");
    }
    @Override public void onStart() {
        Bundle result=new Bundle();
        original=new java.util.HashMap<>(getTargetContext().getSharedPreferences("teleprompter",0).getAll());
        try {
            if(preview) {capturePreview(); restoreOriginal(); result.putString("stream","PASS: portrait and landscape UI screenshots captured\n"); finish(Activity.RESULT_OK,result); return;}
            getTargetContext().getSharedPreferences("teleprompter",0).edit().clear().putString("script","旧版本稿件迁移验证").commit();
            launch();
            runOnMainSync(()->{
                check(((TextView)field(activity,"reader")).getText().toString().contains("旧版本"),"legacy migration");
                invoke("clearDraft"); check(((TextView)field(activity,"reader")).length()==0,"clear");
                invoke("undoClear"); check(((TextView)field(activity,"reader")).getText().toString().contains("旧版本"),"undo");
                Object d=field(activity,"current"); StringBuilder text=new StringBuilder();
                for(int i=0;i<100;i++) text.append("第 ").append(i).append(" 行：保持自然表达，稳定阅读台词。\n");
                set(d,"text",text.toString()); set(d,"anchor",0); set(activity,"countdown",0); invoke("showCurrent"); invoke("save");
            }); settle();
            runOnMainSync(()->invoke("togglePlay")); SystemClock.sleep(1800); runOnMainSync(()->{});
            runOnMainSync(()->{
                check((Boolean)field(activity,"playing"),"play starts");
                check(((ScrollView)field(activity,"scroll")).getScrollY()>0,"continuous scroll advances");
                check((activity.getWindow().getAttributes().flags & 128)!=0,"screen stays awake");
                callActivityOnPause(activity);
                check(!(Boolean)field(activity,"playing") && !(Boolean)field(activity,"counting"),"pause clears state");
                check((activity.getWindow().getAttributes().flags & 128)==0,"pause releases keep awake");
                callActivityOnResume(activity);
            }); settle();
            runOnMainSync(()->{
                set(activity,"countdown",3); invoke("togglePlay"); check((Boolean)field(activity,"counting"),"countdown starts"); invoke("pause");
                check(!(Boolean)field(activity,"counting"),"countdown cancels");
                set(activity,"countdown",0); set(activity,"fontSize",40); set(activity,"mirrored",true);
                Object d=field(activity,"current"); set(d,"anchor",400); invoke("showCurrent");
            }); settle();
            runOnMainSync(()->{
                check(((ScrollView)field(activity,"scroll")).getScrollY()>0,"anchor restores"); invoke("save"); activity.finish();
            }); settle(); launch();
            ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);
            runOnMainSync(()->activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
            Activity rotated=waitForMonitorWithTimeout(monitor,10000); removeMonitor(monitor);
            check(rotated!=null,"rotation recreates screen"); activity=rotated; settle();
            runOnMainSync(()->{
                check(activity.getResources().getConfiguration().orientation==2,"landscape layout");
                check(((ScrollView)field(activity,"scroll")).getHeight()>0,"landscape reading area");
                ScrollView s=(ScrollView)field(activity,"scroll"); int[] location=new int[2]; s.getLocationOnScreen(location);
                check(Math.abs(location[0]+s.getWidth()/2-activity.getResources().getDisplayMetrics().widthPixels/2)<4,"reading column centered on screen");
                int width=s.getWidth(); invoke("togglePlay");
                check(((android.view.View)field(activity,"header")).getVisibility()==android.view.View.VISIBLE,"left controls stay visible during play");
                check(((android.view.View)field(activity,"tools")).getVisibility()==android.view.View.VISIBLE,"right controls stay visible during play");
                check(s.getWidth()==width,"reading width stable during play"); invoke("pause");
            });
            runOnMainSync(()->{
                check((Integer)field(activity,"fontSize")==40,"font persists");
                check((Boolean)field(activity,"mirrored"),"mirror persists");
                check(((ScrollView)field(activity,"scroll")).getScrollY()>0,"position persists after recreation");
                invoke("clearDraft"); activity.finish();
            }); settle(); launch();
            runOnMainSync(()->{invoke("undoClear"); check(((TextView)field(activity,"reader")).length()>1000,"undo persists after recreation");}); settle();
            runOnMainSync(()->{
                ScrollView s=(ScrollView)field(activity,"scroll"); TextView r=(TextView)field(activity,"reader");
                s.scrollTo(0,r.getHeight()-s.getHeight()-5); set(activity,"speed",10); invoke("togglePlay");
            }); SystemClock.sleep(1600); runOnMainSync(()->{});
            runOnMainSync(()->{
                check(!(Boolean)field(activity,"playing"),"end stops automatically");
                check(((TextView)field(activity,"progress")).getText().toString().contains("已读完"),"end status");
            });
            restoreOriginal(); result.putString("stream","PASS: "+checks+" regression assertions\n"); finish(Activity.RESULT_OK,result);
        } catch(Throwable e) {restoreOriginal(); result.putString("stream","FAIL: "+e+"\n"+android.util.Log.getStackTraceString(e)); finish(Activity.RESULT_CANCELED,result);}
    }
}
