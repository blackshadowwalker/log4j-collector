package org.apache.log4j.client;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.reflect.ReflectData;
import org.apache.avro.reflect.ReflectDatumWriter;
import org.apache.avro.specific.SpecificRecord;
import org.apache.flume.Event;
import org.apache.flume.EventDeliveryException;
import org.apache.flume.FlumeException;
import org.apache.flume.api.RpcClient;
import org.apache.flume.api.RpcClientConfigurationConstants;
import org.apache.flume.api.RpcClientFactory;
import org.apache.flume.clients.log4jappender.Log4jAvroHeaders;
import org.apache.flume.event.EventBuilder;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Layout;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.spi.LoggingEvent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by karl on 2015/12/15.
 */
public class FlumeAppender extends AppenderSkeleton {
    private String hostname;
    private String application;
    private int port;
    private boolean unsafeMode = true;
    private long maxIoWorkers = 5;
    private long timeout = RpcClientConfigurationConstants.DEFAULT_REQUEST_TIMEOUT_MILLIS;
    private volatile long reconnectionDelay = 10000;
    private boolean avroReflectionEnabled;
    private String avroSchemaUrl;
    private volatile Connector connector;
    private String clientIP;
    private Integer localPort = 31234;
    RpcClient rpcClient = null;
    private static AtomicLong rpcReconnectTimes = new AtomicLong(0);

//    private String dateFormat = "yyyy-MM-dd HH:mm:ss.SSS";
    private String dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

    public DateFormat DATE_FORMAT = new SimpleDateFormat(dateFormat);

    /**
     * If this constructor is used programmatically rather than from a log4j conf
     * you must set the <tt>port</tt> and <tt>hostname</tt> and then call
     * <tt>activateOptions()</tt> before calling <tt>append()</tt>.
     */
    public FlumeAppender(){
    }

    /**
     * Sets the hostname and port. Even if these are passed the
     * <tt>activateOptions()</tt> function must be called before calling
     * <tt>append()</tt>, else <tt>append()</tt> will throw an Exception.
     * @param hostname The first hop where the client should connect to.
     * @param port The port to connect on the host.
     *
     */
    public FlumeAppender(String hostname, int port){
        this.hostname = hostname;
        this.port = port;
    }

    public static void main(String[] argv){
        DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        System.out.println(sdf.format(new Date()));
    }


    /**
     * Append the LoggingEvent, to send to the first Flume hop.
     * @param event The LoggingEvent to be appended to the flume.
     * @throws org.apache.flume.FlumeException if the appender was closed,
     * or the hostname and port were not setup, there was a timeout, or there
     * was a connection error.
     */

    @Override
    public synchronized void append(LoggingEvent event) throws FlumeException{
        if (connector!=null && connector.connecting){
            //LogLog.debug("rpcClient#" + rpcReconnectTimes.get() + " is connecting.");
            return ;
        }
        //client created first time append is called.
        Map<String, String> hdrs = new HashMap<String, String>();
        String app = event.getProperty("application");
        if (app==null){
            app = application;
        }
        hdrs.put("flume.client.log4j.application", app);
        hdrs.put("flume.client.log4j.clientIP", clientIP);
        hdrs.put(Log4jAvroHeaders.LOGGER_NAME.toString(), event.getLoggerName());
        hdrs.put(Log4jAvroHeaders.TIMESTAMP.toString(), String.valueOf(event.timeStamp));
        hdrs.put("flume.client.log4j.date", DATE_FORMAT.format(new Date(event.timeStamp)));

        //To get the level back simply use
        //LoggerEvent.toLevel(hdrs.get(Integer.parseInt(
        //Log4jAvroHeaders.LOG_LEVEL.toString()))
        hdrs.put(Log4jAvroHeaders.LOG_LEVEL.toString(), String.valueOf(event.getLevel().toInt()));
        hdrs.put("flume.client.log4j.level", String.valueOf(event.getLevel().toString()));
        hdrs.putAll(event.getProperties());

        Event flumeEvent;
        Object message = event.getMessage();

        if (message instanceof GenericRecord) {
            GenericRecord record = (GenericRecord) message;
            populateAvroHeaders(hdrs, record.getSchema(), message);
            flumeEvent = EventBuilder.withBody(serialize(record, record.getSchema()), hdrs);
        } else if (message instanceof SpecificRecord || avroReflectionEnabled) {
            Schema schema = ReflectData.get().getSchema(message.getClass());
            populateAvroHeaders(hdrs, schema, message);
            flumeEvent = EventBuilder.withBody(serialize(message, schema), hdrs);
        } else {
            hdrs.put(Log4jAvroHeaders.MESSAGE_ENCODING.toString(), "UTF8");
            StringBuffer msgBuffer = new StringBuffer(1024);
            msgBuffer.append(layout != null ? layout.format(event) : message.toString());
            if(layout.ignoresThrowable()) {
                String[] s = event.getThrowableStrRep();
                if (s != null) {
                    int len = s.length;
                    for(int i = 0; i < len; i++) {
                        msgBuffer.append(s[i]).append(Layout.LINE_SEP);
                    }
                }
            }
            String msg = msgBuffer.toString();
            flumeEvent = EventBuilder.withBody(msg, Charset.forName("UTF8"), hdrs);
        }

        try {
            if(rpcClient!=null && rpcClient.isActive()) {
                rpcClient.append(flumeEvent);
            }else{
                fireConnector();
            }
        } catch (EventDeliveryException e) {
//            LogLog.error("rpcClient.append EventDeliveryException", e);
            LogLog.warn("rpcClient#" + rpcReconnectTimes.get() + " Detected problem with EventDeliveryException: " + e.getMessage() + "--" + e.getCause() );
            fireConnector();
            if (unsafeMode) {
                return;
            }
            String msg = "Flume append() failed.";
            LogLog.error(msg);
            throw new FlumeException(msg + " Exception follows.", e);
        }
    }

