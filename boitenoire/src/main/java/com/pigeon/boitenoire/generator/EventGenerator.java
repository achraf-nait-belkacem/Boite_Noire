package com.pigeon.boitenoire.generator;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.bson.types.ObjectId;

import com.pigeon.boitenoire.model.Event;
import com.pigeon.boitenoire.model.EventType;
import com.pigeon.boitenoire.model.User;

public class EventGenerator {

    private static final double MESSAGE_RATE = 0.60;
    private static final double PAYMENT_RATE = 0.25;
    private static final double ACTIVITY_EXPONENT = 0.8;
    private static final int HEAVY_USERS = 200;

    private record Endpoint(String method, String path, double weight, double medianMs, double sigma) {
        boolean isMessagePost() {
            return method.equals("POST") && path.equals("/messages");
        }
    }

    private record ErrorKind(String service, String message, String severity, double weight) {
    }

    private static final List<Endpoint> ENDPOINTS = List.of(
            new Endpoint("GET", "/messages", 30, 80, 0.6),
            new Endpoint("POST", "/messages", 18, 140, 0.7),
            new Endpoint("GET", "/conversations", 16, 110, 0.6),
            new Endpoint("GET", "/contacts", 10, 60, 0.5),
            new Endpoint("GET", "/notifications", 10, 50, 0.5),
            new Endpoint("GET", "/search", 7, 300, 1.0),
            new Endpoint("POST", "/attachments", 5, 450, 0.9),
            new Endpoint("POST", "/auth/refresh", 4, 40, 0.4));

    private static final List<ErrorKind> ERROR_KINDS = List.of(
            new ErrorKind("messaging", "Connection refused", "HIGH", 20),
            new ErrorKind("gateway", "Upstream timeout", "HIGH", 16),
            new ErrorKind("auth", "Invalid token signature", "MEDIUM", 14),
            new ErrorKind("storage", "Disk quota exceeded", "MEDIUM", 10),
            new ErrorKind("messaging", "Message queue backlog", "MEDIUM", 9),
            new ErrorKind("notification", "Push gateway rate limited", "LOW", 8),
            new ErrorKind("payment", "Payment provider unavailable", "CRITICAL", 7),
            new ErrorKind("search", "Index shard unavailable", "HIGH", 6),
            new ErrorKind("messaging", "Attachment virus scan failed", "LOW", 5),
            new ErrorKind("auth", "Session store unreachable", "CRITICAL", 3),
            new ErrorKind("storage", "Checksum mismatch", "CRITICAL", 1.5));

    private static final String[] DEVICES = { "mobile", "desktop", "tablet" };
    private static final double[] DEVICE_WEIGHTS = { 55, 35, 10 };
    private static final String[] PLANS = { "BASIC", "PRO", "BUSINESS" };
    private static final double[] PLAN_PRICES = { 4.99, 9.99, 29.99 };
    private static final double[] PLAN_WEIGHTS = { 50, 35, 15 };
    private static final String[] CHANNELS = { "push", "email", "sms" };
    private static final double[] CHANNEL_WEIGHTS = { 60, 30, 10 };
    private static final String[] NOTIFICATION_TITLES = {
            "New message", "New contact request", "Payment received", "Security alert",
            "Weekly summary", "Mention in a conversation", "Attachment ready" };

    private static final EventType[] EXTRA_TYPES = {
            EventType.API_CALL, EventType.NOTIFICATION, EventType.LOGIN, EventType.ERROR, EventType.PAYMENT };
    private static final double[] EXTRA_TYPE_WEIGHTS = { 52, 22, 15, 6, 5 };

    private final Random random;
    private final ActivityCalendar calendar;

    EventGenerator(Random random, ActivityCalendar calendar) {
        this.random = random;
        this.calendar = calendar;
    }

