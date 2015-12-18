package com.log4j.socket;

import org.apache.log4j.Logger;

import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by karl on 2015/8/25.
 */
public class Log4jAcceptor extends Thread{
    private static Logger log = Logger.getLogger(Log4jAcceptor.class);

    public final static int DEFAULT_PORT = 4560;
    private int port = DEFAULT_PORT;
    private CountDownLatch latch;
    private ServerSocket serverSocket;
    private AtomicInteger count = new AtomicInteger(0);

    public void setPort(int port) {
        this.port = port;
    }

    public int getPort() {
        return port;
    }

    public Log4jAcceptor(CountDownLatch latch){
        this.latch = latch;
    }

    private transient boolean started = false;
    public synchronized void start(){
        if(started){
            return ;
        }
        this.setName("Log4jAcceptorThread");
        this.setDaemon(true);
        try {
            serverSocket = new ServerSocket(port);
        }catch (BindException e){
            log.error("端口被占用", e);
            return ;
        }catch (Exception e){
            throw new RuntimeException(e);
        }
        super.start();
        started = true;
    }

    public void run(){
        try {
            log.info(this.getName() + " started at " + InetAddress.getLocalHost().getHostAddress() + ":"+ port);
            while (true) {
                Socket client = serverSocket.accept();
                int sumClient = count.incrementAndGet();
                Log4jClient log4jClient = new Log4jClient(client);
                log.info("#"+sumClient+" accepted client connection " + log4jClient.getClientHost());
                log4jClient.setName("log4jClient#" + sumClient +"-" + log4jClient.getClientHost());
                Thread t = new Thread(log4jClient);
                t.setName(log4jClient.getName());
                t.setDaemon(true);
                t.start();
            }
        }catch (Exception e){
            log.error("Log4jAcceptor#run error", e);
        }
        if(this.latch!=null){
            this.latch.countDown();
        }
        started = false;
    }

}
