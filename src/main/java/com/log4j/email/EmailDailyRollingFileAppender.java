package com.log4j.email;

import org.apache.log4j.DailyRollingFileAppender;
import org.apache.log4j.helpers.CyclicBuffer;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.spi.ErrorCode;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.OptionHandler;
import org.apache.log4j.spi.TriggeringEventEvaluator;
import org.apache.log4j.xml.UnrecognizedElementHandler;
import org.w3c.dom.Element;

import javax.mail.*;
import javax.mail.internet.*;
import java.io.*;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by karl on 2015/7/23.
 */
public class EmailDailyRollingFileAppender extends DailyRollingFileAppender implements UnrecognizedElementHandler{

    private String to;
    /**
     * Comma separated list of cc recipients.
     */
    private String cc;
    /**
     * Comma separated list of bcc recipients.
     */
    private String bcc;
    private String from;
    private String nickName;
    /**
     * Comma separated list of replyTo addresses.
     */
    private String replyTo;
    private String subject;
    private String smtpHost;
    private String smtpUsername;
    private String smtpPassword;
    private String smtpProtocol;
    private int smtpPort = -1;
    private boolean smtpDebug = false;
    private int bufferSize = 512;
    private boolean locationInfo = false;
    private boolean sendOnClose = false;

    protected CyclicBuffer cb = new CyclicBuffer(bufferSize);
    protected Message msg;

    protected TriggeringEventEvaluator evaluator;

    InternetAddress getAddress(String addressStr) {
        InternetAddress internetAddress = null;
        try {
            internetAddress = new InternetAddress(addressStr);
            internetAddress.setPersonal(internetAddress.getPersonal());
            return internetAddress;
        }catch (UnsupportedEncodingException e1){
            errorHandler.error("Could not parse address ["+addressStr+"].", e1,
                    ErrorCode.ADDRESS_PARSE_FAILURE);
        } catch(AddressException e) {
            errorHandler.error("Could not parse address ["+addressStr+"].", e,
                    ErrorCode.ADDRESS_PARSE_FAILURE);
        }
        return null;
    }

    InternetAddress[] parseAddress(String addressStr) {
        try {
            InternetAddress[] as = InternetAddress.parse(addressStr, true);
            if (as != null && as.length > 0) {
                for (InternetAddress a : as) {
                    a.setPersonal(a.getPersonal());
                }
            }
            return as;
        }catch (UnsupportedEncodingException e1){
            errorHandler.error("Could not parse address ["+addressStr+"].", e1,
                    ErrorCode.ADDRESS_PARSE_FAILURE);
        } catch(AddressException e) {
            errorHandler.error("Could not parse address ["+addressStr+"].", e,
                    ErrorCode.ADDRESS_PARSE_FAILURE);
        }
        return null;
    }

    protected void addressMessage(final Message msg) throws MessagingException {
        if (from != null) {
            InternetAddress internetAddress = getAddress(from);
            if(this.nickName!=null) {
                try{
                    internetAddress.setPersonal(this.nickName, this.getEncoding());
                }catch (UnsupportedEncodingException e1){
                    errorHandler.error("Could not parse address ["+internetAddress+"].", e1,
                            ErrorCode.ADDRESS_PARSE_FAILURE);
                }
            }
            msg.setFrom(internetAddress);
        } else {
            msg.setFrom();
        }

        //Add ReplyTo addresses if defined.
        if (replyTo != null && replyTo.length() > 0) {
            msg.setReplyTo(parseAddress(replyTo));
        }

        if (to != null && to.length() > 0) {
            msg.setRecipients(Message.RecipientType.TO, parseAddress(to));
        }

        //Add CC receipients if defined.
        if (cc != null && cc.length() > 0) {
            msg.setRecipients(Message.RecipientType.CC, parseAddress(cc));
        }

        //Add BCC receipients if defined.
        if (bcc != null && bcc.length() > 0) {
            msg.setRecipients(Message.RecipientType.BCC, parseAddress(bcc));
        }
    }

