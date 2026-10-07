package com.pigeon.boitenoire.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import com.pigeon.boitenoire.dto.ErrorBreakdownResponse;
import com.pigeon.boitenoire.dto.FunnelResponse;
import com.pigeon.boitenoire.dto.FunnelStepResponse;
import com.pigeon.boitenoire.dto.LatencyResponse;
import com.pigeon.boitenoire.dto.TopUserResponse;
import com.pigeon.boitenoire.exception.InvalidRequestException;

@Service
public class AnalyticsService {

    private MongoTemplate mongoTemplate;

    public AnalyticsService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<TopUserResponse> topUsers(LocalDate from, LocalDate to, int limit) {
        if (from.isAfter(to)) {
            throw new InvalidRequestException("Parameter 'from' (" + from + ") must not be after parameter 'to' (" + to + ")");
        }
        String dateFrom = from + "T00:00:00Z";
        String dateTo = to.plusDays(1) + "T00:00:00Z";

        String pipeline = """
                [
                  { $match: { timestamp: { $gte: ISODate("DATE_FROM"), $lt: ISODate("DATE_TO") } } },
                  { $group: { _id: "$userId", eventCount: { $sum: 1 } } },
                  { $sort: { eventCount: -1, _id: 1 } },
                  { $limit: LIMIT },
                  { $lookup: { from: "users", localField: "_id", foreignField: "_id", as: "user" } },
                  { $unwind: "$user" },
                  { $project: { eventCount: 1, name: "$user.name", email: "$user.email" } }
                ]
                """;
        pipeline = pipeline.replace("DATE_FROM", dateFrom);
        pipeline = pipeline.replace("DATE_TO", dateTo);
        pipeline = pipeline.replace("LIMIT", String.valueOf(limit));

        List<Document> stages = Document.parse("{ stages: " + pipeline + " }").getList("stages", Document.class);

        List<TopUserResponse> result = new ArrayList<>();
        for (Document doc : mongoTemplate.getCollection("events").aggregate(stages)) {
            ObjectId userId = (ObjectId) doc.get("_id");
            String name = doc.getString("name");
            String email = doc.getString("email");
            long eventCount = ((Number) doc.get("eventCount")).longValue();
            result.add(new TopUserResponse(userId.toHexString(), name, email, eventCount));
        }
        return result;
    }

    public List<ErrorBreakdownResponse> errors(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new InvalidRequestException("Parameter 'from' (" + from + ") must not be after parameter 'to' (" + to + ")");
        }
        String dateFrom = from + "T00:00:00Z";
        String dateTo = to.plusDays(1) + "T00:00:00Z";

        String pipeline = """
                [
                  { $match: { type: "ERROR", timestamp: { $gte: ISODate("DATE_FROM"), $lt: ISODate("DATE_TO") } } },
                  { $group: {
                      _id: {
                        day: { $dateToString: { format: "%Y-%m-%d", date: "$timestamp", timezone: "UTC" } },
                        service: "$payload.service",
                        message: "$payload.message"
                      },
                      count: { $sum: 1 }
                  } },
                  { $sort: { "_id.day": 1, count: -1, "_id.service": 1, "_id.message": 1 } }
                ]
                """;
        pipeline = pipeline.replace("DATE_FROM", dateFrom);
        pipeline = pipeline.replace("DATE_TO", dateTo);

        List<Document> stages = Document.parse("{ stages: " + pipeline + " }").getList("stages", Document.class);