    private Schema schema;
    private ByteArrayOutputStream out;
    private DatumWriter<Object> writer;
    private BinaryEncoder encoder;

    protected void populateAvroHeaders(Map<String, String> hdrs, Schema schema,
                                       Object message) {
        if (avroSchemaUrl != null) {
            hdrs.put(Log4jAvroHeaders.AVRO_SCHEMA_URL.toString(), avroSchemaUrl);
            return;
        }
        LogLog.warn("Cannot find ID for schema. Adding header for schema, " +
                "which may be inefficient. Consider setting up an Avro Schema Cache.");
        hdrs.put(Log4jAvroHeaders.AVRO_SCHEMA_LITERAL.toString(), schema.toString());
    }

    private byte[] serialize(Object datum, Schema datumSchema) throws FlumeException {
        if (schema == null || !datumSchema.equals(schema)) {
            schema = datumSchema;
            out = new ByteArrayOutputStream();
            writer = new ReflectDatumWriter<Object>(schema);
            encoder = EncoderFactory.get().binaryEncoder(out, null);
        }
        out.reset();
        try {
            writer.write(datum, encoder);
            encoder.flush();
            return out.toByteArray();
        } catch (IOException e) {
            throw new FlumeException(e);
        }
    }

    //This function should be synchronized to make sure one thread
    //does not close an appender another thread is using, and hence risking
    //a null pointer exception.
    /**
     * Closes underlying client.
     * If <tt>append()</tt> is called after this function is called,
     * it will throw an exception.
     * @throws org.apache.flume.FlumeException if errors occur during close
     */
    @Override
    public synchronized void close() throws FlumeException {
        try{
            if(connector != null) {
                connector.interrupt = true;
                connector = null;
            }
        }catch (Exception e){
            LogLog.error("connector.interrupt", e);
        }
        // Any append calls after this will result in an Exception.
        if (rpcClient != null) {
            try {
                LogLog.warn("appender#close close... rpcClient#" + rpcReconnectTimes.get());
                rpcClient.close();
            } catch (FlumeException ex) {
                LogLog.error("Error while trying to close RpcClient#" + rpcReconnectTimes.get(), ex);
                if (unsafeMode) {
                    return;
                }
                throw ex;
            } finally {
                rpcClient = null;
            }
        } else {
            if(unsafeMode) {
                return;
            }
            String errorMsg = "flume Appender already closed!";
            LogLog.error(errorMsg);
            throw new FlumeException(errorMsg);
        }
    }

    @Override
    public boolean requiresLayout() {
        // This method is named quite incorrectly in the interface. It should
        // probably be called canUseLayout or something. According to the docs,
        // even if the appender can work without a layout, if it can work with one,
        // this method must return true.
        return true;
    }

