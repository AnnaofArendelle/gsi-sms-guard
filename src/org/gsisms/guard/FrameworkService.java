package org.gsisms.guard;
import android.app.*;
import android.content.*;
import android.os.*;
import org.json.JSONObject;
import java.util.concurrent.*;

/** Foreground user framework. Reasserts readiness when RIL processes change.
 * No root daemon, no system partition writes, no diagnostic log persistence.
 */
public final class FrameworkService extends Service {
    static final String CHANNEL="gsi_sms_framework";
    static final int NOTIFY=241;
    private ScheduledExecutorService worker;
    private RootClient root;
    private String lastIdentity="";
    private long lastSent=0;
    private boolean destroyed;
    @Override public void onCreate(){super.onCreate();android.util.Log.i("GsiSmsFramework","service created");
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel(CHANNEL,"GSI 短信修复框架",NotificationManager.IMPORTANCE_LOW));
        startForeground(NOTIFY,notification("正在检查 GSI 和高通无线服务"));
        root=new RootClient(getApplicationInfo().nativeLibraryDir);
        worker=Executors.newSingleThreadScheduledExecutor();
        worker.scheduleWithFixedDelay(()->tick(),0,30,TimeUnit.SECONDS);
    }
    Notification notification(String state){
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent pending=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_notify_sync_noanim).setContentTitle("GSI 短信修复框架").setContentText(state).setContentIntent(pending).setOngoing(true).setOnlyAlertOnce(true).build();
    }
    JSONObject call(String action)throws Exception{
        String c="CLASSPATH="+RootClient.quote(getApplicationInfo().sourceDir)+" /system/bin/app_process / org.gsisms.guard.Engine "+action;
        String output=root.run(c);
        for(String line:output.split("\n"))if(line.startsWith("GSISMS_RESULT=")){
            JSONObject r=new JSONObject(line.substring(14));if(!r.optBoolean("ok"))throw new Exception(r.optString("message"));return r;
        }throw new Exception("工作进程未返回结果");
    }
    JSONObject poll()throws Exception{
        // Shell-only polling avoids starting an ART VM every 30 seconds.
        String query="getprop ro.gsid.image_running; getprop ro.product.vendor.device; "
          +"if [ -e /system/bin/qcom-atel-ready ]; then echo builtin; else echo none; fi; "
          +"readlink /proc/self/ns/mnt; readlink /proc/1/ns/mnt; "
          +"awk '$5==\"/data/vendor/radio\" {found=1} END {print found+0}' /proc/1/mountinfo; "
          +"for pid in $(pidof qcrilNrd); do awk '{print $1 \":\" $22}' /proc/$pid/stat; done";
        PollState parsed=PollState.parse(root.run(query));
        JSONObject r=new JSONObject();r.put("gsi",parsed.gsi);r.put("eligible",parsed.eligible);
        r.put("message","设备、内置修复或挂载空间条件不符合");r.put("mounted",parsed.mounted);
        r.put("identity",parsed.identity);return r;
    }
    void state(String text){android.util.Log.i("GsiSmsFramework",text);
        getSharedPreferences("framework",0).edit().putString("lastState",text).putLong("updated",System.currentTimeMillis()).apply();
        if(!destroyed)((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFY,notification(text));
    }
    void tick(){
        if(destroyed)return;
        if(!getSharedPreferences("framework",0).getBoolean("enabled",false)){stopSelf();return;}
        try{
            JSONObject r=poll();
            if(!r.optBoolean("gsi")){state("当前为主系统，框架不执行无线操作");stopSelf();return;}
            if(!r.optBoolean("eligible")){state(r.optString("message","设备不符合条件"));return;}
            if(r.optBoolean("mounted")){state("临时挂载生效，暂停 ATEL 自动上报");lastIdentity="";return;}
            String id=r.optString("identity");
            if(id.isEmpty()){state("等待高通无线服务启动");return;}
            if(!id.equals(lastIdentity)||SystemClock.elapsedRealtime()-lastSent>=300000){
                state("检测到无线服务启动，正在上报 ATEL 就绪");
                JSONObject ready=call("ready");
                if(ready.optInt("sentSlots")!=ready.optInt("expectedSlots")){state("部分槽位尚未就绪，稍后重试");return;}
                lastIdentity=id;lastSent=SystemClock.elapsedRealtime();state("已上报 ATEL 就绪；自动跟随无线服务重启");
            }
        }catch(Exception ex){
            // Preserve enabled preference for a future user-started retry, but do not spam root prompts.
            state("框架暂停："+RootClient.safeError(ex.getMessage()==null?"root 不可用":ex.getMessage()));
            stopSelf();
        }
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){startForeground(NOTIFY,notification("框架已启动，检查无线服务状态"));return START_STICKY;}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){android.util.Log.i("GsiSmsFramework","service destroyed");destroyed=true;if(worker!=null)worker.shutdown();super.onDestroy();}
}
