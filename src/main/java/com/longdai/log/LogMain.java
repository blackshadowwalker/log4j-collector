package com.longdai.log;

import ch.qos.logback.classic.net.SimpleSocketServer;

/**
 * Created by karl on 2016/3/24.
 */
public class LogMain {
    public static void main(String[] args) throws Exception {
        System.out.println("staring...");
        SimpleSocketServer.main(new String[]{"4562", "/data/conf/logback.xml"});
        System.out.println("running...");
    }
}