    public List<Event> generate(List<User> users, int targetEvents) {
        int userCount = users.size();

        List<Integer> roleOrder = shuffledIndexes(userCount);
        int messageCount = (int) Math.round(userCount * MESSAGE_RATE);
        int paymentCount = (int) Math.round(userCount * PAYMENT_RATE);
        boolean[] messenger = new boolean[userCount];
        boolean[] payer = new boolean[userCount];
        for (int i = 0; i < messageCount; i++) {
            messenger[roleOrder.get(i)] = true;
        }
        for (int i = 0; i < paymentCount; i++) {
            payer[roleOrder.get(i)] = true;
        }

        double[] activity = activityWeights(users);
        double activitySum = 0;
        for (double w : activity) {
            activitySum += w;
        }
        int extraTotal = Math.max(0, targetEvents - userCount - messageCount - paymentCount);

        List<Event> events = new ArrayList<>(targetEvents + 1000);
        for (int i = 0; i < userCount; i++) {
            User user = users.get(i);
            int planIndex = weightedIndex(PLAN_WEIGHTS);
            String ipPrefix = (11 + random.nextInt(200)) + "." + random.nextInt(256) + ".";

            Instant firstLogin = user.createdAt().isBefore(calendar.start())
                    ? calendar.sample(calendar.start(), calendar.start().plus(Duration.ofDays(10)), random)
                    : user.createdAt().plus(Duration.ofMinutes(1 + random.nextInt(30)));
            Instant firstMessage = firstLogin.plus(Duration.ofMinutes(1 + random.nextInt(180)));
            Instant firstPayment = calendar.sample(
                    firstMessage.plus(Duration.ofHours(1)), firstMessage.plus(Duration.ofDays(14)), random);

            events.add(login(user, firstLogin, ipPrefix, true));
            if (messenger[i]) {
                events.add(apiCall(user, firstMessage, ENDPOINTS.get(1), true));
            }
            if (payer[i]) {
                events.add(payment(user, firstPayment, planIndex, "SUCCESS"));
            }

            double expected = extraTotal * activity[i] / activitySum;
            int extraCount = (int) expected + (random.nextDouble() < expected - (int) expected ? 1 : 0);
            for (int e = 0; e < extraCount; e++) {
                EventType type = EXTRA_TYPES[weightedIndex(EXTRA_TYPE_WEIGHTS)];
                if (type == EventType.PAYMENT && !payer[i]) {
                    type = EventType.API_CALL;
                }
                switch (type) {
                    case LOGIN -> events.add(login(user, after(firstLogin), ipPrefix, random.nextDouble() < 0.92));
                    case ERROR -> events.add(error(user, after(firstLogin)));
                    case NOTIFICATION -> events.add(notification(user, after(firstLogin)));
                    case PAYMENT -> events.add(payment(user, after(firstPayment), planIndex,
                            random.nextDouble() < 0.9 ? "SUCCESS" : "FAILED"));
                    default -> {
                        Endpoint endpoint = pickEndpoint(messenger[i]);
                        Instant bound = endpoint.isMessagePost() ? firstMessage : firstLogin;
                        events.add(apiCall(user, after(bound), endpoint, false));
                    }
                }
            }
        }

        events.sort(Comparator.comparing(Event::timestamp));
        return events;
    }

    private Instant after(Instant bound) {
        return calendar.sample(bound.plusSeconds(1), calendar.end(), random);
    }

    private double[] activityWeights(List<User> users) {
        List<Integer> existing = new ArrayList<>();
        List<Integer> others = new ArrayList<>();
        for (int i : shuffledIndexes(users.size())) {
            if (users.get(i).createdAt().isBefore(calendar.start()) && existing.size() < HEAVY_USERS) {
                existing.add(i);
            } else {
                others.add(i);
            }
        }
        List<Integer> ranking = new ArrayList<>(existing);
        ranking.addAll(others);

        double[] weights = new double[users.size()];
        for (int rank = 0; rank < ranking.size(); rank++) {
            weights[ranking.get(rank)] = Math.pow(rank + 1, -ACTIVITY_EXPONENT);
        }
        return weights;
    }

