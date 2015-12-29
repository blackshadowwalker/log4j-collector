package org.apache.log4j;

import org.apache.log4j.net.SyslogAppender;

/**
 * Created by karl on 2015/8/6.
 */
public class EmailLogger {

    private static final String FQCN = EmailLogger.class.getName();
    private static Logger log = Logger.getLogger(EmailLogger.class);

    public final static int EMAIL_INT = 45000;

    private static class EZheLevel extends Level{
        protected EZheLevel(int level, String levelStr, int syslogEquivalent) {
            super(level, levelStr, syslogEquivalent);
        }
    }

    public static Level EMAIL = new EZheLevel(EMAIL_INT, "EMAIL", SyslogAppender.LOG_KERN);

    public static void email(Object message){
        log.log(FQCN, EmailLogger.EMAIL, message, null);
    }

    public static void email(Object message, Throwable t){
        log.log(FQCN, EmailLogger.EMAIL, message, t);
    }

    public static Level toLevel(String sArg, Level defaultValue) {
        if(sArg.toUpperCase().equals("EMAIL")) return EmailLogger.EMAIL;
        return defaultValue;
    }

}
