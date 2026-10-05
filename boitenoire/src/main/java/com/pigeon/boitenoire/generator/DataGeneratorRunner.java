package com.pigeon.boitenoire.generator;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import com.pigeon.boitenoire.model.Event;
import com.pigeon.boitenoire.model.EventType;
import com.pigeon.boitenoire.model.User;

@Component
@Profile("generator")
public class DataGeneratorRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataGeneratorRunner.class);
    private static final int BATCH_SIZE = 1000;

    private final MongoTemplate mongoTemplate;
    private final long seed;
    private final int userCount;
    private final int eventCount;
    private final String referenceDate;

    public DataGeneratorRunner(
            MongoTemplate mongoTemplate,
            @Value("${generator.seed:42}") long seed,
            @Value("${generator.user-count:2000}") int userCount,
            @Value("${generator.event-count:150000}") int eventCount,
            @Value("${generator.reference-date:}") String referenceDate) {
        this.mongoTemplate = mongoTemplate;
        this.seed = seed;
        this.userCount = userCount;
        this.eventCount = eventCount;
        this.referenceDate = referenceDate;
    }

    @Override
    public void run(String... args) {
        LocalDate endExclusive = referenceDate.isBlank() ? LocalDate.now(ZoneOffset.UTC) : LocalDate.parse(referenceDate);
        Random random = new Random(seed);
        ActivityCalendar calendar = new ActivityCalendar(endExclusive, random);
        UserGenerator userGenerator = new UserGenerator(random, calendar);
        EventGenerator eventGenerator = new EventGenerator(random, calendar);

        log.info("Generating data (seed={}, period {} -> {}, UTC)", seed, calendar.start(), calendar.end());

        mongoTemplate.dropCollection(Event.class);
        mongoTemplate.dropCollection(User.class);
        log.info("Collections 'users' and 'events' dropped");

        List<User> users = userGenerator.generate(userCount);
        insertInBatches(users, User.class, "users");

        List<Event> events = eventGenerator.generate(users, eventCount);
        insertInBatches(events, Event.class, "events");

        printSummary();
    }

    private <T> void insertInBatches(List<T> documents, Class<T> type, String label) {
        int total = documents.size();
        int nextReport = 10;
        for (int from = 0; from < total; from += BATCH_SIZE) {
            int to = Math.min(from + BATCH_SIZE, total);
            mongoTemplate.insert(documents.subList(from, to), type);
            int percent = (int) (100L * to / total);
            if (percent >= nextReport || to == total) {
                log.info("Inserting {}: {}/{} ({}%)", label, to, total, percent);
                nextReport = (percent / 10 + 1) * 10;
            }
        }
    }

    private void printSummary() {
        log.info("===== Summary =====");
        log.info("users : {}", mongoTemplate.count(new Query(), User.class));
        log.info("events: {}", mongoTemplate.count(new Query(), Event.class));
        for (EventType type : EventType.values()) {
            long count = mongoTemplate.count(new Query(Criteria.where("type").is(type.name())), Event.class);
            log.info("  {} : {}", type, count);
        }
    }
}
