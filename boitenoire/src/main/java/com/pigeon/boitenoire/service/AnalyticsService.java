package com.pigeon.boitenoire.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import com.pigeon.boitenoire.dto.ErrorBreakdownResponse;
import com.pigeon.boitenoire.dto.FunnelResponse;
import com.pigeon.boitenoire.dto.FunnelStepResponse;
import com.pigeon.boitenoire.dto.LatencyResponse;
import com.pigeon.boitenoire.dto.TopUserResponse;
import com.pigeon.boitenoire.model.Event;
import com.pigeon.boitenoire.model.EventType;
import com.pigeon.boitenoire.model.User;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.limit;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.lookup;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.project;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.sort;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.unwind;



@Service
public class AnalyticsService {

    private final MongoTemplate mongoTemplate;
    private final String eventsCollection;
    private final String usersCollection;

    public AnalyticsService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
        this.eventsCollection = mongoTemplate.getCollectionName(Event.class);
        this.usersCollection = mongoTemplate.getCollectionName(User.class);
    }


    public List<TopUserResponse> topUsers(TimeRange range, int limit) {
        Aggregation pipeline = newAggregation(
                match(within(range)),
                Aggregation.group("userId").count().as("eventCount"),
                sort(Sort.by(Sort.Direction.DESC, "eventCount").and(Sort.by(Sort.Direction.ASC, "_id"))),
                limit(limit),
                lookup(usersCollection, "_id", "_id", "user"),
                unwind("user"),
                project("eventCount").and("user.name").as("name").and("user.email").as("email"));

        return run(pipeline).stream()
                .map(doc -> new TopUserResponse(
                        ((ObjectId) doc.get("_id")).toHexString(),
                        doc.getString("name"),
                        doc.getString("email"),
                        number(doc, "eventCount").longValue()))
                .toList();
    }


    public List<ErrorBreakdownResponse> errors(TimeRange range) {
        Document groupKey = new Document("day", new Document("$dateToString",
                new Document("format", "%Y-%m-%d").append("date", "$timestamp").append("timezone", "UTC")))
                .append("service", "$payload.service")
                .append("message", "$payload.message");

        Aggregation pipeline = newAggregation(
                match(within(range, Criteria.where("type").is(EventType.ERROR.name()))),
                stage(new Document("$group", new Document("_id", groupKey).append("count", new Document("$sum", 1)))),
                sort(Sort.by(Sort.Direction.ASC, "_id.day")
                        .and(Sort.by(Sort.Direction.DESC, "count"))
                        .and(Sort.by(Sort.Direction.ASC, "_id.service"))
                        .and(Sort.by(Sort.Direction.ASC, "_id.message"))));

        return run(pipeline).stream()
                .map(doc -> {
                    Document key = doc.get("_id", Document.class);
                    return new ErrorBreakdownResponse(
                            key.getString("day"), key.getString("service"), key.getString("message"),
                            number(doc, "count").longValue());
                })
                .toList();
    }

    public List<LatencyResponse> latency(TimeRange range) {
        Document percentile = new Document("$percentile", new Document("input", "$payload.durationMs")
                .append("p", List.of(0.95))
                .append("method", "approximate"));
        Document group = new Document("_id", new Document("endpoint", "$payload.endpoint")
                .append("method", "$payload.method"))
                .append("count", new Document("$sum", 1))
                .append("avgMs", new Document("$avg", "$payload.durationMs"))
                .append("p95", percentile)
                .append("maxMs", new Document("$max", "$payload.durationMs"));
        Document projection = new Document("count", 1).append("avgMs", 1).append("maxMs", 1)
                .append("p95Ms", new Document("$arrayElemAt", List.of("$p95", 0)));

        Aggregation pipeline = newAggregation(
                match(within(range, Criteria.where("type").is(EventType.API_CALL.name()))),
                stage(new Document("$group", group)),
                stage(new Document("$project", projection)),
                sort(Sort.by(Sort.Direction.DESC, "p95Ms").and(Sort.by(Sort.Direction.ASC, "_id.endpoint"))
                        .and(Sort.by(Sort.Direction.ASC, "_id.method"))));

        return run(pipeline).stream()
                .map(doc -> {
                    Document key = doc.get("_id", Document.class);
                    return new LatencyResponse(
                            key.getString("endpoint"), key.getString("method"),
                            number(doc, "count").longValue(),
                            round(number(doc, "avgMs").doubleValue(), 1),
                            round(number(doc, "p95Ms").doubleValue(), 1),
                            number(doc, "maxMs").longValue());
                })
                .toList();
    }

    public FunnelResponse funnel(TimeRange range) {
        Criteria relevantEvents = new Criteria().orOperator(
                Criteria.where("type").is(EventType.LOGIN.name()).and("payload.success").is(true),
                Criteria.where("type").is(EventType.API_CALL.name())
                        .and("payload.endpoint").is("/messages").and("payload.method").is("POST"),
                Criteria.where("type").is(EventType.PAYMENT.name()).and("payload.status").is("SUCCESS"));

        Document firstDates = new Document("_id", "$userId")
                .append("loginAt", firstDateOf(EventType.LOGIN))
                .append("messageAt", firstDateOf(EventType.API_CALL))
                .append("paymentAt", firstDateOf(EventType.PAYMENT));

        Document reachedLogin = new Document("$ne", Arrays.asList("$loginAt", null));
        Document reachedMessage = new Document("$and", List.of(
                reachedLogin, new Document("$gt", List.of("$messageAt", "$loginAt"))));
        Document reachedPayment = new Document("$and", List.of(
                reachedMessage, new Document("$gt", List.of("$paymentAt", "$messageAt"))));
        Document counts = new Document("_id", null)
                .append("login", countIf(reachedLogin))
                .append("message", countIf(reachedMessage))
                .append("payment", countIf(reachedPayment));

        Aggregation pipeline = newAggregation(
                match(within(range, relevantEvents)),
                stage(new Document("$group", firstDates)),
                stage(new Document("$group", counts)));

        List<Document> result = run(pipeline);
        Document row = result.isEmpty() ? new Document() : result.get(0);
        long login = row.containsKey("login") ? number(row, "login").longValue() : 0;
        long message = row.containsKey("message") ? number(row, "message").longValue() : 0;
        long payment = row.containsKey("payment") ? number(row, "payment").longValue() : 0;

        return new FunnelResponse(List.of(
                step("LOGIN", login, login, login),
                step("POST_MESSAGE", message, login, login),
                step("PAYMENT_SUCCESS", payment, message, login)));
    }

    private static FunnelStepResponse step(String name, long users, long previous, long first) {
        return new FunnelStepResponse(name, users, percentage(users, previous), percentage(users, first));
    }

    private static double percentage(long part, long total) {
        return total == 0 ? 0 : round(100.0 * part / total, 2);
    }

    private static Document firstDateOf(EventType type) {
        return new Document("$min", new Document("$cond",
                Arrays.asList(new Document("$eq", List.of("$type", type.name())), "$timestamp", null)));
    }

    private static Document countIf(Document condition) {
        return new Document("$sum", new Document("$cond", List.of(condition, 1, 0)));
    }

    private static AggregationOperation stage(Document stage) {
        return context -> stage;
    }

    private static Criteria within(TimeRange range, Criteria... others) {
        List<Criteria> parts = new ArrayList<>();
        if (range.isBounded()) {
            parts.add(range.timestampCriteria());
        }
        parts.addAll(List.of(others));
        if (parts.isEmpty()) {
            return new Criteria();
        }
        return parts.size() == 1 ? parts.get(0) : new Criteria().andOperator(parts);
    }

    private List<Document> run(Aggregation pipeline) {
        return mongoTemplate.aggregate(pipeline, eventsCollection, Document.class).getMappedResults();
    }

    private static Number number(Map<String, Object> doc, String key) {
        Object value = doc.get(key);
        return value instanceof Number n ? n : 0;
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
