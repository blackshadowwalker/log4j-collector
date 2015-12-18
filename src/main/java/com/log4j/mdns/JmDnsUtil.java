package com.log4j.mdns;

import org.apache.log4j.net.SocketAppender;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import javax.jmdns.impl.JmDNSImpl;
import javax.jmdns.impl.ServiceInfoImpl;
import java.net.InetAddress;

/**
 * Created by karl on 2015/11/6.
 */
public class JmDnsUtil {

    public static void main(String[] argv) throws Exception{
        JmDNS jmDNS = new JmDNSImpl(InetAddress.getLocalHost(), "karl");
        jmDNS.addServiceTypeListener(new DiscoverServices.SampleListener());
        jmDNS.addServiceListener(SocketAppender.ZONE, new DiscoverServices.SampleListener());
        //String type, String name, String subtype, int port, int weight, int priority, boolean persistent, String text
//        ServiceInfo serviceInfo = new ServiceInfoImpl("log4j", "log4j-agent", "log4j", 4561, 1, 1, true, "log4j-agent-text");
//        jmDNS.registerService(serviceInfo);
//        System.out.println(jmDNS);
    }

}
