package org.gsisms.guard;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import org.json.JSONObject;

/** Root-side worker, reached through app_process. No arbitrary actions from UI/external intents. */
public final class Engine {
    static final String RADIO="/data/vendor/radio", CLONE="/data/local/tmp/radio-poweropt-test";
    static final String STATE="/data/local/tmp/radio-poweropt-state.json", KEY="persist.vendor.radio.poweron_opt";
    static final String HOOK="vendor.qti.hardware.radio.qcrilhook@1.0::IQtiOemHook";
    static final String[] SERVICE_NAMES={"vendor.qcrild","vendor.qcrild2","vendor.qcrilNrd","vendor.qcrilNrd2","qcrild","qcrild2"};
    static String safeShell(String command){try{return shell(command);}catch(Exception ex){return "";}}
    static String safeProp(String name){return safeShell("getprop "+RootClient.quote(name));}
    static List<String> radioServices(){
        List<String> result=new ArrayList<>();
        for(String name:SERVICE_NAMES){String state=safeProp("init.svc."+name);if(!state.isEmpty())result.add(name);}
        return result;
    }
    static boolean masterNamespace(){String self=safeShell("readlink /proc/self/ns/mnt"),init=safeShell("readlink /proc/1/ns/mnt");return !self.isEmpty()&&self.equals(init);}
    static boolean databaseCompatible(){try{return !powerConfig().equals("missing");}catch(Exception ex){return false;}}
    static Compatibility policy(JSONObject r){return new Compatibility(r.optBoolean("hook"),r.optBoolean("builtin"),r.optBoolean("mounted"),r.optBoolean("masterNamespace"),r.optBoolean("databaseCompatible"),r.optString("powerOpt").equals("1"),r.optBoolean("bufferEvidence"),r.optBoolean("servicesCompatible"));}
    static String shell(String command) throws Exception {
        java.lang.Process p=new ProcessBuilder("/system/bin/sh","-c",command).redirectErrorStream(true).start();
        p.getOutputStream().close();
        String output=new String(RootClient.read(p.getInputStream()),java.nio.charset.StandardCharsets.UTF_8);
        if(p.waitFor()!=0) throw new IOException("系统操作失败："+RootClient.safeError(output));
        return output.trim();
    }
    static String prop(String name)throws Exception{return shell("getprop "+RootClient.quote(name));}
    static String mounts()throws Exception{return shell("cat /proc/1/mountinfo");}
    static String mountLine()throws Exception{
        for(String line:mounts().split("\n"))if(line.contains(" /data/vendor/radio "))return line;
        return "";
    }
    static boolean owned()throws Exception{return mountLine().contains(" /local/tmp/radio-poweropt-test /data/vendor/radio ");}
    static String hash(File file)throws Exception{
        MessageDigest md=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)md.update(b,0,n);}
        StringBuilder s=new StringBuilder();for(byte b:md.digest())s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();
    }
    static String originalHash()throws Exception{
        if(mountLine().isEmpty())return hash(new File(RADIO+"/qcrilNr.db"));
        String view="/data/local/tmp/gsisms-original-view";
        shell("mkdir -p "+view);shell("mount --bind /data/vendor "+view);
        try{return hash(new File(view+"/radio/qcrilNr.db"));}
        finally{shell("umount "+view);shell("rmdir "+view);}
    }
    static String powerConfig()throws Exception{
        SQLiteDatabase db=SQLiteDatabase.openDatabase(RADIO+"/qcrilNr.db",null,SQLiteDatabase.OPEN_READONLY);
        try(Cursor c=db.rawQuery("SELECT value,def_val FROM qcril_properties_table WHERE property=?",new String[]{KEY})){
            if(!c.moveToFirst())return "missing";return c.isNull(0)?(c.isNull(1)?"unset":c.getString(1)):c.getString(0);
        }finally{db.close();}
    }
    static JSONObject metadata()throws Exception{
        return new JSONObject(new String(java.nio.file.Files.readAllBytes(new File(STATE).toPath()),java.nio.charset.StandardCharsets.UTF_8));
    }
    static void save(JSONObject object)throws Exception{
        try(FileOutputStream out=new FileOutputStream(STATE)){out.write(object.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));out.getFD().sync();}
        shell("chmod 600 "+STATE);
    }
    static List<String> stop()throws Exception{
        List<String> running=new ArrayList<>();
        for(String name:radioServices())if(safeProp("init.svc."+name).equals("running"))running.add(name);
        if(running.isEmpty())throw new IOException("未识别到运行中的可控制 RIL 服务，未挂载");
        try{
            for(String name:running)shell("setprop ctl.stop "+RootClient.quote(name));
            for(int i=0;i<40;i++){
                boolean done=true;for(String name:running)if(!safeProp("init.svc."+name).equals("stopped"))done=false;
                if(done&&safeShell("pidof qcrilNrd qcrild qcrild2").isEmpty())return running;
                Thread.sleep(250);
            }
            throw new IOException("无线服务没有完全停止，取消挂载");
        }catch(Exception ex){start(running);throw ex;}
    }
    static void start(List<String> services)throws Exception{
        IOException failure=null;for(String name:services)try{shell("setprop ctl.start "+RootClient.quote(name));}catch(Exception ex){failure=new IOException("恢复无线服务失败："+name,ex);}
        if(failure!=null)throw failure;
    }
    static void requireMountPrerequisites()throws Exception{
        if(!masterNamespace())throw new IOException("挂载需要主挂载空间；未执行目录修改。ATEL 上报不要求主挂载空间。");
        if(!databaseCompatible())throw new IOException("缺少兼容 qcrilNr.db/Power Opt 属性行，无法完成挂载；未修改配置。");
        if(radioServices().isEmpty())throw new IOException("没有可控制的已识别 RIL 服务，无法安全复制配置。");
    }
    static JSONObject diagnostic()throws Exception{
        JSONObject r=new JSONObject();
        r.put("gsi",safeProp("ro.gsid.image_running").equals("1"));String device=safeProp("ro.product.vendor.device");
        if(device.isEmpty())device=safeProp("ro.product.device");r.put("device",device);r.put("sdk",safeProp("ro.build.version.sdk"));
        boolean hook0=AtelSignal.available("oemhook0"),hook1=AtelSignal.available("oemhook1");
        r.put("hook0",hook0);r.put("hook1",hook1);r.put("hook",hook0||hook1);r.put("availableSlots",(hook0?1:0)+(hook1?1:0));
        r.put("builtin",new File("/system/bin/qcom-atel-ready").exists());
        boolean mount=false,own=false;try{mount=!mountLine().isEmpty();own=owned();}catch(Exception ignored){}
        r.put("mounted",mount);r.put("owned",own);r.put("canUndo",own&&masterNamespace());
        r.put("ril0",safeProp("init.svc.vendor.qcrild"));r.put("ril1",safeProp("init.svc.vendor.qcrild2"));
        r.put("servicesCompatible",!radioServices().isEmpty());r.put("selinux",safeShell("getenforce"));r.put("masterNamespace",masterNamespace());
        String config="unknown";try{config=powerConfig();}catch(Exception ignored){}r.put("powerOpt",config);r.put("databaseCompatible",databaseCompatible());
        r.put("integrity","not-recorded");if(mount&&own&&new File(STATE).isFile())try{r.put("integrity",originalHash().equals(metadata().getString("original_db_hash"))?"unchanged":"changed");}catch(Exception ignored){r.put("integrity","unknown");}
        // Only boolean event indicators leave the worker; no raw logs or SMS contents.
        String log=safeShell("logcat -b radio -d -t 1800 -v brief");
        boolean held=java.util.regex.Pattern.compile("MT SMS ACK NEEDED Power Opt buffer length [1-9][0-9]*").matcher(log).find(),clear=log.contains("Reset MT SMS ACK NEEDED Power Opt buffer"),incoming=log.contains("onIncomingSms")||log.contains("SMS received");
        r.put("bufferEvidence",held);r.put("timeoutEvidence",held&&clear);r.put("incomingEvidence",incoming);
        r.put("imsRegisteredEvidence",log.contains("onRegistered")||log.contains("IMS registered"));
        Compatibility c=policy(r);r.put("supported",c.readyRecommended());r.put("mountSupported",c.mountRecommended());
        r.put("readyWarnings",c.readyWarnings());r.put("mountWarnings",c.mountWarnings());
        r.put("verdict",held?"观察到 Power Opt 缓冲事件（历史日志；需收信对照确认）":(config.equals("1")?"符合 Power Opt 故障条件，但未捕获短信被截留证据":"未确认本故障；可以在警告后尝试 ATEL"));
        return r;
    }
    static JSONObject mount(boolean force)throws Exception{
        JSONObject initial=diagnostic();
        if(!policy(initial).allowMount(force))throw new IOException(initial.optString("mountWarnings")+"\n请在界面确认警告后尝试。");
        requireMountPrerequisites();
        if(!mountLine().isEmpty()){if(!owned())throw new IOException("检测到其他挂载，拒绝覆盖");return diagnostic();}
        if(!force&&!powerConfig().equals("1"))throw new IOException("原配置不是启用状态，请确认警告后尝试");
        boolean mounted=false;List<String> stopped=new ArrayList<>();
        try{
            stopped=stop();shell("rm -rf "+CLONE+" && cp -a "+RADIO+" "+CLONE);
            String before=hash(new File(CLONE+"/qcrilNr.db"));
            SQLiteDatabase db=SQLiteDatabase.openDatabase(CLONE+"/qcrilNr.db",null,SQLiteDatabase.OPEN_READWRITE);
            try{
                db.beginTransaction();
                try{db.execSQL("UPDATE qcril_properties_table SET value=? WHERE property=?",new Object[]{"0",KEY});db.setTransactionSuccessful();}
                finally{db.endTransaction();}
                try(Cursor c=db.rawQuery("PRAGMA integrity_check",null)){if(!c.moveToFirst()||!c.getString(0).equals("ok"))throw new IOException("副本数据库校验失败");}
            }finally{db.close();}
            // cp -a preserves this device's ownership/modes/labels. SQLite may create sidecar files.
            String attributes=shell("stat -c '%u:%g %a' "+RADIO+"/qcrilNr.db");
            if(!attributes.matches("[0-9]+:[0-9]+ [0-7]{3,4}"))throw new IOException("无法读取原配置权限");
            String[] parts=attributes.split(" ");shell("chown "+parts[0]+" "+CLONE+"/qcrilNr.db");shell("chmod "+parts[1]+" "+CLONE+"/qcrilNr.db");
            if(safeShell("getenforce").equals("Enforcing")){
                String context=shell("ls -Zd "+RADIO).split(" +")[0];
                if(!context.matches("[A-Za-z0-9_:,.-]+"))throw new IOException("无法确认 radio SELinux 标签");
                shell("chcon -R "+RootClient.quote(context)+" "+CLONE);
            }
            save(new JSONObject().put("original_db_hash",before).put("original_poweron_opt",prop(KEY)));
            shell("mount --bind "+CLONE+" "+RADIO);mounted=true;
            if(!owned()||!originalHash().equals(before))throw new IOException("挂载或原文件校验失败");
        }catch(Exception ex){if(mounted)shell("umount "+RADIO);throw ex;}
        finally{if(!stopped.isEmpty())start(stopped);}
        JSONObject r=diagnostic();r.put("message","临时挂载已启用。等待约 30 秒恢复 4G/IMS，再测试短信。");return r;
    }
    static JSONObject undo()throws Exception{
        if(!masterNamespace())throw new IOException("撤销挂载需要主挂载空间");
        if(mountLine().isEmpty()){JSONObject r=diagnostic();r.put("message","当前没有临时挂载。");return r;}
        if(!owned())throw new IOException("拒绝撤销其他应用的挂载");
        String before=metadata().getString("original_db_hash");
        List<String> stopped=stop();
        try{shell("umount "+RADIO);if(!mountLine().isEmpty())throw new IOException("卸载未完成");}
        finally{start(stopped);}
        boolean unchanged=originalHash().equals(before);
        shell("rm -rf "+CLONE+"; rm -f "+STATE);
        JSONObject r=diagnostic();r.put("integrity",unchanged?"unchanged":"changed");r.put("message","已撤销挂载并恢复原配置。等待约 30 秒恢复网络。");return r;
    }
    static JSONObject ready(boolean force)throws Exception{
        JSONObject initial=diagnostic();
        if(!policy(initial).allowReady(force))throw new IOException(initial.optString("readyWarnings")+"\n可在界面警告后选择仍然尝试。");
        int sent=0,expected=initial.optInt("availableSlots");StringBuilder slots=new StringBuilder(),failures=new StringBuilder();
        for(String slot:new String[]{"oemhook0","oemhook1"}){
            if(!force&&!initial.optBoolean(slot.equals("oemhook0")?"hook0":"hook1"))continue;
            try{AtelSignal.send(slot);Thread.sleep(1000);AtelSignal.send(slot);sent++;slots.append(slot).append(' ');}
            catch(Throwable ex){failures.append(slot).append(": ").append(ex.getClass().getSimpleName()).append(' ');}
        }
        if(sent==0)throw new IOException("未能发送 ATEL 请求。已尝试固定接口；root、接口缺失或 SELinux 访问限制不能通过警告按钮创建。 "+failures);
        JSONObject r=diagnostic();r.put("sentSlots",sent);r.put("expectedSlots",expected>0?expected:sent);r.put("forced",force);
        r.put("message","已向 "+slots+"发送 ATEL UI ready（单向请求，不代表短信已修复）。请测试收信；配置未修改。"+(failures.length()>0?" 部分接口失败："+failures:""));return r;
    }
    public static void main(String[] args){
        JSONObject result=new JSONObject();
        try{
            if(args.length!=1)throw new IOException("Invalid action");
            try(java.io.RandomAccessFile mutex=new java.io.RandomAccessFile("/dev/gsisms-worker.lock","rw");java.nio.channels.FileChannel channel=mutex.getChannel();java.nio.channels.FileLock lock=channel.lock()){
            switch(args[0]){case "detect":result=diagnostic();break;case "mount":result=mount(false);break;case "mount-force":result=mount(true);break;case "undo":result=undo();break;case "ready":result=ready(false);break;case "ready-force":result=ready(true);break;default:throw new IOException("Unknown action");}
            result.put("ok",true);
            }
        }catch(Throwable ex){try{result.put("ok",false);result.put("message",RootClient.safeError(ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage()));}catch(Exception ignored){}}
        System.out.println("GSISMS_RESULT="+result);System.out.flush();System.exit(0);
    }
}
