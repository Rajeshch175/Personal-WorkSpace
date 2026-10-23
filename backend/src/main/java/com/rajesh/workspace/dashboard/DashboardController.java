package com.rajesh.workspace.dashboard;

import com.rajesh.workspace.records.RecordService;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final RecordService service;
    public DashboardController(RecordService service) { this.service=service; }
    private LocalDate date(Map<String,Object> record) { return LocalDate.parse(record.get("date").toString()); }
    private int minutes(Map<String,Object> record) { return ((Number)record.get("minutes")).intValue(); }
    @GetMapping
    public Map<String,Object> dashboard(@RequestParam(required=false) LocalDate date) {
        LocalDate day=date==null?LocalDate.now():date;
        var blocks=service.list("time-blocks");
        var timeline=blocks.stream().filter(x->date(x).equals(day)).sorted(Comparator.comparing(x->x.get("time").toString())).toList();
        var courses=service.list("courses");
        courses.forEach(x->x.put("progress",Math.round(((Number)x.get("completed")).doubleValue()*100/((Number)x.get("total")).intValue())));
        var finances=service.list("finances").stream().sorted(Comparator.comparing(this::date)).toList();
        var upcoming=finances.stream().filter(x->!x.get("status").equals("paid")&&!date(x).isBefore(day)&&date(x).isBefore(day.plusDays(30))).toList();
        Map<String,BigDecimal> categories=new LinkedHashMap<>();
        for(var item:upcoming) categories.merge(item.get("category").toString(),new BigDecimal(item.get("amount").toString()),BigDecimal::add);
        var total=categories.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add);
        var loans=service.list("loans");
        loans.forEach(x->{
            var balance=new BigDecimal(x.get("amount").toString()).subtract(new BigDecimal(x.get("repaid").toString()));
            x.put("outstanding",balance);
            x.put("status",balance.signum()==0?"Repaid":LocalDate.parse(x.get("dueDate").toString()).isBefore(day)?"Overdue":"On track");
        });
        var habits=service.list("habits");
        habits.forEach(x->x.put("completed",((List<?>)x.get("completedDates")).contains(day.toString())));
        long habitsDone=habits.stream().filter(x->Boolean.TRUE.equals(x.get("completed"))).count();
        long done=timeline.stream().filter(x->x.get("status").equals("done")).count();
        int focus=timeline.stream().filter(x->x.get("status").equals("done")&&!x.get("type").equals("wellbeing")).mapToInt(this::minutes).sum();
        Set<LocalDate> learningDays=new HashSet<>();
        blocks.stream().filter(x->x.get("status").equals("done")&&Set.of("learning","course").contains(x.get("type"))).forEach(x->learningDays.add(date(x)));
        LocalDate cursor=learningDays.contains(day)?day:day.minusDays(1); int streak=0;
        while(learningDays.contains(cursor)) { streak++; cursor=cursor.minusDays(1); }
        var week=new ArrayList<Map<String,Object>>();
        LocalDate monday=day.with(DayOfWeek.MONDAY);
        for(int i=0;i<7;i++) {
            LocalDate weekday=monday.plusDays(i);
            var completed=blocks.stream().filter(x->date(x).equals(weekday)&&x.get("status").equals("done")).toList();
            week.add(Map.of("date",weekday,"work",completed.stream().filter(x->x.get("type").equals("work")).mapToInt(this::minutes).sum(),"study",completed.stream().filter(x->Set.of("learning","course").contains(x.get("type"))).mapToInt(this::minutes).sum()));
        }
        int count=timeline.size()+habits.size();
        return Map.of("date",day,"settings",service.settings(),"stats",Map.of("focusedMinutes",focus,"habitsDone",habitsDone,"habitsTotal",habits.size(),"next30Days",total,"dayScore",count==0?0:Math.round((done+habitsDone)*100.0/count),"streak",streak),"timeline",timeline,"courses",courses,"finances",finances,"moneyCategories",categories,"loans",loans,"habits",habits,"week",week);
    }
}
