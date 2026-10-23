package com.rajesh.workspace.records;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class RecordService {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    public RecordService(JdbcTemplate db, ObjectMapper json) { this.db=db; this.json=json; }
    private Map<String,Object> decode(String value) {
        try { return json.readValue(value, new TypeReference<LinkedHashMap<String,Object>>() {}); }
        catch (Exception e) { throw new IllegalStateException("Cannot read saved record", e); }
    }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("Cannot save record", e); }
    }
    public List<Map<String,Object>> list(String kind) {
        return db.query("SELECT id,payload FROM workspace_record WHERE kind=? ORDER BY id", (rs,n) -> {
            var data=decode(rs.getString("payload")); data.put("id",rs.getString("id")); return data;
        },kind);
    }
    private Map<String,Object> locked(String kind, UUID id) {
        var rows=db.query("SELECT payload FROM workspace_record WHERE kind=? AND id=? FOR UPDATE",(rs,n)->decode(rs.getString(1)),kind,id);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Record not found");
        return rows.getFirst();
    }
    @Transactional
    public Map<String,Object> create(String kind,Object value) {
        guard();
        Map<String,Object> data=json.convertValue(value,new TypeReference<LinkedHashMap<String,Object>>(){});
        validate(kind,data);
        if(kind.equals("loans")) { data.put("repaid",BigDecimal.ZERO); data.put("repayments",new ArrayList<>()); }
        if(kind.equals("habits")) data.put("completedDates",new ArrayList<>());
        if(kind.equals("finances")) {
            data.put("anchorDay",LocalDate.parse(data.get("date").toString()).getDayOfMonth());
            data.put("payments",new ArrayList<>());
            if(data.get("status").equals("paid")) recordPayment(data);
        }
        UUID id=UUID.randomUUID(); db.update("INSERT INTO workspace_record(id,kind,payload) VALUES(?,?,?)",id,kind,encode(data));
        data.put("id",id.toString()); return data;
    }
    @Transactional
    public Map<String,Object> replace(String kind,UUID id,Object value) {
        guard();
        var previous=locked(kind,id);
        Map<String,Object> data=json.convertValue(value,new TypeReference<LinkedHashMap<String,Object>>(){});
        validate(kind,data);
        if(kind.equals("loans")) {
            if(new BigDecimal(data.get("amount").toString()).compareTo(new BigDecimal(previous.get("repaid").toString()))<0) bad("Loan amount cannot be below repayments already recorded");
            data.put("repaid",previous.get("repaid")); data.put("repayments",previous.get("repayments"));
        }
        if(kind.equals("habits")) data.put("completedDates",previous.get("completedDates"));
        if(kind.equals("finances")) {
            data.put("payments",previous.getOrDefault("payments",new ArrayList<>()));
            @SuppressWarnings("unchecked") var payments=(List<Map<String,Object>>)data.get("payments");
            if(!Objects.equals(previous.get("date"),data.get("date")) && payments.stream().anyMatch(p->p.get("dueDate").equals(data.get("date")))) bad("This due date already has a recorded payment");
            if(data.get("status").equals("paid")&&!data.get("recurrence").equals("none")) bad("Create a new recurring commitment for future payments");
            if(!Objects.equals(previous.get("status"),data.get("status"))) bad("Use the payment action to change payment status");
            data.put("anchorDay",Objects.equals(previous.get("date"),data.get("date"))?previous.getOrDefault("anchorDay",LocalDate.parse(data.get("date").toString()).getDayOfMonth()):LocalDate.parse(data.get("date").toString()).getDayOfMonth());
        }
        return save(kind,id,data);
    }
    private void validate(String kind,Map<String,Object> data) {
        if(kind.equals("finances") && data.get("recurrence")==null) data.put("recurrence","none");
        if(kind.equals("courses")) {
            if(((Number)data.get("completed")).intValue()>((Number)data.get("total")).intValue()) bad("Completed units cannot exceed total units");
            String url=(String)data.get("url");
            if(url!=null && !url.isBlank()) {
                try { var uri=java.net.URI.create(url); if(!Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null) bad("Course URL must be an HTTP or HTTPS URL"); }
                catch(IllegalArgumentException e) { bad("Invalid course URL"); }
            }
        }
    }
    private Map<String,Object> save(String kind,UUID id,Map<String,Object> data) {
        db.update("UPDATE workspace_record SET payload=? WHERE kind=? AND id=?",encode(data),kind,id);
        data.put("id",id.toString()); return data;
    }
    @Transactional
    public Map<String,Object> repay(UUID id,BigDecimal amount,UUID requestId) {
        guard();
        var data=locked("loans",id);
        @SuppressWarnings("unchecked") var existing=(List<Map<String,Object>>)data.get("repayments");
        for(var payment:existing) if(requestId.toString().equals(payment.get("requestId"))) {
            if(new BigDecimal(payment.get("amount").toString()).compareTo(amount)!=0) throw new ResponseStatusException(HttpStatus.CONFLICT,"This repayment key was already used with a different amount");
            data.put("id",id.toString()); return data;
        }
        var repaid=new BigDecimal(data.get("repaid").toString()).add(amount);
        if(repaid.compareTo(new BigDecimal(data.get("amount").toString()))>0) bad("Repayment exceeds outstanding balance");
        data.put("repaid",repaid);
        @SuppressWarnings("unchecked") var history=(List<Object>)data.get("repayments");
        history.add(Map.of("amount",amount,"recordedAt",java.time.Instant.now().toString(),"requestId",requestId.toString()));
        return save("loans",id,data);
    }
    @Transactional
    public Map<String,Object> completeHabit(UUID id,LocalDate date,boolean completed) {
        guard();
        var data=locked("habits",id);
        @SuppressWarnings("unchecked") var dates=(List<String>)data.get("completedDates");
        dates.remove(date.toString()); if(completed) dates.add(date.toString());
        return save("habits",id,data);
    }
    @Transactional
    public void delete(String kind,UUID id) {
        guard();
        if(db.update("DELETE FROM workspace_record WHERE kind=? AND id=?",kind,id)==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Record not found");
    }
    public Map<String,Object> settings() {
        return db.query("SELECT name,intention FROM workspace_settings WHERE id=1",(rs,n)->Map.<String,Object>of("name",rs.getString(1),"intention",rs.getString(2))).stream().findFirst().orElse(Map.of("name","","intention",""));
    }
    @Transactional
    public Models.Settings settings(Models.Settings settings) {
        guard();
        db.update("MERGE INTO workspace_settings(id,name,intention) KEY(id) VALUES(1,?,?)",settings.name(),settings.intention()); return settings;
    }
    public void guard() { db.queryForObject("SELECT id FROM workspace_guard WHERE id=1 FOR UPDATE",Integer.class); }
    @SuppressWarnings("unchecked")
    private void recordPayment(Map<String,Object> data) {
        LocalDate due=LocalDate.parse(data.get("date").toString());
        ((List<Object>)data.get("payments")).add(Map.of("dueDate",due.toString(),"amount",data.get("amount"),"recordedAt",java.time.Instant.now().toString()));
        String recurrence=data.getOrDefault("recurrence","none").toString();
        if(recurrence.equals("none")) data.put("status","paid");
        else {
            LocalDate next=recurrence.equals("monthly")?due.plusMonths(1):due.plusYears(1);
            int anchor=((Number)data.get("anchorDay")).intValue();
            next=next.withDayOfMonth(Math.min(anchor,next.lengthOfMonth()));
            data.put("date",next.toString()); data.put("status","scheduled");
        }
    }
    @Transactional
    public Map<String,Object> pay(UUID id,LocalDate dueDate) {
        guard(); var data=locked("finances",id);
        data.putIfAbsent("payments",new ArrayList<>());
        @SuppressWarnings("unchecked") var payments=(List<Map<String,Object>>)data.get("payments");
        if(payments.stream().anyMatch(p->p.get("dueDate").equals(dueDate.toString()))) { data.put("id",id.toString()); return data; }
        if(!data.get("date").equals(dueDate.toString())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Due date changed; refresh before recording payment");
        if(data.get("status").equals("paid")) { data.put("id",id.toString()); return data; }
        data.putIfAbsent("anchorDay",dueDate.getDayOfMonth());
        recordPayment(data); return save("finances",id,data);
    }
    private void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
}
