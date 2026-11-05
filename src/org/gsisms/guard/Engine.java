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
    static final String Q="'";
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
    static void stop()throws Exception{
        shell("setprop ctl.stop vendor.qcrild");shell("setprop ctl.stop vendor.qcrild2");
        for(int i=0;i<20;i++){
            if(prop("init.svc.vendor.qcrild").equals("stopped")&&prop("init.svc.vendor.qcrild2").equals("stopped"))return;
            Thread.sleep(250);
        }throw new IOException("无线服务没有停止，取消操作");
    }
    static void start()throws Exception{shell("setprop ctl.start vendor.qcrild");shell("setprop ctl.start vendor.qcrild2");}
    static void requireEnvironment()throws Exception{
        if(!prop("ro.gsid.image_running").equals("1"))throw new IOException("仅允许在正在运行的 DSU/GSI 内操作");
        if(!prop("ro.product.vendor.device").equals("cupid"))throw new IOException("此版本只为已验证的小米 12 cupid 开放修改操作");
        if(!new File(RADIO+"/qcrilNr.db").isFile())throw new IOException("没有找到已支持的高通配置数据库");
        // Mount must affect init/RIL, not only a private Magisk/app namespace.
        if(!shell("readlink /proc/self/ns/mnt").equals(shell("readlink /proc/1/ns/mnt")))
            throw new IOException("Root 位于私有挂载空间。需要主挂载空间；当前未执行修改");
    }
    static void requireGsi()throws Exception{
        requireEnvironment();
        if(!shell("lshal").contains(HOOK))throw new IOException("没有找到支持的高通 OEM hook");
        if(new File("/system/bin/qcom-atel-ready").exists())throw new IOException("系统已包含 qcom-atel-ready，请先核对内置服务，不重复修改");
    }
    static JSONObject diagnostic()throws Exception{
        JSONObject r=new JSONObject();
        r.put("gsi",prop("ro.gsid.image_running").equals("1"));r.put("device",prop("ro.product.vendor.device"));r.put("sdk",prop("ro.build.version.sdk"));
        String hals=shell("lshal");r.put("hook",hals.contains(HOOK));r.put("builtin",new File("/system/bin/qcom-atel-ready").exists());
        boolean mount=!mountLine().isEmpty();r.put("mounted",mount);r.put("owned",owned());
        r.put("canUndo",r.getBoolean("gsi")&&r.getString("device").equals("cupid")&&owned());
        r.put("ril0",prop("init.svc.vendor.qcrild"));r.put("ril1",prop("init.svc.vendor.qcrild2"));
        r.put("selinux",shell("getenforce"));r.put("masterNamespace",shell("readlink /proc/self/ns/mnt").equals(shell("readlink /proc/1/ns/mnt")));
        String config="unknown";try{config=powerConfig();}catch(Exception ignored){}r.put("powerOpt",config);
        r.put("integrity","not-recorded");if(mount&&owned()&&new File(STATE).isFile())r.put("integrity",originalHash().equals(metadata().getString("original_db_hash"))?"unchanged":"changed");
        // Only boolean indicators leave the root worker. Raw log text is neither saved nor displayed.
        String log="";try{log=shell("logcat -b radio -d -t 1800 -v brief");}catch(Exception ignored){}
        boolean held=java.util.regex.Pattern.compile("MT SMS ACK NEEDED Power Opt buffer length [1-9][0-9]*").matcher(log).find(),clear=log.contains("Reset MT SMS ACK NEEDED Power Opt buffer"),incoming=log.contains("onIncomingSms")||log.contains("SMS received");
        r.put("bufferEvidence",held);r.put("timeoutEvidence",held&&clear);r.put("incomingEvidence",incoming);
        r.put("imsRegisteredEvidence",log.contains("onRegistered")||log.contains("IMS registered"));
        r.put("supported",r.getBoolean("gsi")&&r.getString("device").equals("cupid")&&r.getBoolean("hook")&&r.getBoolean("masterNamespace")&&!r.getBoolean("builtin")&&!config.equals("unknown")&&!config.equals("missing"));
        r.put("verdict",held?"观察到 Power Opt 缓冲事件（历史日志；需收信对照确认）":(config.equals("1")?"符合故障条件，但未捕获短信被截留证据":"当前未发现 Power Opt 缓冲证据"));
        return r;
    }
    static JSONObject mount()throws Exception{
        requireGsi();
        if(!mountLine().isEmpty()){if(!owned())throw new IOException("检测到其他挂载，拒绝覆盖");return diagnostic();}
        if(!powerConfig().equals("1"))throw new IOException("原配置不是启用状态，拒绝盲目修改");
        boolean mounted=false,stopped=false;
        try{
            stopped=true;stop();shell("rm -rf "+CLONE+" && cp -a "+RADIO+" "+CLONE);
            String before=hash(new File(CLONE+"/qcrilNr.db"));
            SQLiteDatabase db=SQLiteDatabase.openDatabase(CLONE+"/qcrilNr.db",null,SQLiteDatabase.OPEN_READWRITE);
            try{
                db.beginTransaction();
                try{db.execSQL("UPDATE qcril_properties_table SET value=? WHERE property=?",new Object[]{"0",KEY});db.setTransactionSuccessful();}
                finally{db.endTransaction();}
                try(Cursor c=db.rawQuery("PRAGMA integrity_check",null)){if(!c.moveToFirst()||!c.getString(0).equals("ok"))throw new IOException("副本数据库校验失败");}
            }finally{db.close();}
            shell("chown radio:radio "+CLONE+"/qcrilNr.db; chmod 600 "+CLONE+"/qcrilNr.db; chcon -R u:object_r:vendor_radio_vendor_data_file:s0 "+CLONE);
            save(new JSONObject().put("original_db_hash",before).put("original_poweron_opt",prop(KEY)));
            shell("mount --bind "+CLONE+" "+RADIO);mounted=true;
            if(!owned()||!originalHash().equals(before))throw new IOException("挂载或原文件校验失败");
        }catch(Exception ex){if(mounted)shell("umount "+RADIO);throw ex;}
        finally{if(stopped)start();}
        JSONObject r=diagnostic();r.put("message","临时挂载已启用。等待约 30 秒恢复 4G/IMS，再测试短信。");return r;
    }
    static JSONObject undo()throws Exception{
        requireEnvironment();
        if(mountLine().isEmpty()){JSONObject r=diagnostic();r.put("message","当前没有临时挂载。");return r;}
        if(!owned())throw new IOException("拒绝撤销其他应用的挂载");
        String before=metadata().getString("original_db_hash");
        try{stop();shell("umount "+RADIO);if(!mountLine().isEmpty())throw new IOException("卸载未完成");}
        finally{start();}
        boolean unchanged=originalHash().equals(before);
        shell("rm -rf "+CLONE+"; rm -f "+STATE);
        JSONObject r=diagnostic();r.put("integrity",unchanged?"unchanged":"changed");r.put("message","已撤销挂载并恢复原配置。等待约 30 秒恢复网络。");return r;
    }
    static JSONObject ready()throws Exception{
        requireGsi();if(!mountLine().isEmpty())throw new IOException("先撤销配置挂载，再单独验证 ATEL 方案");
        int sent=0;StringBuilder slots=new StringBuilder();
        for(String s:new String[]{"oemhook0","oemhook1"}){
            try{AtelSignal.send(s);Thread.sleep(5000);AtelSignal.send(s);sent++;slots.append(s).append(' ');}
            catch(Exception ignored){}
        }
        if(sent==0)throw new IOException("两个 OEM hook 均未发送成功");
        JSONObject r=diagnostic();r.put("sentSlots",sent);r.put("expectedSlots",2);r.put("message","已向 "+slots+"发送 ATEL UI ready（单向接口，不代表已确认生效）。需短信对照；无线服务重启后需重发。配置未修改。");return r;
    }
    static JSONObject watch()throws Exception{
        JSONObject r=new JSONObject();boolean gsi=prop("ro.gsid.image_running").equals("1");
        r.put("gsi",gsi);r.put("eligible",false);r.put("mounted",!mountLine().isEmpty());
        if(!gsi)return r;
        if(!prop("ro.product.vendor.device").equals("cupid")){r.put("message","此版本尚未为该设备开放自动修复");return r;}
        if(new File("/system/bin/qcom-atel-ready").exists()){r.put("message","系统已有内置 ATEL 修复，框架停止重复上报");return r;}
        if(!shell("readlink /proc/self/ns/mnt").equals(shell("readlink /proc/1/ns/mnt"))){r.put("message","root 位于私有挂载空间");return r;}
        r.put("eligible",true);
        StringBuilder identity=new StringBuilder();
        String pids="";try{pids=shell("pidof qcrilNrd");}catch(Exception ignored){}
        for(String pid:pids.split(" +"))if(pid.matches("[0-9]+")){
            try{
                String stat=new String(java.nio.file.Files.readAllBytes(new File("/proc/"+pid+"/stat").toPath()),java.nio.charset.StandardCharsets.UTF_8);
                String[] fields=stat.substring(stat.lastIndexOf(')')+2).split(" +");
                identity.append(pid).append(':').append(fields[19]).append(';');
            }catch(Exception ignored){}
        }
        r.put("identity",identity.toString());return r;
    }
    public static void main(String[] args){
        JSONObject result=new JSONObject();int exit=0;
        try{
            if(args.length!=1)throw new IOException("Invalid action");
            try(java.io.RandomAccessFile mutex=new java.io.RandomAccessFile("/dev/gsisms-worker.lock","rw");java.nio.channels.FileChannel channel=mutex.getChannel();java.nio.channels.FileLock lock=channel.lock()){
            switch(args[0]){case "watch":result=watch();break;case "detect":result=diagnostic();break;case "mount":result=mount();break;case "undo":result=undo();break;case "ready":result=ready();break;default:throw new IOException("Unknown action");}
            result.put("ok",true);
            }
        }catch(Throwable ex){exit=1;try{result.put("ok",false);result.put("message",RootClient.safeError(ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage()));}catch(Exception ignored){}}
        System.out.println("GSISMS_RESULT="+result);System.out.flush();System.exit(0);
    }
}
