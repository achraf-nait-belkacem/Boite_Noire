package com.pigeon.boitenoire.generator;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.pigeon.boitenoire.model.User;

public class UserGenerator {

    private static final String[] FIRST_NAMES = {
            "Achraf", "Amelie", "Lucas", "Emma", "Hugo", "Lina", "Noah", "Sarah", "Adam", "Chloe",
            "Yanis", "Camille", "Omar", "Ines", "Louis", "Manon", "Karim", "Julie", "Nathan", "Zoe",
            "Sofia", "Mehdi", "Clara", "Anis", "Lea", "Rayan", "Jade", "Malik", "Eva", "Tom" };
    private static final String[] LAST_NAMES = {
            "Martin", "Bernard", "Dubois", "Thomas", "Robert", "Richard", "Petit", "Durand", "Leroy", "Moreau",
            "Simon", "Laurent", "Lefebvre", "Michel", "Garcia", "David", "Bertrand", "Roux", "Vincent", "Fournier",
            "Benali", "Haddad", "Nait", "Cherif", "Morel", "Girard", "Andre", "Mercier", "Blanc", "Guerin" };

    private static final double EXISTING_USER_RATE = 0.5;
    private static final int SIGNUP_WINDOW_DAYS = 310;

    private final Random random;
    private final ActivityCalendar calendar;

    UserGenerator(Random random, ActivityCalendar calendar) {
        this.random = random;
        this.calendar = calendar;
    }

    public List<User> generate(int count) {
        List<User> users = new ArrayList<>(count);
        Instant signupEnd = calendar.start().plus(Duration.ofDays(SIGNUP_WINDOW_DAYS));
        for (int i = 0; i < count; i++) {
            Instant createdAt;
            if (random.nextDouble() < EXISTING_USER_RATE) {
                createdAt = calendar.start().minus(Duration.ofDays(30 + random.nextInt(670)))
                        .minusSeconds(random.nextInt(86_400));
            } else {
                createdAt = calendar.sample(calendar.start(), signupEnd, random);
            }
            String first = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
            String last = LAST_NAMES[random.nextInt(LAST_NAMES.length)];
            String email = (first + "." + last + "." + (i + 1)).toLowerCase() + "@example.com";
            users.add(new User(ObjectIds.at(createdAt, random), first + " " + last, email, createdAt));
        }
        return users;
    }
}
