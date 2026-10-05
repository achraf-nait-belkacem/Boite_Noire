package com.pigeon.boitenoire.config;

import jakarta.annotation.PostConstruct;

import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.stereotype.Component;

@Component
public class MongoConfig {

    private final MappingMongoConverter converter;

    public MongoConfig(MappingMongoConverter converter) {
        this.converter = converter;
    }

    @PostConstruct
    void removeTypeField() {
        converter.setTypeMapper(new DefaultMongoTypeMapper(null));
    }
}
