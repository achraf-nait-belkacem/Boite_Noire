package com.BoiteNoire.BoiteNoire.analytics;

import com.BoiteNoire.BoiteNoire.analytics.TopUser;
import com.BoiteNoire.BoiteNoire.analytics.ErrorTypeDate;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.DateOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.format.annotation.DateTimeFormat; 
import org.springframework.web.bind.annotation.GetMapping; 
import org.springframework.web.bind.annotation.RequestMapping; 
import org.springframework.web.bind.annotation.RequestParam;   
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.tags.ArgumentAware;

import java.time.Instant;
import java.util.List;




@RestController 
@RequestMapping ("/api/analytics")
public class Engine 
{
    private final MongoTemplate mongoTemplate;

    public Engine(MongoTemplate mongoTemplate)
    {
        this.mongoTemplate = mongoTemplate;
    }

    @GetMapping ("/top-users")
    //iso date is 2026-10-01T00:00:00Z
    //GET /top-users?beginDate=2026-10-01T08:00:00Z&endDate=2026-10-06T18:00:00Z
    //2026-01-01T00:00:00Z
    //2026-12-31T23:59:59Z
    public List<TopUser> getTopUsers(@RequestParam @DateTimeFormat (iso = DateTimeFormat.ISO.DATE_TIME) Instant beginDate, @RequestParam @DateTimeFormat (iso  = DateTimeFormat.ISO.DATE_TIME) Instant endDate)
    {
        Aggregation aggregation = Aggregation.newAggregation
        (
            Aggregation.match(Criteria.where("timestamp").gte(beginDate).lte(endDate)),
            Aggregation.group("userId").count().as("count"),
            Aggregation.project("count").and("_id").as("userId"), //restruct and add a numvalue to the userId
            Aggregation.sort(Sort.Direction.DESC, "count"),
            Aggregation.limit(100)//top 100 no need to change 
        );
        return  mongoTemplate.aggregate(aggregation, "events", TopUser.class).getMappedResults();
    }

    @GetMapping("/errors-by-day")
    //filters and count errors by type for each day on a given period of time
    public List<ErrorTypeDate> getErrorsByDay(@RequestParam @DateTimeFormat (iso = DateTimeFormat.ISO.DATE_TIME) Instant beginDate, @RequestParam @DateTimeFormat (iso  = DateTimeFormat.ISO.DATE_TIME) Instant endDate)
    {
        Aggregation aggregation = Aggregation.newAggregation
        (
            Aggregation.match(Criteria.where("type").is("ERROR").and("timestamp").gte(beginDate).lte(endDate)),
            Aggregation.project("payload.errorType").and(DateOperators.dateOf("timestamp").toString("%Y-%m-%d")).as("day"),
            Aggregation.group("day", "errorType").count().as("count"),
            Aggregation.project("count").and("_id.day").as("day").and("_id.errorType").as("errorType"),
            Aggregation.sort(Sort.Direction.ASC, "day")
        );
        return mongoTemplate.aggregate(aggregation, "events", ErrorTypeDate.class).getMappedResults();
    }

    //slowest endpoint
    @GetMapping("/endpoint-performance") 
    public List<EndpointData> getEndpointPerformance() {
        Aggregation aggregation = Aggregation.newAggregation
        (Aggregation.match(Criteria.where("type").is("API_CALL")),
        //calculate average and p96 using mongo syntax
            context -> org.bson.Document.parse("""
                {
                $group: {
                _id: "$payload.endpoint",
                averageDuration: { $avg: "$payload.durationMs" },
                p95Duration: { $percentile: { input: "$payload.durationMs", p: [0.95], method: "approximate" } }
                }
            }
            """),
            //our syntax
            context -> org.bson.Document.parse("""
                {
                $project: { 
                endpoint: "$_id",
                averageDuration: { $round: ["$averageDuration", 2] },
                p95Duration: { $arrayElemAt: ["$p95Duration", 0] }
            }
            }
            """)
        );
        return mongoTemplate.aggregate(aggregation, "events", EndpointData.class).getMappedResults();
    }


}
