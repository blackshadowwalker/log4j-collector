package org.apache.log4j;

import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.helpers.PatternConverter;
import org.apache.log4j.helpers.Transform;
import org.apache.log4j.spi.LocationInfo;
import org.apache.log4j.spi.LoggingEvent;

import java.text.SimpleDateFormat;

/**
 * Created by karl on 2015/7/24.
 */
public class FormatHTMLLayout extends Layout {

    protected final int BUF_SIZE = 256;
    protected final int MAX_CAPACITY = 1024;

    static String TRACE_PREFIX = "<br>&nbsp;&nbsp;&nbsp;&nbsp;";

    // output buffer appended to when format() is invoked
    protected StringBuffer sbuf = new StringBuffer(BUF_SIZE);
    protected String encoding = "utf-8";

    // output buffer appended to when format() is invoked
    private String pattern;

    private PatternConverter head;

    /**
     * A string constant used in naming the option for setting the the
     * location information flag.  Current value of this string
     * constant is <b>LocationInfo</b>.
     * <p/>
     * <p>Note that all option keys are case sensitive.
     *
     * @deprecated Options are now handled using the JavaBeans paradigm.
     * This constant is not longer needed and will be removed in the
     * <em>near</em> term.
     */
    public static final String LOCATION_INFO_OPTION = "LocationInfo";

    /**
     * A string constant used in naming the option for setting the the
     * HTML document title.  Current value of this string
     * constant is <b>Title</b>.
     */
    public static final String TITLE_OPTION = "Title";

    // Print no location info by default
    boolean locationInfo = false;

    String title = "Log4J Log Messages";

    /**
     * The <b>LocationInfo</b> option takes a boolean value. By
     * default, it is set to false which means there will be no location
     * information output by this layout. If the the option is set to
     * true, then the file name and line number of the statement
     * at the origin of the log statement will be output.
     * <p/>
     * <p>If you are embedding this layout within an {@link
     * org.apache.log4j.net.SMTPAppender} then make sure to set the
     * <b>LocationInfo</b> option of that appender as well.
     */
    public void setLocationInfo(boolean flag) {
        locationInfo = flag;
    }

    /**
     * Returns the current value of the <b>LocationInfo</b> option.
     */
    public boolean getLocationInfo() {
        return locationInfo;
    }

    /**
     * The <b>Title</b> option takes a String value. This option sets the
     * document title of the generated HTML document.
     * <p/>
     * <p>Defaults to 'Log4J Log Messages'.
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Returns the current value of the <b>Title</b> option.
     */
    public String getTitle() {
        return title;
    }

    /**
     * Returns the content type output by this layout, i.e "text/html".
     */
    public String getContentType() {
        return "text/html";
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    /**
     * No options to activate.
     */
    public void activateOptions() {
    }

    public String format(LoggingEvent event) {

        if (sbuf.capacity() > MAX_CAPACITY) {
            sbuf = new StringBuffer(BUF_SIZE);
        } else {
            sbuf.setLength(0);
        }

        sbuf.append(Layout.LINE_SEP + "<tr>" + Layout.LINE_SEP);

        sbuf.append("<td >");
//        sbuf.append(event.timeStamp - LoggingEvent.getStartTime());
        sbuf.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));
        sbuf.append("</td>" + Layout.LINE_SEP);

        Object app = event.getMDC("app");
        sbuf.append("<td title=\"" + app + " thread\">");
        if(app!=null){
            sbuf.append(app.toString());
        }
        sbuf.append("&nbsp;</td>" + Layout.LINE_SEP);

        String escapedThread = Transform.escapeTags(event.getThreadName());
        sbuf.append("<td title=\"" + escapedThread + " thread\">");
        sbuf.append(escapedThread);
        sbuf.append("</td>" + Layout.LINE_SEP);

        sbuf.append("<td title=\"Level\">");
        if (event.getLevel().equals(Level.DEBUG)) {
            sbuf.append("<font color=\"#339933\">");
            sbuf.append(Transform.escapeTags(String.valueOf(event.getLevel())));
            sbuf.append("</font>");
        } else if (event.getLevel().isGreaterOrEqual(Level.WARN)) {
            sbuf.append("<font color=\"#993300\"><strong>");
            sbuf.append(Transform.escapeTags(String.valueOf(event.getLevel())));
            sbuf.append("</strong></font>");
        } else {
            sbuf.append(Transform.escapeTags(String.valueOf(event.getLevel())));
        }
        sbuf.append("</td>" + Layout.LINE_SEP);

        String escapedLogger = Transform.escapeTags(event.getLoggerName());
        sbuf.append("<td title=\"" + escapedLogger + " category\">");
        sbuf.append(escapedLogger);
        sbuf.append("</td>" + Layout.LINE_SEP);

        if (locationInfo) {
            LocationInfo locInfo = event.getLocationInformation();
            sbuf.append("<td>");
            sbuf.append(Transform.escapeTags(locInfo.getFileName()));
            sbuf.append(':');
            sbuf.append(locInfo.getLineNumber());
            sbuf.append("</td>" + Layout.LINE_SEP);
        }