    private List<Integer> shuffledIndexes(int size) {
        List<Integer> indexes = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            indexes.add(i);
        }
        Collections.shuffle(indexes, random);
        return indexes;
    }

    private Event login(User user, Instant timestamp, String ipPrefix, boolean success) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ip", ipPrefix + random.nextInt(256) + "." + (1 + random.nextInt(254)));
        payload.put("device", DEVICES[weightedIndex(DEVICE_WEIGHTS)]);
        payload.put("success", success);
        return event(EventType.LOGIN, user, timestamp, payload);
    }

    private Event payment(User user, Instant timestamp, int planIndex, String status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("amount", PLAN_PRICES[planIndex]);
        payload.put("currency", "EUR");
        payload.put("plan", PLANS[planIndex]);
        payload.put("status", status);
        return event(EventType.PAYMENT, user, timestamp, payload);
    }

    private Event error(User user, Instant timestamp) {
        double total = ERROR_KINDS.stream().mapToDouble(ErrorKind::weight).sum();
        double target = random.nextDouble() * total;
        ErrorKind kind = ERROR_KINDS.get(ERROR_KINDS.size() - 1);
        for (ErrorKind candidate : ERROR_KINDS) {
            target -= candidate.weight();
            if (target < 0) {
                kind = candidate;
                break;
            }
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("service", kind.service());
        payload.put("message", kind.message());
        payload.put("severity", kind.severity());
        return event(EventType.ERROR, user, timestamp, payload);
    }

    private Event apiCall(User user, Instant timestamp, Endpoint endpoint, boolean forceOk) {
        int statusCode = forceOk ? 200 : pickStatusCode();
        double duration = endpoint.medianMs() * Math.exp(endpoint.sigma() * random.nextGaussian());
        if (random.nextDouble() < 0.02) {
            duration *= 4 + 11 * random.nextDouble();
        }
        if (statusCode == 504) {
            duration = 10_000 + random.nextInt(20_000);
        } else if (statusCode >= 400 && statusCode < 500) {
            duration = Math.min(duration, endpoint.medianMs() * 0.5);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("endpoint", endpoint.path());
        payload.put("method", endpoint.method());
        payload.put("durationMs", (int) Math.max(1, Math.min(30_000, Math.round(duration))));
        payload.put("statusCode", statusCode);
        return event(EventType.API_CALL, user, timestamp, payload);
    }

    private Event notification(User user, Instant timestamp) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("channel", CHANNELS[weightedIndex(CHANNEL_WEIGHTS)]);
        payload.put("title", NOTIFICATION_TITLES[random.nextInt(NOTIFICATION_TITLES.length)]);
        payload.put("read", random.nextDouble() < 0.65);
        return event(EventType.NOTIFICATION, user, timestamp, payload);
    }

    private Event event(EventType type, User user, Instant timestamp, Map<String, Object> payload) {
        ObjectId id = ObjectIds.at(timestamp, random);
        return new Event(id, type, user.id(), timestamp, payload);
    }

    private Endpoint pickEndpoint(boolean canPostMessages) {
        while (true) {
            double total = ENDPOINTS.stream().mapToDouble(Endpoint::weight).sum();
            double target = random.nextDouble() * total;
            for (Endpoint endpoint : ENDPOINTS) {
                target -= endpoint.weight();
                if (target < 0) {
                    if (canPostMessages || !endpoint.isMessagePost()) {
                        return endpoint;
                    }
                    break;
                }
            }
        }
    }

    private int pickStatusCode() {
        double r = random.nextDouble();
        if (r < 0.935) {
            return 200;
        }
        if (r < 0.98) {
            int[] clientErrors = { 400, 401, 403, 404, 429 };
            return clientErrors[random.nextInt(clientErrors.length)];
        }
        int[] serverErrors = { 500, 502, 503, 504 };
        return serverErrors[random.nextInt(serverErrors.length)];
    }

    private int weightedIndex(double[] weights) {
        double total = 0;
        for (double w : weights) {
            total += w;
        }
        double target = random.nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            target -= weights[i];
            if (target < 0) {
                return i;
            }
        }
        return weights.length - 1;
    }
}
