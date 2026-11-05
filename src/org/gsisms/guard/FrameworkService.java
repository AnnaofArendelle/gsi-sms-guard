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
    private long lastAttempt=0;
    private boolean destroyed;
    @Override public void onCreate(){super.onCreate();android.util.Log.i("GsiSmsFramework","service created");
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel(CHANNEL,"GSI 短信修复框架",NotificationManager.IMPORTANCE_LOW));
        startForeground(NOTIFY,notification("正在检查高通无线服务"));
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
        String query="if [ -e /system/bin/qcom-atel-ready ]; then echo builtin; else echo none; fi; "
          +"awk '$5==\"/data/vendor/radio\" {found=1} END {print found+0}' /proc/self/mountinfo; "
          +"for pid in $(pidof qcrilNrd qcrild qcrild2); do awk '{print $1 \":\" $22}' /proc/$pid/stat; done";
        PollState parsed=PollState.parse(root.run(query));
        JSONObject r=new JSONObject();r.put("builtin",parsed.builtin);r.put("mounted",parsed.mounted);
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
            boolean force=getSharedPreferences("framework",0).getBoolean("force",false);
            if(!force&&r.optBoolean("builtin")){state("系统已有 ATEL 修复；如需重复上报，请重新确认警告");return;}
            if(!force&&r.optBoolean("mounted")){state("radio 已有挂载，暂停 ATEL 自动上报");lastIdentity="";return;}
            String id=r.optString("identity");
            long now=SystemClock.elapsedRealtime();
            // Unknown process names still get a periodic interface probe; no vendor-name allowlist.
            boolean due=lastSent==0||(!id.isEmpty()&&!id.equals(lastIdentity))||now-lastSent>=300000;
            if(due&&(lastAttempt==0||now-lastAttempt>=30000)){
                lastAttempt=now;state("正在检查接口并上报 ATEL 就绪");
                JSONObject ready;
                try{ready=call(force?"ready-force":"ready");}
                catch(Exception ex){state("本次上报未完成，稍后重试："+RootClient.safeError(ex.getMessage()==null?"接口不可用":ex.getMessage()));return;}
                if(ready.optInt("sentSlots")<ready.optInt("expectedSlots")){state("部分已检测槽位尚未发送成功，稍后重试");return;}
                lastIdentity=id;lastSent=SystemClock.elapsedRealtime();
                state("已上报 ATEL 就绪；"+(id.isEmpty()?"未识别 RIL 进程，每 5 分钟补发":"自动跟随无线服务重启")+(force?"（已确认警告）":""));
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