        List<ErrorBreakdownResponse> result = new ArrayList<>();
        for (Document doc : mongoTemplate.getCollection("events").aggregate(stages)) {
            Document id = (Document) doc.get("_id");
            String day = id.getString("day");
            String service = id.getString("service");
            String message = id.getString("message");
            long count = ((Number) doc.get("count")).longValue();
            result.add(new ErrorBreakdownResponse(day, service, message, count));
        }
        return result;
    }

    public List<LatencyResponse> latency(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new InvalidRequestException("Parameter 'from' (" + from + ") must not be after parameter 'to' (" + to + ")");
        }
        String dateFrom = from + "T00:00:00Z";
        String dateTo = to.plusDays(1) + "T00:00:00Z";

        String pipeline = """
                [
                  { $match: { type: "API_CALL", timestamp: { $gte: ISODate("DATE_FROM"), $lt: ISODate("DATE_TO") } } },
                  { $group: {
                      _id: { endpoint: "$payload.endpoint", method: "$payload.method" },
                      count: { $sum: 1 },
                      avgMs: { $avg: "$payload.durationMs" },
                      p95: { $percentile: { input: "$payload.durationMs", p: [0.95], method: "approximate" } },
                      maxMs: { $max: "$payload.durationMs" }
                  } },
                  { $project: { count: 1, avgMs: 1, maxMs: 1, p95Ms: { $arrayElemAt: ["$p95", 0] } } },
                  { $sort: { p95Ms: -1, "_id.endpoint": 1, "_id.method": 1 } }
                ]
                """;
        pipeline = pipeline.replace("DATE_FROM", dateFrom);
        pipeline = pipeline.replace("DATE_TO", dateTo);

        List<Document> stages = Document.parse("{ stages: " + pipeline + " }").getList("stages", Document.class);

        List<LatencyResponse> result = new ArrayList<>();
        for (Document doc : mongoTemplate.getCollection("events").aggregate(stages)) {
            Document id = (Document) doc.get("_id");
            String endpoint = id.getString("endpoint");
            String method = id.getString("method");
            long count = ((Number) doc.get("count")).longValue();
            double avgMs = ((Number) doc.get("avgMs")).doubleValue();
            double p95Ms = ((Number) doc.get("p95Ms")).doubleValue();
            long maxMs = ((Number) doc.get("maxMs")).longValue();
            avgMs = Math.round(avgMs * 10) / 10.0;
            p95Ms = Math.round(p95Ms * 10) / 10.0;
            result.add(new LatencyResponse(endpoint, method, count, avgMs, p95Ms, maxMs));
        }
        return result;
    }

    public FunnelResponse funnel(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new InvalidRequestException("Parameter 'from' (" + from + ") must not be after parameter 'to' (" + to + ")");
        }
        String dateFrom = from + "T00:00:00Z";
        String dateTo = to.plusDays(1) + "T00:00:00Z";

        String pipeline = """
                [
                  { $match: {
                      timestamp: { $gte: ISODate("DATE_FROM"), $lt: ISODate("DATE_TO") },
                      $or: [
                        { type: "LOGIN", "payload.success": true },
                        { type: "API_CALL", "payload.endpoint": "/messages", "payload.method": "POST" },
                        { type: "PAYMENT", "payload.status": "SUCCESS" }
                      ]
                  } },
                  { $group: {
                      _id: "$userId",
                      loginAt: { $min: { $cond: [ { $eq: ["$type", "LOGIN"] }, "$timestamp", null ] } },
                      messageAt: { $min: { $cond: [ { $eq: ["$type", "API_CALL"] }, "$timestamp", null ] } },
                      paymentAt: { $min: { $cond: [ { $eq: ["$type", "PAYMENT"] }, "$timestamp", null ] } }
                  } },
                  { $group: {
                      _id: null,
                      login: { $sum: { $cond: [
                        { $ne: ["$loginAt", null] },
                        1, 0 ] } },
                      message: { $sum: { $cond: [
                        { $and: [ { $ne: ["$loginAt", null] }, { $gt: ["$messageAt", "$loginAt"] } ] },
                        1, 0 ] } },
                      payment: { $sum: { $cond: [
                        { $and: [ { $ne: ["$loginAt", null] }, { $gt: ["$messageAt", "$loginAt"] }, { $gt: ["$paymentAt", "$messageAt"] } ] },
                        1, 0 ] } }
                  } }
                ]
                """;
        pipeline = pipeline.replace("DATE_FROM", dateFrom);
        pipeline = pipeline.replace("DATE_TO", dateTo);

        List<Document> stages = Document.parse("{ stages: " + pipeline + " }").getList("stages", Document.class);

        long login = 0;
        long message = 0;
        long payment = 0;
        for (Document doc : mongoTemplate.getCollection("events").aggregate(stages)) {
            login = ((Number) doc.get("login")).longValue();
            message = ((Number) doc.get("message")).longValue();
            payment = ((Number) doc.get("payment")).longValue();
        }

        double loginPct = 0;
        double messageFromLoginPct = 0;
        double paymentFromMessagePct = 0;
        double paymentFromLoginPct = 0;
        if (login > 0) {
            loginPct = 100.0;
            messageFromLoginPct = Math.round(100.0 * message / login * 100) / 100.0;
            paymentFromLoginPct = Math.round(100.0 * payment / login * 100) / 100.0;
        }
        if (message > 0) {
            paymentFromMessagePct = Math.round(100.0 * payment / message * 100) / 100.0;
        }

        List<FunnelStepResponse> steps = new ArrayList<>();
        steps.add(new FunnelStepResponse("LOGIN", login, loginPct, loginPct));
        steps.add(new FunnelStepResponse("POST_MESSAGE", message, messageFromLoginPct, messageFromLoginPct));
        steps.add(new FunnelStepResponse("PAYMENT_SUCCESS", payment, paymentFromMessagePct, paymentFromLoginPct));
        return new FunnelResponse(steps);
    }
}
