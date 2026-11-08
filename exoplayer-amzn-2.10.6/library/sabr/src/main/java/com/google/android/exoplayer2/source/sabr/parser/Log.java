package com.google.android.exoplayer2.source.sabr.parser;

/**
 * HearthTube: SabrStream and SabrProcessor log a debug line for every media part, hundreds of thousands an hour while
 * a video plays, which overruns the TV's system log within hours. This class has the same name and methods as the
 * shared logger, so those two files only lose their import of it: debug lines are dropped unless the file log is on
 * (Settings, for a bug report) or "adb shell setprop log.tag.SabrStream DEBUG" (likewise SabrProcessor) asks for
 * them; warnings and errors always go through. Checked before formatting, so the dropped lines cost nothing.
 */
final class Log {
    private Log() {
    }

    private static boolean debugOn(String tag) {
        if (com.liskovsoft.sharedutils.mylogger.Log.LOG_TYPE_FILE.equals(com.liskovsoft.sharedutils.mylogger.Log.getLogType())) {
            return true;
        }

        try {
            return android.util.Log.isLoggable(tag, android.util.Log.DEBUG);
        } catch (IllegalArgumentException e) { // a tag over 23 characters on old Android
            return false;
        }
    }

    static void d(String tag, Object msg, Object... formatArgs) {
        if (debugOn(tag)) {
            com.liskovsoft.sharedutils.mylogger.Log.d(tag, msg, formatArgs);
        }
    }

    static void d(String tag, Object msg, Throwable ex) {
        if (debugOn(tag)) {
            com.liskovsoft.sharedutils.mylogger.Log.d(tag, msg, ex);
        }
    }

    static void w(String tag, Object msg, Object... formatArgs) {
        com.liskovsoft.sharedutils.mylogger.Log.w(tag, msg, formatArgs);
    }

    static void w(String tag, Object msg, Throwable ex) {
        com.liskovsoft.sharedutils.mylogger.Log.w(tag, msg, ex);
    }

    static void e(String tag, Object msg, Object... formatArgs) {
        com.liskovsoft.sharedutils.mylogger.Log.e(tag, msg, formatArgs);
    }

    static void e(String tag, Object msg, Throwable ex) {
        com.liskovsoft.sharedutils.mylogger.Log.e(tag, msg, ex);
    }
}