    /**
     * Set the first flume hop hostname.
     * @param hostname The first hop where the client should connect to.
     */
    public void setHostname(String hostname){
        this.hostname = hostname;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    /**
     * Set the port on the hostname to connect to.
     * @param port The port to connect on the host.
     */
    public void setPort(int port){
        this.port = port;
    }

    public void setUnsafeMode(boolean unsafeMode) {
        this.unsafeMode = unsafeMode;
    }

    public boolean getUnsafeMode() {
        return unsafeMode;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public long getTimeout() {
        return this.timeout;
    }

    public void setAvroReflectionEnabled(boolean avroReflectionEnabled) {
        this.avroReflectionEnabled = avroReflectionEnabled;
    }

    public void setAvroSchemaUrl(String avroSchemaUrl) {
        this.avroSchemaUrl = avroSchemaUrl;
    }

    public void setReconnectionDelay(long reconnectionDelay) {
        this.reconnectionDelay = reconnectionDelay;
    }

    public void setMaxIoWorkers(long maxIoWorkers) {
        this.maxIoWorkers = maxIoWorkers;
    }

    private Properties props = new Properties();
    @Override
    public void activateOptions(){
        DATE_FORMAT = new SimpleDateFormat(dateFormat);
        props.setProperty(RpcClientConfigurationConstants.CONFIG_HOSTS, "h1");
        props.setProperty(RpcClientConfigurationConstants.CONFIG_HOSTS_PREFIX + "h1", hostname + ":" + port);
        props.setProperty(RpcClientConfigurationConstants.CONFIG_CONNECT_TIMEOUT, String.valueOf(timeout));
        props.setProperty(RpcClientConfigurationConstants.CONFIG_REQUEST_TIMEOUT, String.valueOf(timeout));
        props.setProperty(RpcClientConfigurationConstants.MAX_IO_WORKERS, String.valueOf(maxIoWorkers));
        if (layout != null) {
            layout.activateOptions();
        }
        try {
            InetAddress local = InetAddress.getLocalHost();
            clientIP = local.getHostAddress();
            if (localPort!=null && localPort > 1024) {
//                props.setProperty("localAddress", new InetSocketAddress(local, localPort)));
            }
        }catch (Exception e){
            clientIP = "unknown";
        }
        fireConnector();
    }

    /**
     * Make it easy to reconnect on failure
     * @throws org.apache.flume.FlumeException
     */
    Lock connectLock = new ReentrantLock();
    AtomicInteger rpcCloseErrorTimes = new AtomicInteger(0);
    private void reconnect() throws Exception {
        if (rpcClient!=null && rpcClient.isActive()) {
            LogLog.warn("reconnect: rpcClient#" + rpcReconnectTimes.get() + " is already connected.");
            return ;
        }
        try {
            connectLock.tryLock(timeout, TimeUnit.MICROSECONDS);
            try {
                if (rpcClient != null) {
                    LogLog.warn("close... rpcClient#" + rpcReconnectTimes.get());
                    rpcClient.close();
                }
            } catch (Exception e) {
                LogLog.error("", e);
                if(rpcCloseErrorTimes.incrementAndGet() >= 3){
                    LogLog.error("force close rpcClient#"+  rpcReconnectTimes.get() +" after 3 times try close rpcClient error.");
                    rpcCloseErrorTimes.set(0);
                    rpcClient = null;
                }
            }
            try {
                rpcClient = RpcClientFactory.getInstance(props);
                long reconnectTimes = rpcReconnectTimes.incrementAndGet();
                LogLog.warn("connected to " + hostname + ":" + port + " rpcClient#" + reconnectTimes);
            } catch (FlumeException e) {
                String errorMsg = "RPC client creation failed! " + e.getMessage();
                LogLog.error(errorMsg);
            } catch (Exception e) {
                LogLog.error("", e);
            }
            connectLock.unlock();
        }catch (InterruptedException e){
            LogLog.error("", e);
        }
    }

    Lock lock = new ReentrantLock();

    void fireConnector() {
        if(connector != null) {
            return;
        }
        lock.lock();
        if (connector == null) {
            LogLog.warn("Starting a new connector thread. " + this);
            connector = new Connector();
            connector.setDaemon(true);
            connector.setName("FlumeAppenderConnector@" + Integer.toHexString(connector.hashCode()));
            connector.setPriority(Thread.MIN_PRIORITY);
            connector.start();
        }
        lock.unlock();
    }

    class Connector extends Thread {
        private volatile boolean interrupt = false;
        private volatile boolean connecting = false;
        public void run() {
            while (!interrupt) {
                try {
                    sleep(reconnectionDelay);
                    if (rpcClient!=null && rpcClient.isActive()) {
                        continue;
                    }
                    connecting = true;
                    LogLog.debug("Attempting connecting to " + hostname + ":" + port);
                    FlumeAppender.this.reconnect();
                }catch (InterruptedException e) {
                    LogLog.warn("Connector interrupted. Leaving loop.");
                    connector = null;
                    break;
                } catch (Exception e) {
                    LogLog.error("Remote host " + hostname + " ", e);
                }
                connecting = false;
            }
        }
    }
    
}
