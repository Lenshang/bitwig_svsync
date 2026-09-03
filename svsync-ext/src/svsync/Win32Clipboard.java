package svsync;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

/** 通过 JNA 直接调用 Win32 剪贴板 API(绕过 headless JVM 禁用的 AWT 剪贴板)。 */
public final class Win32Clipboard {
    private static final int CF_UNICODETEXT = 13;
    private static final int GMEM_MOVEABLE = 0x0002;

    private interface User32 extends Library {
        boolean OpenClipboard(Pointer hWnd);
        boolean CloseClipboard();
        boolean EmptyClipboard();
        boolean IsClipboardFormatAvailable(int format);
        Pointer GetClipboardData(int format);
        Pointer SetClipboardData(int format, Pointer hMem);
    }

    private interface Kernel32 extends Library {
        Pointer GlobalLock(Pointer hMem);
        boolean GlobalUnlock(Pointer hMem);
        Pointer GlobalAlloc(int flags, long bytes);
    }

    private static final User32 U = Native.load("user32", User32.class);
    private static final Kernel32 K = Native.load("kernel32", Kernel32.class);

    private Win32Clipboard() {
    }

    /** 返回剪贴板文本;非文本内容或读取失败返回 null。 */
    public static String getText() {
        if (!U.OpenClipboard(null)) {
            return null;
        }
        try {
            if (!U.IsClipboardFormatAvailable(CF_UNICODETEXT)) {
                return null;
            }
            final Pointer h = U.GetClipboardData(CF_UNICODETEXT);
            if (h == null) {
                return null;
            }
            final Pointer p = K.GlobalLock(h);
            if (p == null) {
                return null;
            }
            try {
                return p.getWideString(0);
            } finally {
                K.GlobalUnlock(h);
            }
        } finally {
            U.CloseClipboard();
        }
    }

    public static boolean setText(final String text) {
        if (!U.OpenClipboard(null)) {
            return false;
        }
        try {
            U.EmptyClipboard();
            final char[] chars = (text + "\0").toCharArray();
            final Pointer mem = K.GlobalAlloc(GMEM_MOVEABLE, (long) chars.length * 2L);
            if (mem == null) {
                return false;
            }
            final Pointer p = K.GlobalLock(mem);
            if (p == null) {
                return false;
            }
            p.write(0, chars, 0, chars.length);
            K.GlobalUnlock(mem);
            return U.SetClipboardData(CF_UNICODETEXT, mem) != null;
        } finally {
            U.CloseClipboard();
        }
    }
}
