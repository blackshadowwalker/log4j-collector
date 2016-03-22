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
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.spi.LoggingEvent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
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

    RpcClient rpcClient = null;

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

        //client created first time append is called.
        Map<String, String> hdrs = new HashMap<String, String>();
        hdrs.put("flume.client.log4j.application", application);
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
            String msg = layout != null ? layout.format(event) : message.toString();
            flumeEvent = EventBuilder.withBody(msg, Charset.forName("UTF8"), hdrs);
        }

        try {
            if(rpcClient!=null && rpcClient.isActive()) {
                rpcClient.append(flumeEvent);
            }else{
                fireConnector();
            }
        } catch (EventDeliveryException e) {
            LogLog.debug("Detected problem with EventDeliveryException: " + e.getCause());
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
                synchronized (reconnectObject) {
                    reconnectObject.notifyAll();
                }
                connector = null;
                reconnectObject = null;
            }
        }catch (Exception e){
            LogLog.error("connector.interrupt", e);
        }
        // Any append calls after this will result in an Exception.
        if (rpcClient != null) {
            try {
                LogLog.warn("appender#close close... rpcClient " + rpcClient);
                rpcClient.close();
            } catch (FlumeException ex) {
                LogLog.error("Error while trying to close RpcClient.", ex);
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


    private Properties props = new Properties();
    @Override
    public void activateOptions() throws FlumeException {
        DATE_FORMAT = new SimpleDateFormat(dateFormat);
        props.setProperty(RpcClientConfigurationConstants.CONFIG_HOSTS, "h1");
        props.setProperty(RpcClientConfigurationConstants.CONFIG_HOSTS_PREFIX + "h1", hostname + ":" + port);
        props.setProperty(RpcClientConfigurationConstants.CONFIG_CONNECT_TIMEOUT, String.valueOf(timeout));
        props.setProperty(RpcClientConfigurationConstants.CONFIG_REQUEST_TIMEOUT, String.valueOf(timeout));
        props.setProperty(RpcClientConfigurationConstants.MAX_IO_WORKERS, String.valueOf(maxIoWorkers));
        if (layout != null) {
            layout.activateOptions();
        }
        fireConnector();
    }

    /**
     * Make it easy to reconnect on failure
     * @throws org.apache.flume.FlumeException
     */
    private synchronized void reconnect() throws FlumeException {
        try{
            if (rpcClient!=null) {
                if(rpcClient.isActive()){
                    LogLog.debug("reconnect : rpc is actived.");
                    return ;
                }
                LogLog.warn("close... rpcClient " + rpcClient);
                rpcClient.close();
            }
        }catch (Exception e){
            LogLog.error("close rpcClient Error:", e);
        }
        rpcClient = null;
        try {
            rpcClient = RpcClientFactory.getInstance(props);
            LogLog.warn("connected to " + hostname + ":" + port + " rpcClient:"  + Integer.toHexString(rpcClient.hashCode()));
        } catch (FlumeException e) {
            String errormsg = "RPC client creation failed! " + e.getMessage();
            LogLog.error(errormsg);
            if (unsafeMode) {
                return;
            }
            throw e;
        }
    }

    Lock lock = new ReentrantLock();
    void fireConnector() {
        if(connector != null) {
            synchronized (reconnectObject) {
                reconnectObject.notifyAll();
            }
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

    volatile Object reconnectObject = new Object();
    class Connector extends Thread {
        private volatile boolean interrupt = false;
        public void run() {
            while (true) {
                try {
                    synchronized (reconnectObject) {
                        if (rpcClient!=null && rpcClient.isActive()) {
                            reconnectObject.wait(30000);
                        }
                        LogLog.debug("Attempting connecting to " + hostname + ":" + port);
                        reconnect();
                        if (rpcClient!=null && rpcClient.isActive()) {
                            reconnectObject.wait(30000);
                        }
                    }
                } catch (Exception e) {
                    LogLog.debug("Remote host " + hostname + " refused connection.");
                }
                try{
                    if(interrupt)
                        return;
                    sleep(reconnectionDelay);
                }catch (InterruptedException e) {
                    LogLog.warn("Connector interrupted. Leaving loop.");
                    connector = null;
                    return;
                }
            }
        }
    }
    
}
