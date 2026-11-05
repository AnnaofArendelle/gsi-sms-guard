package org.gsisms.guard;
import android.content.*;
/** Starts only an already-enabled framework; never silently opts the user in. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        android.util.Log.i("GsiSmsFramework","boot event="+i.getAction());
        if(!c.getSharedPreferences("framework",0).getBoolean("enabled",false))return;
        if(!Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())&&!Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction()))return;
        try{c.startForegroundService(new Intent(c,FrameworkService.class));}
        catch(RuntimeException ex){android.util.Log.e("GsiSmsFramework","boot start failed",ex);} // UI reports a stopped service next time it opens.
    }
}
