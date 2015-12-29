package org.apache.log4j.email;

import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.TriggeringEventEvaluator;

import java.util.Date;

/**
 * Created by karl on 2015/7/23.
 */
public class EmailTriggeringEventEvaluator implements TriggeringEventEvaluator {

    private final static long KB = 1024;
    private final static long MB = 1024 * KB;
    private long maxEvent = 512; // 事件数量
    private long maxBuffer = 1 * MB; //数据大小kb
    private long timeIntervalSec = 3600 * 24;
    private Date sendTimeDaily;

    private long curEvent = 0;

    public void setMaxEvent(long maxEvent) {
        this.maxEvent = maxEvent;
    }

    public void setMaxBuffer(long maxBufferkb) {
        this.maxBuffer = maxBufferkb;
    }

    @Override
    public boolean isTriggeringEvent(LoggingEvent event) {
        curEvent ++;
        if(curEvent == maxEvent){
            curEvent = 0;
            return true;
        }
        return false;
    }
}
