package org.gsisms.guard;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/** Raw HIDL protocol documented by yarosz/lp3-treble and TrebleDroid PR 88.
 * Independently implemented: sends only SET_ATEL_UI_STATUS, never arbitrary OEM requests.
 */
public final class AtelSignal {
    static final String IFACE = "vendor.qti.hardware.radio.qcrilhook@1.0::IQtiOemHook";
    static void send(String instance) throws Exception {
        if (!instance.equals("oemhook0") && !instance.equals("oemhook1"))
            throw new IllegalArgumentException("Unsupported slot");
        Class<?> hb = Class.forName("android.os.HwBinder");
        Class<?> ib = Class.forName("android.os.IHwBinder");
        Class<?> hp = Class.forName("android.os.HwParcel");
        Object binder = hb.getMethod("getService", String.class, String.class).invoke(null, IFACE, instance);
        if (binder == null) throw new IllegalStateException("OEM hook unavailable");
        Object req = hp.getConstructor().newInstance(), reply = hp.getConstructor().newInstance();
        try {
            ByteBuffer b = ByteBuffer.allocate(17).order(ByteOrder.nativeOrder());
            b.put("QOEMHOOK".getBytes(StandardCharsets.US_ASCII)).putInt(0x8001a).putInt(1).put((byte)1);
            ArrayList<Byte> payload = new ArrayList<>();
            for (byte x : b.array()) payload.add(x);
            hp.getMethod("writeInterfaceToken", String.class).invoke(req, IFACE);
            hp.getMethod("writeInt32", int.class).invoke(req, 1);
            hp.getMethod("writeInt8Vector", ArrayList.class).invoke(req, payload);
            ib.getMethod("transact", int.class, hp, hp, int.class).invoke(binder, 2, req, reply, 1);
        } catch (InvocationTargetException ex) {
            throw new Exception("HIDL request failed", ex.getCause());
        } finally {
            try { hp.getMethod("release").invoke(req); } catch (Throwable ignored) {}
            try { hp.getMethod("release").invoke(reply); } catch (Throwable ignored) {}
        }
    }
    public static void main(String[] args) {
        int success = 0;
        for (String slot : new String[]{"oemhook0", "oemhook1"}) {
            try { send(slot); System.out.println(slot + ": request sent (oneway; not a modem acknowledgement)"); success++; }
            catch (Throwable ex) { System.out.println(slot + ": unavailable/denied " + ex.getClass().getSimpleName()); }
        }
        System.out.flush();
        System.exit(success == 0 ? 1 : 0);
    }
}