        sbuf.append("<td title=\"Message\">");
        sbuf.append(Transform.escapeTags(event.getRenderedMessage()));
        sbuf.append("</td>" + Layout.LINE_SEP);
        sbuf.append("</tr>" + Layout.LINE_SEP);

        if (event.getNDC() != null) {
            sbuf.append("<tr><td bgcolor=\"#EEEEEE\" style=\"font-size : xx-small;\" colspan=\"6\" title=\"Nested Diagnostic Context\">");
            sbuf.append("NDC: " + Transform.escapeTags(event.getNDC()));
            sbuf.append("</td></tr>" + Layout.LINE_SEP);
        }

        String[] s = event.getThrowableStrRep();
        if (s != null) {
            sbuf.append("<tr><td bgcolor=\"#993300\" style=\"color:White; font-size : xx-small;\" colspan=\"6\">");
            appendThrowableAsHTML(s, sbuf);
            sbuf.append("</td></tr>" + Layout.LINE_SEP);
        }

        return sbuf.toString();
    }

    void appendThrowableAsHTML(String[] s, StringBuffer sbuf) {
        if (s != null) {
            int len = s.length;
            if (len == 0)
                return;
            sbuf.append(Transform.escapeTags(s[0]));
            sbuf.append(Layout.LINE_SEP);
            for (int i = 1; i < len; i++) {
                sbuf.append(TRACE_PREFIX);
                sbuf.append(Transform.escapeTags(s[i]));
                sbuf.append(Layout.LINE_SEP);
            }
        }
    }

    /**
     * Returns appropriate HTML headers.
     */
    public String getHeader() {
        StringBuffer sbuf = new StringBuffer();
        sbuf.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\">" + Layout.LINE_SEP);
        sbuf.append("<html>" + Layout.LINE_SEP);
        sbuf.append("<head>" + Layout.LINE_SEP);
        sbuf.append("<title>" + title + "</title>" + Layout.LINE_SEP);
        sbuf.append("<meta http-equiv=\"content-type\" content=\"text/html; charset=" + this.encoding + "\">");
        sbuf.append("<style type=\"text/css\">" + Layout.LINE_SEP);
        sbuf.append("<!--" + Layout.LINE_SEP);
        sbuf.append("body, table {font-family: arial,sans-serif; font-size: 11px;}" + Layout.LINE_SEP);
        sbuf.append("th {background: #336699; color: #FFFFFF; text-align: left;}" + Layout.LINE_SEP);
        sbuf.append("-->" + Layout.LINE_SEP);
        sbuf.append("</style>" + Layout.LINE_SEP);
        sbuf.append("</head>" + Layout.LINE_SEP);
        sbuf.append("<body bgcolor=\"#FFFFFF\" topmargin=\"6\" leftmargin=\"6\">" + Layout.LINE_SEP);
        sbuf.append("<div>" + header + "<div>");
        sbuf.append("<hr size=\"1\" noshade>" + Layout.LINE_SEP);
        sbuf.append("Log session start time " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()) + "<br>" + Layout.LINE_SEP);
        sbuf.append("<br>" + Layout.LINE_SEP);
        sbuf.append("<table style=\"max-width:1150px;\" cellspacing=\"0\" cellpadding=\"4\" border=\"1\" bordercolor=\"#224466\" width=\"100%\">" + Layout.LINE_SEP);
        sbuf.append("<tr>" + Layout.LINE_SEP);
        sbuf.append("<th min-width='111px'>Time</th>" + Layout.LINE_SEP);
        sbuf.append("<th min-width='80px' >APP</th>" + Layout.LINE_SEP);
        sbuf.append("<th min-width='100px' >Thread</th>" + Layout.LINE_SEP);
        sbuf.append("<th min-width='45px'>Level</th>" + Layout.LINE_SEP);
        sbuf.append("<th min-width='250px'>Category</th>" + Layout.LINE_SEP);
        if (locationInfo) {
            sbuf.append("<th min-width='250px'>File:Line</th>" + Layout.LINE_SEP);
        }
        sbuf.append("<th min-width='500px'>Message</th>" + Layout.LINE_SEP);
        sbuf.append("</tr>" + Layout.LINE_SEP);
        return sbuf.toString();
    }

    /**
     * Returns the appropriate HTML footers.
     */
    public String getFooter() {
        StringBuffer sbuf = new StringBuffer();
        sbuf.append("</table>" + Layout.LINE_SEP);
        sbuf.append("<br>" + Layout.LINE_SEP);
        sbuf.append("<div>" + footer + "<div>");
        sbuf.append("<br>" + Layout.LINE_SEP);
        sbuf.append("</body></html>");
        return sbuf.toString();
    }

    private String header;
    private String footer;

    public void setHeader(String header) {
        this.header = header;
    }

    public void setFooter(String footer) {
        this.footer = footer;
    }

    /**
     * The HTML layout handles the throwable contained in logging
     * events. Hence, this method return <code>false</code>.
     */
    public boolean ignoresThrowable() {
        return false;
    }
}
