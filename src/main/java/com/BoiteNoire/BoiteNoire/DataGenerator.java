package com.BoiteNoire.BoiteNoire;

import com.BoiteNoire.model.Event;
import com.BoiteNoire.model.payloads.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


@Component //save class as bean
@Profile ("generator") //only launches if we launch said profile

//add run() method from spring
public class DataGenerator implements CommandLineRunner  {
    private final MongoTemplate mongoTemplate; //ref mongodb util
    
    public DataGenerator(MongoTemplate mongoTemplate) //spring inject mongotemplate into app
    {
        this.mongoTemplate = mongoTemplate;
    }

    @Override 
    public void run(String... args)//run seen above
    {
        System.err.println("generating data");
        Random randomVal = new Random();
        List<Event> batchEvents = new ArrayList<>();
        mongoTemplate.dropCollection("events"); //should be empty anyways

        for (int i = 1; i <= 100000; i++)
        {
            //proba big/small user
            String userId;
            if (randomVal.nextInt(100) < 70 ) 
            {
                userId = "user" + (1 + randomVal.nextInt(10)); //our ten biggest users
            }
            else
            {
                userId = "user" + (11 + randomVal.nextInt(500));
            }
        }

        int month = 1 + randomVal.nextInt(12);
        int day = 1 + randomVal.nextInt(28);
        int hour;
        if (randomVal.nextInt(100)<80)
        {
            hour = 9 + randomVal.nextInt(10);
        }
        else
        {
            hour = randomVal.nextInt(24);
        }
        int minute = randomVal.nextInt(60);
        int second = randomVal.nextInt(60);

        Instant timestamp = LocalDate.of(2026, month, day, hour, minute, second).toInstant(ZoneOffset.UTC); //convert and savedate to UTC formazt used by mongo

        //type of events
        int typeRandom =  randomVal.nextInt(100); 
        Event event;


        if (typeRandom < 50) 
        {
            //50% of events API
            int duration = 50 + randomVal.nextInt(100);
            if (randomVal.nextInt(100)<5)
            {
                
            }
        }
    }
    
}
