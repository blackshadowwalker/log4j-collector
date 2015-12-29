package org.apache.log4j.mdns;

import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import javax.jmdns.ServiceTypeListener;

/**
 * Created by karl on 2015/11/6.
 */
public class DiscoverServices {

    static class SampleListener implements ServiceListener, ServiceTypeListener {

        //serviceAdded 方法会自动返回搜索到服务的相关信息，比如一般都会带有ip地址、端口、serviceInfo信息，这些信息一般都是搜索到的注册的设备的信息，用这个方法就可以拿到设备的所有信息
        //有的时候这个信息ServiceInfo的值是null，这个几率不是很大，但是和手机的硬件设备有点关系（htc e1搜索不是很乐观、ASUS me302c和三星GT-I8160不错），看情况通常很少
        @Override
        public void serviceAdded(ServiceEvent event) {
            System.out.println("Service added   : " + event.getName() + "." + event.getType());
        }

        //移除某一个服务
        @Override
        public void serviceRemoved(ServiceEvent event) {
            System.out.println("Service removed : " + event.getName() + "." + event.getType());
        }

        @Override
        public void serviceResolved(ServiceEvent event) {//什么时候监听接口回调该方法
            System.out.println("Service resolved: " + event.getInfo());
        }

        //搜索局域网设备的type，该方法和ServiceAdded很像，但是这个方法不到serviceInfo，event的serviceInfo是为null
        @Override
        public void serviceTypeAdded(ServiceEvent event) {
            System.out.println(" serviceTypeAdded: " + event);
            ServiceInfo serviceInfo = event.getDNS().getServiceInfo(event.getType(), event.getName());
            if(serviceInfo!=null){
                System.out.println(serviceInfo.getInet4Address());
                System.out.println(serviceInfo.getNiceTextString());
            }

        }

        @Override
        public void subTypeForServiceTypeAdded(ServiceEvent event) {
            System.out.println(" subTypeForServiceTypeAdded: " + event.getInfo());

        }
    }
}
