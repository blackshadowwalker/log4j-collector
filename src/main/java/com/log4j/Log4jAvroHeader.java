package com.log4j;

import java.util.Locale;

/**
 * Created by karl on 2015/8/24.
 */
public enum Log4jAvroHeader {
    SOURCE("flume.client.log4j.source"),
    HOST("flume.client.log4j.host"),
    OTHER("flume.client.log4j.logger.other"),

    TIMESTAMP("flume.client.log4j.timestamp"),
    LOGGER_NAME("flume.client.log4j.logger.name"),
    LOGGER_LEVEL("flume.client.log4j.log.level"),
    LOGGER_NDC("flume.client.log4j.log.ndc"),
    LOGGER_THREAD_NAME("flume.client.log4j.log.threadName"),
    LOGGER_LOCATION("flume.client.log4j.log.location"),
    LOGGER_FQCN("flume.client.log4j.log.fqcn"),

    MESSAGE_ENCODING("flume.client.log4j.message.encoding"),
    AVRO_SCHEMA_LITERAL("flume.avro.schema.literal"),
    AVRO_SCHEMA_URL("flume.avro.schema.url");

    private String headerName;
    private Log4jAvroHeader(String headerName){
        this.headerName = headerName;
    }

    public String getName(){
        return headerName;
    }

    public String toString(){
        return getName();
    }

    public static Log4jAvroHeader getByName(String headerName){
        Log4jAvroHeader hdrs = null;
        try{
            hdrs = Log4jAvroHeader.valueOf(headerName.toLowerCase(Locale.ENGLISH).trim());
        }
        catch(IllegalArgumentException e){
            hdrs = Log4jAvroHeader.OTHER;
        }
        return hdrs;
    }
}
