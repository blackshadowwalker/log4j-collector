package com.log4j.socket;

import org.apache.log4j.Logger;
import org.apache.log4j.MDC;
import org.apache.log4j.spi.LoggingEvent;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;
import java.net.SocketException;

/**
 * Created by karl on 2015/8/25.
 */
public class Log4jClient implements Runnable {
    private static Logger log = Logger.getLogger(Log4jClient.class);

    private ObjectInputStream ois;
    private Socket client;
    private String clientHost;
    private String name;

    public String getClientHost() {
        return clientHost;
    }

    public Log4jClient(Socket client) {
        try {
            this.client = client;
            this.ois = new ObjectInputStream(client.getInputStream());
            clientHost = client.getInetAddress().getHostAddress() + ":" + client.getPort();
        } catch (Exception e) {
            log.error("new Log4jClient(socket) error " + clientHost, e);
        }
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    private void close() {
        try {
            client.close();
        } catch (Exception e1) {
            log.error("socket client#close error " + clientHost, e1);
        }
    }

    @Override
    public void run() {
        while (!client.isClosed()) {
            try {
                if (ois == null) {
                    log.error("ois is null " + clientHost);
                    break;
                }
                LoggingEvent event = (LoggingEvent) ois.readObject();
                String application = event.getProperty("application");
                MDC.put("app", application);
                Logger loggerApp = Logger.getRootLogger().getLoggerRepository().getLogger(application);
                if (loggerApp != null) {
                    loggerApp.callAppenders(event);
                }
                Logger logger = Logger.getRootLogger().getLoggerRepository().getLogger(event.getLoggerName());
                if (logger != null) {
                    logger.callAppenders(event);
                } else {
                    log.callAppenders(event);
                }
            } catch (EOFException e) {
                log.error("EOFException " + clientHost, e);
                close();
            } catch (SocketException e) {
                log.error("SocketException Log4jClient#run error " + clientHost, e);
                close();
            } catch (IOException e) {
                log.error("IOException " + clientHost, e);
                close();
            } catch (NullPointerException e) {
                log.error(clientHost, e);
                break;
            } catch (Exception e) {
                log.error("Log4jClient#run error " + clientHost, e);
            }
        }
    }
}
