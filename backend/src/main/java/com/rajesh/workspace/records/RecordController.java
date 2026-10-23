package com.rajesh.workspace.records;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api")
public class RecordController {
    private final RecordService service;
    public RecordController(RecordService service) { this.service=service; }
    @GetMapping("/time-blocks") public List<Map<String,Object>> timeblocks() { return service.list("time-blocks"); }
    @PostMapping("/time-blocks") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> createTimeBlock(@Valid @RequestBody Models.TimeBlock value) { return service.create("time-blocks",value); }
    @DeleteMapping("/time-blocks/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTimeBlock(@PathVariable UUID id) { service.delete("time-blocks",id); }
    @PutMapping("/time-blocks/{id}") public Map<String,Object> updateTimeBlock(@PathVariable UUID id,@Valid @RequestBody Models.TimeBlock value) { return service.replace("time-blocks",id,value); }
    @GetMapping("/courses") public List<Map<String,Object>> courses() { return service.list("courses"); }
    @PostMapping("/courses") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> createCourse(@Valid @RequestBody Models.Course value) { return service.create("courses",value); }
    @DeleteMapping("/courses/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCourse(@PathVariable UUID id) { service.delete("courses",id); }
    @PutMapping("/courses/{id}") public Map<String,Object> updateCourse(@PathVariable UUID id,@Valid @RequestBody Models.Course value) { return service.replace("courses",id,value); }
    @GetMapping("/finances") public List<Map<String,Object>> finances() { return service.list("finances"); }
    @PostMapping("/finances") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> createFinance(@Valid @RequestBody Models.Finance value) { return service.create("finances",value); }
    @DeleteMapping("/finances/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFinance(@PathVariable UUID id) { service.delete("finances",id); }
    @PutMapping("/finances/{id}") public Map<String,Object> updateFinance(@PathVariable UUID id,@Valid @RequestBody Models.Finance value) { return service.replace("finances",id,value); }
    @GetMapping("/loans") public List<Map<String,Object>> loans() { return service.list("loans"); }
    @PostMapping("/loans") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> createLoan(@Valid @RequestBody Models.Loan value) { return service.create("loans",value); }
    @DeleteMapping("/loans/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLoan(@PathVariable UUID id) { service.delete("loans",id); }
    @GetMapping("/habits") public List<Map<String,Object>> habits() { return service.list("habits"); }
    @PostMapping("/habits") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> createHabit(@Valid @RequestBody Models.Habit value) { return service.create("habits",value); }
    @DeleteMapping("/habits/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHabit(@PathVariable UUID id) { service.delete("habits",id); }
    @PostMapping("/loans/{id}/repayments")
    public Map<String,Object> repay(@PathVariable UUID id,@RequestHeader("Idempotency-Key") UUID requestId,@Valid @RequestBody Models.Repayment value) { return service.repay(id,value.amount(),requestId); }
    @PutMapping("/habits/{id}/completions/{date}")
    public Map<String,Object> complete(@PathVariable UUID id,@PathVariable LocalDate date) { return service.completeHabit(id,date,true); }
    @DeleteMapping("/habits/{id}/completions/{date}")
    public Map<String,Object> uncomplete(@PathVariable UUID id,@PathVariable LocalDate date) { return service.completeHabit(id,date,false); }
    @PutMapping("/loans/{id}") public Map<String,Object> updateLoan(@PathVariable UUID id,@Valid @RequestBody Models.Loan value) { return service.replace("loans",id,value); }
    @PutMapping("/habits/{id}") public Map<String,Object> updateHabit(@PathVariable UUID id,@Valid @RequestBody Models.Habit value) { return service.replace("habits",id,value); }
    @PostMapping("/finances/{id}/payments") public Map<String,Object> pay(@PathVariable UUID id,@Valid @RequestBody Models.Payment value) { return service.pay(id,value.dueDate()); }
    @GetMapping("/settings") public Map<String,Object> settings() { return service.settings(); }
    @PutMapping("/settings") public Models.Settings settings(@Valid @RequestBody Models.Settings value) { return service.settings(value); }
}
