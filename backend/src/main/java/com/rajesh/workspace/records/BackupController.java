package com.rajesh.workspace.records;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.math.BigDecimal;
import java.time.*;

@RestController
@RequestMapping("/api/backup")
public class BackupController {
    private static final Map<String,Class<?>> TYPES=Map.of("time-blocks",Models.TimeBlock.class,"courses",Models.Course.class,"finances",Models.Finance.class,"loans",Models.Loan.class,"habits",Models.Habit.class);
    private final RecordService service;
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final Validator validator;
    public BackupController(RecordService service,JdbcTemplate db,ObjectMapper json,Validator validator) { this.service=service;this.db=db;this.json=json;this.validator=validator; }
    @GetMapping
    @Transactional
    public Map<String,Object> export() {
        service.guard();
        Map<String,Object> records=new TreeMap<>();
        TYPES.keySet().forEach(kind->records.put(kind,service.list(kind)));
        return Map.of("version",1,"exportedAt",Instant.now().toString(),"settings",service.settings(),"records",records);
    }
    @PostMapping("/restore")
    @Transactional
    public Map<String,Object> restore(@RequestBody Map<String,Object> backup) {
        service.guard();
        try {
            if(!Integer.valueOf(1).equals(backup.get("version"))) invalid();
            Map<String,Object> settings=map(backup.get("settings"));
            if(!settings.equals(Map.of("name","","intention",""))) check(settings,Models.Settings.class);
            Map<String,Object> groups=map(backup.get("records"));
            if(!groups.keySet().equals(TYPES.keySet())) invalid();
            Set<UUID> ids=new HashSet<>();
            List<Map<String,Object>> rows=new ArrayList<>();
            for(var entry:groups.entrySet()) {
                if(!(entry.getValue() instanceof List<?> items)) { invalid(); return null; }
                for(var item:items) {
                    Map<String,Object> data=new LinkedHashMap<>(map(item));
                    UUID id=UUID.fromString(data.remove("id").toString());
                    if(!ids.add(id) || ids.size()>100000) invalid();
                    check(data,TYPES.get(entry.getKey()));
                    validateHistory(entry.getKey(),data);
                    rows.add(Map.of("id",id,"kind",entry.getKey(),"payload",json.writeValueAsString(data)));
                }
            }
            // Validation is complete before any existing record is removed.
            db.update("DELETE FROM workspace_record");
            db.update("DELETE FROM workspace_settings");
            for(var row:rows) db.update("INSERT INTO workspace_record(id,kind,payload) VALUES(?,?,?)",row.get("id"),row.get("kind"),row.get("payload"));
            if(!settings.get("name").equals("")) db.update("INSERT INTO workspace_settings(id,name,intention) VALUES(1,?,?)",settings.get("name"),settings.get("intention"));
            return Map.of("restored",rows.size());
        } catch(ResponseStatusException e) { throw e; }
        catch(Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid backup; no data was changed"); }
    }
    private void check(Map<String,Object> data,Class<?> type) {
        Object value=json.convertValue(data,type);
        if(!validator.validate(value).isEmpty()) invalid();
        if(value instanceof Models.Course course) {
            if(course.completed()>course.total()) invalid();
            if(course.url()!=null && !course.url().isBlank()) { var uri=java.net.URI.create(course.url()); if(!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null) invalid(); }
        }
    }
    private void validateHistory(String kind,Map<String,Object> data) {
        if(kind.equals("habits")) {
            Set<String> dates=new HashSet<>();
            for(Object date:(List<?>)data.get("completedDates")) if(!dates.add(LocalDate.parse(date.toString()).toString())) invalid();
        }
        if(kind.equals("loans")) {
            BigDecimal sum=BigDecimal.ZERO; Set<String> keys=new HashSet<>();
            for(Object entry:(List<?>)data.get("repayments")) {
                var payment=map(entry); BigDecimal amount=amount(payment.get("amount")); sum=sum.add(amount);
                Instant.parse(payment.get("recordedAt").toString());
                if(payment.containsKey("requestId")&&!keys.add(UUID.fromString(payment.get("requestId").toString()).toString())) invalid();
            }
            if(sum.compareTo(new BigDecimal(data.get("repaid").toString()))!=0||sum.compareTo(new BigDecimal(data.get("amount").toString()))>0) invalid();
        }
        if(kind.equals("finances")) {
            if(data.get("recurrence")==null) data.put("recurrence","none");
            data.putIfAbsent("anchorDay",LocalDate.parse(data.get("date").toString()).getDayOfMonth());
            int anchor=((Number)data.get("anchorDay")).intValue(); if(anchor<1||anchor>31) invalid();
            data.putIfAbsent("payments",new ArrayList<>()); Set<String> dates=new HashSet<>();
            for(Object entry:(List<?>)data.get("payments")) {
                var payment=map(entry); amount(payment.get("amount")); Instant.parse(payment.get("recordedAt").toString());
                if(!dates.add(LocalDate.parse(payment.get("dueDate").toString()).toString())) invalid();
            }
        }
    }
    private BigDecimal amount(Object value) {
        BigDecimal amount=new BigDecimal(value.toString());
        if(amount.signum()<=0||amount.scale()>2||amount.compareTo(new BigDecimal("9999999999.99"))>0) invalid();
        return amount;
    }
    @SuppressWarnings("unchecked") private Map<String,Object> map(Object value) { return (Map<String,Object>)value; }
    private void invalid() { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid backup; no data was changed"); }
}
