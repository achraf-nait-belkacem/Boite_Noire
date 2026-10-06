package com.pigeon.boitenoire.optimizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

import com.pigeon.boitenoire.model.Event;

@Component
@Profile("optimize")
public class IndexRunner implements CommandLineRunner {

    static final String INDEX_NAME = "type_1_timestamp_1";

    private static final Logger log = LoggerFactory.getLogger(IndexRunner.class);

    private final MongoTemplate mongoTemplate;

    public IndexRunner(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(String... args) {
        Index index = new Index()
                .on("type", Sort.Direction.ASC)
                .on("timestamp", Sort.Direction.ASC)
                .named(INDEX_NAME);

        String created = mongoTemplate.indexOps(Event.class).createIndex(index);
        log.info("Index '{}' is in place on 'events' (createIndex is idempotent)", created);

        mongoTemplate.indexOps(Event.class).getIndexInfo()
                .forEach(info -> log.info("  index {} -> {}", info.getName(), info.getIndexFields()));
    }
}
