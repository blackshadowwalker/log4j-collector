package com.log4j.email;

import org.apache.log4j.AppenderSkeleton;
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
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

/**
 * Created by karl on 2015/8/6.
 */
public class EmailAppender extends AppenderSkeleton implements UnrecognizedElementHandler {
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
    private String encoding;

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

    @Override
    protected void append(LoggingEvent event) {
        if(evaluator.isTriggeringEvent(event)) {
            System.out.println("send email");
            StringBuilder sb = new StringBuilder();
            sb.append(this.layout.getHeader());
            sb.append(this.layout.format(event));
            sb.append(this.layout.getFooter());
            this.sendEmail(sb.toString());
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

    protected void sendEmail(String emailMsg){
        try {
            StringBuilder sb = new StringBuilder();
            String host = getLocalHostInfo();

            if(host!=null)
                sb.append((new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")).format(new Date()) + "/" + host + "/" + "\n\n");

            msg.setSubject(MimeUtility.encodeText(subject + "(" + host + ")", "UTF-8", null));
            sb.append(emailMsg);

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
                }
            }

            Multipart mp = new MimeMultipart();
            mp.addBodyPart(part);
            msg.setContent(mp);

            msg.setSentDate(new Date());
            Transport.send(msg);

        }catch (Exception e){
            LogLog.error( "send email error." , e);
        }
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String value) {
        encoding = value;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getCc() {
        return cc;
    }

    public void setCc(String cc) {
        this.cc = cc;
    }

    public String getBcc() {
        return bcc;
    }

    public void setBcc(String bcc) {
        this.bcc = bcc;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public String getReplyTo() {
        return replyTo;
    }

    public void setReplyTo(String replyTo) {
        this.replyTo = replyTo;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getSmtpHost() {
        return smtpHost;
    }

    public void setSmtpHost(String smtpHost) {
        this.smtpHost = smtpHost;
    }

    public String getSmtpUsername() {
        return smtpUsername;
    }

    public void setSmtpUsername(String smtpUsername) {
        this.smtpUsername = smtpUsername;
    }

    public String getSmtpPassword() {
        return smtpPassword;
    }

    public void setSmtpPassword(String smtpPassword) {
        this.smtpPassword = smtpPassword;
    }

    public String getSmtpProtocol() {
        return smtpProtocol;
    }

    public void setSmtpProtocol(String smtpProtocol) {
        this.smtpProtocol = smtpProtocol;
    }

    public int getSmtpPort() {
        return smtpPort;
    }

    public void setSmtpPort(int smtpPort) {
        this.smtpPort = smtpPort;
    }

    public boolean isSmtpDebug() {
        return smtpDebug;
    }

    public void setSmtpDebug(boolean smtpDebug) {
        this.smtpDebug = smtpDebug;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public boolean isLocationInfo() {
        return locationInfo;
    }

    public void setLocationInfo(boolean locationInfo) {
        this.locationInfo = locationInfo;
    }

    public boolean isSendOnClose() {
        return sendOnClose;
    }

    public void setSendOnClose(boolean sendOnClose) {
        this.sendOnClose = sendOnClose;
    }

    public CyclicBuffer getCb() {
        return cb;
    }

    public void setCb(CyclicBuffer cb) {
        this.cb = cb;
    }

    public Message getMsg() {
        return msg;
    }

    public void setMsg(Message msg) {
        this.msg = msg;
    }

    public TriggeringEventEvaluator getEvaluator() {
        return evaluator;
    }

    public void setEvaluator(TriggeringEventEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    @Override
    public void close() {
    }

    @Override
    public boolean requiresLayout() {
        return true;
    }

    @Override
    public boolean parseUnrecognizedElement(Element element, Properties props) throws Exception {
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
}
