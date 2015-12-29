package org.apache.log4j.socket;

import org.apache.commons.cli.*;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.xml.DOMConfigurator;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by karl on 2015/8/25.
 */
public class Log4jServer implements Log4jServerMBean {
    private static Logger log = Logger.getLogger(Log4jServer.class);

    public static String CONFIG_PATH = "conf/";
    public static String LOG4J_XML = "log4j.xml";
    public static String LOG4J_PROPERTIES = "log4j.properties";

    private String name = "log4j-agent";

    @Override
    public String status() {
        return started.get()? "running" : "stoped";
    }

    public void loadLog4jConfig(String log4jConfigPath) throws FileNotFoundException{
        File file = null;
        if(log4jConfigPath!=null){
            file = new File(log4jConfigPath);
        }else {
            file = new File(CONFIG_PATH + LOG4J_XML);
            if (!file.exists()) {
                file = new File(CONFIG_PATH + LOG4J_PROPERTIES);
            }
            if(!file.exists()) {
                URL url = this.getClass().getResource("/" + LOG4J_XML);
                file = new File(url.getFile());
            }
        }
        if(file.exists()){
            log.info("loading log4j conf " + file.getAbsolutePath());
            String log4jConf = file.getName();
            if(log4jConf.endsWith(".xml")){
                DOMConfigurator.configureAndWatch(file.getAbsolutePath(), 60);
            }else if(log4jConf.endsWith(".properties")){
                PropertyConfigurator.configureAndWatch(file.getAbsolutePath(), 60);
            }
        }else{
            throw new FileNotFoundException(file.getAbsolutePath());
        }
    }

    public AtomicBoolean started = new AtomicBoolean(false);

    public void registerMBean(){
        AccessController.doPrivileged(new PrivilegedAction<Object>() {
            @Override
            public Object run() {
                return null;
            }
        });
    }

    public void start(String log4jFile, Integer port) throws Exception{
        if(!started.compareAndSet(false, true)){
            System.out.println(name + " already started");
           return;
        }
        try {
            CountDownLatch latch = new CountDownLatch(1);
            Log4jAcceptor log4jAcceptor = new Log4jAcceptor(latch);

            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            ObjectName objectName = new ObjectName(this.getClass().getPackage().getName() + ":type=Log4jServer,id=Log4jServer");

            RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
            String pid = runtimeMXBean.getName().contains("@") ? runtimeMXBean.getName().substring(0, runtimeMXBean.getName().indexOf("@")) : runtimeMXBean.getName() + "\n";
            File file = new File("PID");
            if(file.exists())
                file.delete();
            file.createNewFile();
            FileOutputStream fout = new FileOutputStream(file);
            FileChannel channel = fout.getChannel();
            FileLock fileLock = channel.tryLock();

            if (fileLock != null) {
                log.info(name + " write pid " + pid + " to " + file.getAbsolutePath());
                fout.write(pid.getBytes());
                if (!mbs.isRegistered(objectName)) {
                    if(port!=null && port>1024){
                        log4jAcceptor.setPort(port);
                    }
                    mbs.registerMBean(log4jServer, objectName);
                    loadLog4jConfig(log4jFile);
                    log4jAcceptor.start();
                    latch.await();
                    log.info("============= exit ===============");
                } else {
                    log.info(name + " is running");
                }
                fileLock.release();
                fileLock.close();
            }else{
                System.out.println(name + " already started at " + log4jAcceptor.getPort());
            }
            channel.close();
            fout.close();
        }finally {
            started.compareAndSet(true, false);
        }
    }

    private static Log4jServer log4jServer = new Log4jServer();

    public static void main(String[] args) throws Exception{
        Options options = new Options();
        options.addOption("h", "help", false, "help");
        options.addOption("f", "file", true, "log4j config file");
        options.addOption("p", "port", true, "listen port");
        options.addOption("n", "name", true, "app name");
        HelpFormatter formatter = new HelpFormatter();

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);
        String log4jFile = cmd.getOptionValue("f");
        String name = cmd.getOptionValue("n");
        if(name!=null) {
            log4jServer.name = name;
        }
        String p = cmd.getOptionValue("p");
        Integer port = (p!=null && !p.trim().isEmpty())? Integer.parseInt(p) : null;
        Set<String> argSet = new HashSet<String>();
        argSet.addAll(cmd.getArgList());
        if(argSet.contains("start")){
            log4jServer.start(log4jFile, port);
        }else{
            formatter.printHelp("start -f conf/log4j.xml -p 4561 \n", options, true);
        }
    }

}
