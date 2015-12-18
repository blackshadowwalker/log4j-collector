package com.log4j.email;

import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.TriggeringEventEvaluator;


/**
 * Created by karl on 2015/8/6.
 */
public class EmailEventTriggering implements TriggeringEventEvaluator {

    private Level level;
    private String env;

    public void setLevel(Level level) {
        this.level = level;
    }

    public void setEnv(String env){
        this.env = env;
    }

    @Override
    public boolean isTriggeringEvent(LoggingEvent event) {
        if(level!=null && "www".equalsIgnoreCase(this.env) ) {
            return event.getLevel().equals(level);
        }
        return false;
    }

}
