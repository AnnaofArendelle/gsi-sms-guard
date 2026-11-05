package org.gsisms.guard;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private RootClient root;
    private final ArrayList<Button> buttons=new ArrayList<>();
    private TextView status,details,evidence,note,frameworkState;
    private Button frameworkButton;
    private Button ready,mount,undo;
    private JSONObject current;
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(3),1);return t;}
    private GradientDrawable bg(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private LinearLayout card(LinearLayout parent){LinearLayout c=new LinearLayout(this);c.setOrientation(1);c.setPadding(dp(20),dp(18),dp(20),dp(18));c.setBackground(bg(Color.WHITE,18));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(16);parent.addView(c,p);return c;}
    private Button button(LinearLayout parent,String label,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(15);b.setTextColor(Color.rgb(25,65,90));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(8);parent.addView(b,p);b.setOnClickListener(v->action.run());buttons.add(b);return b;}
    @Override public void onCreate(Bundle state){super.onCreate(state);root=new RootClient(getApplicationInfo().nativeLibraryDir);
        getWindow().setStatusBarColor(Color.rgb(243,246,250));getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.rgb(243,246,250));
        LinearLayout body=new LinearLayout(this);body.setOrientation(1);body.setPadding(dp(22),dp(28),dp(22),dp(28));scroll.addView(body);setContentView(scroll);
        TextView heading=text("GSI 短信修复框架",26,Color.rgb(20,35,50));heading.setTypeface(null,Typeface.BOLD);body.addView(heading);
        TextView sub=text("高通 ATEL / Power Opt · 自动与手动管理",14,Color.rgb(85,100,116));sub.setPadding(0,dp(8),0,dp(22));body.addView(sub);
        LinearLayout first=card(body);first.addView(text("设备与当前状态",16,Color.rgb(25,65,90)));
        status=text("等待检测",22,Color.rgb(20,35,50));status.setPadding(0,dp(12),0,dp(12));first.addView(status);
        details=text("需要 root 授权。检测不会改写无线配置。",14,Color.rgb(85,100,116));first.addView(details);
        button(first,"检测 / 刷新状态",()->run("detect"));
        LinearLayout framework=card(body);framework.addView(text("自动修复框架",16,Color.rgb(25,65,90)));
        frameworkState=text("尚未启用",14,Color.rgb(85,100,116));frameworkState.setPadding(0,dp(12),0,dp(8));framework.addView(frameworkState);
        frameworkButton=button(framework,"启用自动框架",()->toggleFramework());
        button(framework,"打开后台 / 电池设置",()->{
            try{startActivity(new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName())));}
            catch(RuntimeException ignored){}
        });
        TextView autoInfo=text("开机后上报 ATEL UI 就绪；每 30 秒检查无线服务是否重启，重启后自动重发；每 5 分钟补发一次。以低优先级通知显示运行状态，不修改系统分区。",13,Color.rgb(85,100,116));autoInfo.setPadding(0,dp(8),0,0);framework.addView(autoInfo);refreshFramework();
        LinearLayout second=card(body);second.addView(text("故障判断",16,Color.rgb(25,65,90)));
        evidence=text("接口和配置只能判断是否符合条件。短信进入 Power Opt 缓冲的日志，才能提供直接证据；没有日志不代表没有故障。",14,Color.rgb(85,100,116));evidence.setPadding(0,dp(12),0,0);second.addView(evidence);
        LinearLayout actions=card(body);actions.addView(text("本次启动的修复",16,Color.rgb(25,65,90)));
        ready=button(actions,"上报 ATEL UI 就绪",()->confirmRepair(false));
        mount=button(actions,"临时关闭 Power Opt · 挂载副本",()->confirmRepair(true));
        undo=button(actions,"撤销临时挂载",()->confirm("撤销临时挂载","恢复读取原始配置，无线服务会短暂重启。请等待网络恢复。", "undo"));
        note=text("按实际接口与配置检测，不限制机型或系统。条件不符可在警告后仍然尝试；接口/root 缺失时会报告失败。",13,Color.rgb(85,100,116));note.setPadding(0,dp(14),0,0);actions.addView(note);
        LinearLayout about=card(body);about.addView(text("隐私与恢复",16,Color.rgb(25,65,90)));
        TextView privacy=text("不读取短信正文、验证码和卡号，不联网。不会刷写基带、修改 NV/MBN 或关闭 SELinux。自动框架由你手动启用；关闭后不再自动上报。\n\n卸载应用不会自动撤销已有挂载，请先撤销或重启手机。",14,Color.rgb(85,100,116));privacy.setPadding(0,dp(12),0,0);about.addView(privacy);
        button(about,"开源说明 / 设计限制",()->new AlertDialog.Builder(this).setTitle("开源与限制").setMessage("版本 0.4.0，源码随 APK 提供，GPL-3.0（包含 PHH Superuser 客户端）。\n\nATEL 协议依据 yarosz/lp3-treble 和 TrebleDroid/device_phh_treble PR #88。\n\nroot 本身可能被应用检测。已在当前小米 12 GSI 确认 ATEL 方案收到验证码；其他设备须独立核对。\n\n没有日志权限或无线诊断日志未开启时，只能判断兼容条件。此版本不会自动开启 vendor 调试日志。\n\n内置未修改的 PHH su 客户端，保留 Superuser 自身授权流程；随源码提供原客户端及对应开源项目。").setPositiveButton("知道了",null).show());
        undo.setEnabled(false);
    }
    private void confirmRepair(boolean mounting){
        boolean recommended=current!=null&&current.optBoolean(mounting?"mountSupported":"supported");
        String warning=current==null?"尚未完成检测，无法确认接口和配置条件。":current.optString(mounting?"mountWarnings":"readyWarnings","检测未完成。");
        String message=mounting?"临时修改配置副本并挂载，可能短暂中断通话与数据。原始目录不直接改写；主系统也允许尝试，但 radio 数据可能共享。需兼容数据库、主挂载空间与可控制 RIL；这些缺失时只能报告失败。重启清除挂载。":"仅尝试固定 ATEL 就绪请求，不修改数据库或省电配置。已检测接口也不能保证短信修复；请以收信确认。";
        if(!recommended)message="检测条件不匹配：\n"+warning+"\n\n"+message;
        final String action=(mounting?"mount":"ready")+(recommended?"":"-force");
        new AlertDialog.Builder(this).setTitle(recommended?(mounting?"启用临时挂载":"上报 ATEL 就绪"):"兼容性警告")
          .setMessage(message).setNegativeButton("取消",null)
          .setPositiveButton(recommended?"执行":"仍然尝试修复",(d,w)->run(action)).show();
    }
    private void confirm(String title,String message,String action){new AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("取消",null).setPositiveButton("执行",(d,w)->run(action)).show();}
    private void run(String action){
        for(Button b:buttons)b.setEnabled(false);status.setText("正在"+(action.equals("detect")?"检测…":"处理…"));
        worker.execute(()->{
            try{
                String command="CLASSPATH="+RootClient.quote(getApplicationInfo().sourceDir)+" /system/bin/app_process / org.gsisms.guard.Engine "+action;
                String out=root.run(command);String json=null;for(String line:out.split("\n"))if(line.startsWith("GSISMS_RESULT="))json=line.substring(14);
                if(json==null)throw new Exception("Root worker 未返回结果，请检查 root 授权及 SELinux。" );
                JSONObject result=new JSONObject(json);runOnUiThread(()->display(result));
            }catch(Exception ex){runOnUiThread(()->{
                status.setText("操作未完成");details.setText(RootClient.safeError(ex.getMessage()==null?"Root 不可用":ex.getMessage()));
                for(Button b:buttons)b.setEnabled(true);undo.setEnabled(false);current=null;
            });}
        });
    }
    private void display(JSONObject r){refreshFramework();
        current=r;for(Button b:buttons)b.setEnabled(true);
        if(!r.optBoolean("ok")){status.setText("操作未完成");details.setText(r.optString("message"));ready.setEnabled(true);mount.setEnabled(true);undo.setEnabled(r.optBoolean("canUndo"));return;}
        boolean mounted=r.optBoolean("mounted"),supported=r.optBoolean("supported"),own=r.optBoolean("owned");
        status.setText(mounted?(own?"临时挂载已启用":"检测到其他挂载"):(supported?"符合兼容条件":"条件未确认，可警告后尝试"));
        String integrity=r.optString("integrity");
        details.setText("设备："+r.optString("device")+" · Android SDK "+r.optString("sdk")+"\nDSU/GSI："+(r.optBoolean("gsi")?"正在运行":"否")+"\n高通 OEM hook："+(r.optBoolean("hook")?"存在":"未找到")+"\nPower Opt 配置："+r.optString("powerOpt")+"\n无线服务："+r.optString("ril0")+" / "+r.optString("ril1")+"\n原文件校验："+(integrity.equals("unchanged")?"一致":integrity.equals("changed")?"发生变化":"暂无记录")+"\nSELinux："+r.optString("selinux"));
        evidence.setText(r.optString("verdict")+"\n\n缓冲事件："+(r.optBoolean("bufferEvidence")?"观察到":"未观察到")+"\n接收回调："+(r.optBoolean("incomingEvidence")?"观察到":"未观察到")+"\n\n历史日志不能保证是刚刚的短信；最终以受控收信对照为准。" );
        ready.setEnabled(true);mount.setEnabled(true);undo.setEnabled(r.optBoolean("canUndo")&&mounted&&own);
        if(!r.optString("message").isEmpty())new AlertDialog.Builder(this).setTitle("结果").setMessage(r.optString("message")).setPositiveButton("知道了",null).show();
    }
    private void refreshFramework(){
        boolean enabled=getSharedPreferences("framework",0).getBoolean("enabled",false);
        frameworkButton.setText(enabled?"关闭自动框架":"启用自动框架");
        frameworkState.setText((enabled?(getSharedPreferences("framework",0).getBoolean("force",false)?"自动框架：已启用（已确认警告）":"自动框架：已启用"):"自动框架：未启用")+"\n"+getSharedPreferences("framework",0).getString("lastState","开启后保留通知，建议授予永久 root 权限。"));
    }
    private void toggleFramework(){
        boolean enabled=getSharedPreferences("framework",0).getBoolean("enabled",false);
        if(enabled){getSharedPreferences("framework",0).edit().putBoolean("enabled",false).apply();stopService(new android.content.Intent(this,FrameworkService.class));refreshFramework();return;}
        boolean recommended=current!=null&&current.optBoolean("supported");
        String warning=current==null?"尚未完成检测。":current.optString("readyWarnings");
        String message="自动上报 ATEL 就绪，跟随已识别 RIL 重启；其他服务每 5 分钟补发。需要持久 root 与后台权限。不会自动挂载或重启 RIL。关闭后停止上报。";
        if(!recommended)message="检测条件不匹配：\n"+warning+"\n\n仍可启用尝试模式，条件未就绪时会报告失败并重试。\n"+message;
        new AlertDialog.Builder(this).setTitle(recommended?"启用自动框架":"自动框架兼容性警告").setMessage(message)
        .setNegativeButton("取消",null).setPositiveButton(recommended?"启用":"仍然启用尝试",(d,w)->{
            if(android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},41);
            getSharedPreferences("framework",0).edit().putBoolean("enabled",true).putBoolean("force",!recommended).apply();
            startForegroundService(new android.content.Intent(this,FrameworkService.class));refreshFramework();
        }).show();
    }
    @Override protected void onResume(){super.onResume();if(frameworkState!=null)refreshFramework();}
    @Override protected void onDestroy(){super.onDestroy();worker.shutdown();}
}