    public
    void activateOptions() {
        super.activateOptions();
        if(datePattern != null && fileName != null) {
            now.setTime(System.currentTimeMillis());
            sdf = new SimpleDateFormat(datePattern);
            int type = computeCheckPeriod();
            printPeriodicity(type);
            rc.setType(type);
            File file = new File(fileName);
            scheduledFilename = fileName+sdf.format(new Date(file.lastModified()));

        } else {
            LogLog.error("Either File or DatePattern options are not set for appender ["
                    +name+"].");
        }

        Session session = createSession();
        msg = new MimeMessage(session);

        try {
            addressMessage(msg);
            if(subject != null) {
                try {
                    msg.setSubject(MimeUtility.encodeText(subject, "UTF-8", null));
                } catch(UnsupportedEncodingException ex) {
                    LogLog.error("Unable to encode SMTP subject", ex);
                }
            }
        } catch(MessagingException e) {
            LogLog.error("Could not activate SMTPAppender options.", e );
        }

        if (evaluator!=null && evaluator instanceof OptionHandler) {
            ((OptionHandler) evaluator).activateOptions();
        }
    }

    protected Session createSession() {
        Properties props = null;
        try {
            props = new Properties (System.getProperties());
        } catch(SecurityException ex) {
            props = new Properties();
        }

        String prefix = "mail.smtp";
        if (smtpProtocol != null) {
            props.put("mail.transport.protocol", smtpProtocol);
            prefix = "mail." + smtpProtocol;
        }
        if (smtpHost != null) {
            props.put(prefix + ".host", smtpHost);
        }
        if (smtpPort > 0) {
            props.put(prefix + ".port", String.valueOf(smtpPort));
        }

        Authenticator auth = null;
        if(smtpPassword != null && smtpUsername != null) {
            props.put(prefix + ".auth", "true");
            auth = new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(smtpUsername, smtpPassword);
                }
            };
        }
        Session session = Session.getInstance(props, auth);
        if (smtpProtocol != null) {
            session.setProtocolForAddress("rfc822", smtpProtocol);
        }
        if (smtpDebug) {
            session.setDebug(smtpDebug);
        }
        return session;
    }

    private String getLocalHostInfo(){
        try{
            String info = InetAddress.getLocalHost().getHostName() + "/" + InetAddress.getLocalHost().getHostAddress();
            return info ;
        }catch (Exception e){
            e.printStackTrace();
        }
        return "";
    }

    protected void sendEmail(File file){
        try {
            if(!file.exists()) {
                LogLog.error( "sendEmail File Not Exist[" + file.getAbsolutePath()+"]");
                return;
            }
            LogLog.warn( "sending email " + file.getAbsolutePath());
            StringBuilder sb = new StringBuilder();
            String host = getLocalHostInfo();

            if(host!=null)
                sb.append((new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")).format(new Date()) + "/" + host + "/" + file.getAbsolutePath() + "\n\n");
            msg.setSubject(MimeUtility.encodeText(subject + "-" + host, "UTF-8", null));

            FileReader fileReader = new FileReader(file);
            boolean allAscii = false;
            MimeBodyPart part;
            if (allAscii) {
                part = new MimeBodyPart();
                part.setContent(sb.toString(), layout.getContentType());
            } else {
                try {
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                    Writer writer = new OutputStreamWriter(
                            MimeUtility.encode(os, "quoted-printable"), "UTF-8");
                    writer.write(sb.toString());
                    int len = -1;
                    char[] buffer = new char[4096];
                    do{
                        len = fileReader.read(buffer);
                        if(len==-1)
                            break;
                        writer.write(buffer, 0, len);
                    }while (true);
                    writer.close();
                    InternetHeaders headers = new InternetHeaders();
                    headers.setHeader("Content-Type", layout.getContentType() + "; charset=UTF-8");
                    headers.setHeader("Content-Transfer-Encoding", "quoted-printable");
                    part = new MimeBodyPart(headers, os.toByteArray());
                } catch(Exception ex) {
                    StringBuffer sbuf = new StringBuffer(sb.toString());
                    for (int i = 0; i < sbuf.length(); i++) {
                        if (sbuf.charAt(i) >= 0x80) {
                            sbuf.setCharAt(i, '?');
                        }
                    }
                    part = new MimeBodyPart();
                    part.setContent(sbuf.toString(), layout.getContentType());
                }finally {
                    fileReader.close();
                }
            }

            Multipart mp = new MimeMultipart();
            mp.addBodyPart(part);
            msg.setContent(mp);

            msg.setSentDate(new Date());
            Transport.send(msg);

            LogLog.warn( "sended email " + file.getAbsolutePath());
        }catch (Exception e){
            LogLog.error( "send email error." , e);
        }
    }

    @Override
    protected void closeFile() {
        super.closeFile();
    }

    // The code assumes that the following constants are in a increasing
    // sequence.
    public static final int TOP_OF_TROUBLE=-1;
    public static final int TOP_OF_MINUTE = 0;
    public static final int TOP_OF_HOUR   = 1;
    public static final int HALF_DAY      = 2;
    public static final int TOP_OF_DAY    = 3;
    public static final int TOP_OF_WEEK   = 4;
    public static final int TOP_OF_MONTH  = 5;

    /**
     The date pattern. By default, the pattern is set to
     "'.'yyyy-MM-dd" meaning daily rollover.
     */
    protected String datePattern = "'.'yyyy-MM-dd";

    /**
     The log file will be renamed to the value of the
     scheduledFilename variable when the next interval is entered. For
     example, if the rollover period is one hour, the log file will be
     renamed to the value of "scheduledFilename" at the beginning of
     the next hour.

     The precise time when a rollover occurs depends on logging
     activity.
     */
    protected String scheduledFilename;

    /**
     The next time we estimate a rollover should occur. */
    protected long nextCheck = System.currentTimeMillis () - 1;

    protected Date now = new Date();

    protected SimpleDateFormat sdf;

    protected RollingCalendar rc = new RollingCalendar();

    protected int checkPeriod = TOP_OF_TROUBLE;

    // The gmtTimeZone is used only in computeCheckPeriod() method.
    static final TimeZone gmtTimeZone = TimeZone.getTimeZone("GMT");

    /**
     The <b>DatePattern</b> takes a string in the same format as
     expected by {@link java.text.SimpleDateFormat}. This options determines the
     rollover schedule.
     */
    public void setDatePattern(String pattern) {
        datePattern = pattern;
    }

    /** Returns the value of the <b>DatePattern</b> option. */
    public String getDatePattern() {
        return datePattern;
    }

    void printPeriodicity(int type) {
        switch(type) {
            case TOP_OF_MINUTE:
                LogLog.debug("Appender ["+name+"] to be rolled every minute.");
                break;
            case TOP_OF_HOUR:
                LogLog.debug("Appender ["+name
                        +"] to be rolled on top of every hour.");
                break;
            case HALF_DAY:
                LogLog.debug("Appender ["+name
                        +"] to be rolled at midday and midnight.");
                break;
            case TOP_OF_DAY:
                LogLog.debug("Appender ["+name
                        +"] to be rolled at midnight.");
                break;
            case TOP_OF_WEEK:
                LogLog.debug("Appender ["+name
                        +"] to be rolled at start of week.");
                break;
            case TOP_OF_MONTH:
                LogLog.debug("Appender ["+name
                        +"] to be rolled at start of every month.");
                break;
            default:
                LogLog.warn("Unknown periodicity for appender ["+name+"].");
        }
    }


    // This method computes the roll over period by looping over the
    // periods, starting with the shortest, and stopping when the r0 is
    // different from from r1, where r0 is the epoch formatted according
    // the datePattern (supplied by the user) and r1 is the
    // epoch+nextMillis(i) formatted according to datePattern. All date
    // formatting is done in GMT and not local format because the test
    // logic is based on comparisons relative to 1970-01-01 00:00:00
    // GMT (the epoch).

    protected int computeCheckPeriod() {
        RollingCalendar rollingCalendar = new RollingCalendar(gmtTimeZone, Locale.getDefault());
        // set sate to 1970-01-01 00:00:00 GMT
        Date epoch = new Date(0);
        if(datePattern != null) {
            for(int i = TOP_OF_MINUTE; i <= TOP_OF_MONTH; i++) {
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat(datePattern);
                simpleDateFormat.setTimeZone(gmtTimeZone); // do all date formatting in GMT
                String r0 = simpleDateFormat.format(epoch);
                rollingCalendar.setType(i);
                Date next = new Date(rollingCalendar.getNextCheckMillis(epoch));
                String r1 =  simpleDateFormat.format(next);
                //System.out.println("Type = "+i+", r0 = "+r0+", r1 = "+r1);
                if(r0 != null && r1 != null && !r0.equals(r1)) {
                    return i;
                }
            }
        }
        return TOP_OF_TROUBLE; // Deliberately head for trouble...
    }

    /**
     Rollover the current file to a new file.
     */
    protected void rollOver(LoggingEvent event) throws IOException {

    /* Compute filename, but only if datePattern is specified */
        if (datePattern == null) {
            errorHandler.error("Missing DatePattern option in rollOver().");
            return;
        }

        String datedFilename = fileName+sdf.format(now);
        // It is too early to roll over because we are still within the
        // bounds of the current interval. Rollover will occur once the
        // next interval is reached.
        if (scheduledFilename.equals(datedFilename)) {
            return;
        }

        // close current file, and rename it to datedFilename
        this.closeFile();

        File target  = new File(scheduledFilename);
        if (target.exists()) {
            target.delete();
        }

        File file = new File(fileName);
        boolean result = file.renameTo(target);
        if(result) {
            LogLog.debug(fileName +" -> "+ scheduledFilename);
            if(this.evaluator==null) {
                sendEmail(target);
            }else if(this.evaluator.isTriggeringEvent(event)) {
                sendEmail(target);
            }else{
                LogLog.warn(evaluator + " prevent sendEmail. " + new Date());
            }

        } else {
            LogLog.error("Failed to rename [" + fileName + "] to [" + scheduledFilename + "].");
        }

        try {
            // This will also close the file. This is OK since multiple
            // close operations are safe.
            this.setFile(fileName, true, this.bufferedIO, this.bufferSize);
        }
        catch(IOException e) {
            errorHandler.error("setFile("+fileName+", true) call failed.");
        }
        scheduledFilename = datedFilename;
    }

    public boolean parseUnrecognizedElement(final Element element,
                                            final Properties props) throws Exception {
        if ("triggeringPolicy".equals(element.getNodeName())) {
            Object triggerPolicy = org.apache.log4j.xml.DOMConfigurator.parseElement(
                            element, props, TriggeringEventEvaluator.class);
            if (triggerPolicy instanceof TriggeringEventEvaluator) {
                setEvaluator((TriggeringEventEvaluator) triggerPolicy);
            }
            return true;
        }

        return false;
    }

    /**
     * This method differentiates DailyRollingFileAppender from its
     * super class.
     *
     * <p>Before actually logging, this method will check whether it is
     * time to do a rollover. If it is, it will schedule the next
     * rollover time and then rollover.
     * */
    protected void subAppend(LoggingEvent event) {
        long n = System.currentTimeMillis();
        if (n >= nextCheck) {
            now.setTime(n);
            nextCheck = rc.getNextCheckMillis(now);
            try {
                rollOver(event);
            }
            catch(IOException ioe) {
                if (ioe instanceof InterruptedIOException) {
                    Thread.currentThread().interrupt();
                }
                LogLog.error("rollOver() failed.", ioe);
            }
        }
        super.subAppend(event);
    }

    public void setTo(String to) {
        this.to = to;
    }

    public void setCc(String cc) {
        this.cc = cc;
    }

    public void setBcc(String bcc) {
        this.bcc = bcc;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public void setNickName(String nickName){
        this.nickName = nickName;
    }
    public void setReplyTo(String replyTo) {
        this.replyTo = replyTo;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public void setSmtpHost(String smtpHost) {
        this.smtpHost = smtpHost;
    }

    public void setSmtpUsername(String smtpUsername) {
        this.smtpUsername = smtpUsername;
    }

    public void setSmtpPassword(String smtpPassword) {
        this.smtpPassword = smtpPassword;
    }

    public void setSmtpProtocol(String smtpProtocol) {
        this.smtpProtocol = smtpProtocol;
    }

    public void setSmtpPort(int smtpPort) {
        this.smtpPort = smtpPort;
    }

    public void setSmtpDebug(boolean smtpDebug) {
        this.smtpDebug = smtpDebug;
    }

    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public void setLocationInfo(boolean locationInfo) {
        this.locationInfo = locationInfo;
    }

    public void setSendOnClose(boolean sendOnClose) {
        this.sendOnClose = sendOnClose;
    }

    public void setCb(CyclicBuffer cb) {
        this.cb = cb;
    }

    public void setMsg(Message msg) {
        this.msg = msg;
    }

    public void setEvaluator(TriggeringEventEvaluator evaluator) {
        this.evaluator = evaluator;
    }
}


/**
 *  RollingCalendar is a helper class to DailyRollingFileAppender.
 *  Given a periodicity type and the current time, it computes the
 *  start of the next interval.
 * */
class RollingCalendar extends GregorianCalendar {
    private static final long serialVersionUID = -3560331770601814177L;

    int type = EmailDailyRollingFileAppender.TOP_OF_TROUBLE;

    RollingCalendar() {
        super();
    }

    RollingCalendar(TimeZone tz, Locale locale) {
        super(tz, locale);
    }

    void setType(int type) {
        this.type = type;
    }

    public long getNextCheckMillis(Date now) {
        return getNextCheckDate(now).getTime();
    }

    public Date getNextCheckDate(Date now) {
        this.setTime(now);

        switch (type) {
            case EmailDailyRollingFileAppender.TOP_OF_MINUTE:
                this.set(Calendar.SECOND, 0);
                this.set(Calendar.MILLISECOND, 0);
                this.add(Calendar.MINUTE, 1);
                break;
            case EmailDailyRollingFileAppender.TOP_OF_HOUR:
                this.set(Calendar.MINUTE, 0);
                this.set(Calendar.SECOND, 0);
                this.set(Calendar.MILLISECOND, 0);
                this.add(Calendar.HOUR_OF_DAY, 1);
                break;
            case EmailDailyRollingFileAppender.HALF_DAY:
                this.set(Calendar.MINUTE, 0);
                this.set(Calendar.SECOND, 0);
                this.set(Calendar.MILLISECOND, 0);
                int hour = get(Calendar.HOUR_OF_DAY);
                if (hour < 12) {
                    this.set(Calendar.HOUR_OF_DAY, 12);
                } else {
                    this.set(Calendar.HOUR_OF_DAY, 0);
                    this.add(Calendar.DAY_OF_MONTH, 1);
                }
                break;
            case EmailDailyRollingFileAppender.TOP_OF_DAY:
                this.set(Calendar.HOUR_OF_DAY, 0);
                this.set(Calendar.MINUTE, 0);
                this.set(Calendar.SECOND, 0);
                this.set(Calendar.MILLISECOND, 0);
                this.add(Calendar.DATE, 1);
                break;
            case EmailDailyRollingFileAppender.TOP_OF_WEEK:
                this.set(Calendar.DAY_OF_WEEK, getFirstDayOfWeek());
                this.set(Calendar.HOUR_OF_DAY, 0);
                this.set(Calendar.MINUTE, 0);
                this.set(Calendar.SECOND, 0);
                this.set(Calendar.MILLISECOND, 0);
                this.add(Calendar.WEEK_OF_YEAR, 1);
                break;
            case EmailDailyRollingFileAppender.TOP_OF_MONTH:
                this.set(Calendar.DATE, 1);
                this.set(Calendar.HOUR_OF_DAY, 0);
                this.set(Calendar.MINUTE, 0);
                this.set(Calendar.SECOND, 0);
                this.set(Calendar.MILLISECOND, 0);
                this.add(Calendar.MONTH, 1);
                break;
            default:
                throw new IllegalStateException("Unknown periodicity type.");
        }
        return getTime();
    }

}