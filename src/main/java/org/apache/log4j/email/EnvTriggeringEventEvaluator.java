package org.apache.log4j.email;

import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.TriggeringEventEvaluator;

/**
 * Created by karl on 2015/7/27.
 */
public class EnvTriggeringEventEvaluator implements TriggeringEventEvaluator {

    private boolean product = false;//是否是线上环境
    private String env = "";

    public void setProduct(boolean product) {
        this.product = product;
    }

    public void setEnv(String env) {
        this.env = env;
        if(this.env!=null && "www".equalsIgnoreCase(this.env)){
            this.product = true;
        }
    }

    @Override
    public boolean isTriggeringEvent(LoggingEvent event) {
        return product;
    }

}
