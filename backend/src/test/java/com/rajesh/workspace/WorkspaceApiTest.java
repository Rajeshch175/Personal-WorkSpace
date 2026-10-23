package com.rajesh.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:workspace-test;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class WorkspaceApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @BeforeEach void reset() { db.update("DELETE FROM workspace_record"); db.update("DELETE FROM workspace_settings"); }
    JsonNode create(String kind,String body) throws Exception {
        return json.readTree(mvc.perform(post("/api/"+kind).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    JsonNode dashboard() throws Exception {
        return json.readTree(mvc.perform(get("/api/dashboard?date=2026-10-23")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    @Test void startsEmptyAndCalculatesFromSavedRecords() throws Exception {
        var empty=dashboard(); assertTrue(empty.get("timeline").isEmpty()); assertEquals(0,empty.at("/stats/next30Days").asInt());
        create("time-blocks",""" 
            {"title":"Study","date":"2026-10-23","time":"09:00","minutes":45,"type":"learning","status":"done"}
            """);
        create("time-blocks","""
            {"title":"Yesterday","date":"2026-10-22","time":"09:00","minutes":30,"type":"course","status":"done"}
            """);
        create("finances","""
            {"name":"Due","date":"2026-10-23","amount":120.25,"category":"bill","status":"scheduled"}
            """);
        create("finances","""
            {"name":"Paid","date":"2026-10-23","amount":500,"category":"bill","status":"paid"}
            """);
        create("finances","""
            {"name":"Outside window","date":"2026-11-22","amount":1000,"category":"bill","status":"scheduled"}
            """);
        var habit=create("habits","{\"name\":\"Walk\"}");
        String completion="/api/habits/"+habit.get("id").asText()+"/completions/2026-10-23";
        mvc.perform(put(completion)).andExpect(status().isOk()); mvc.perform(put(completion)).andExpect(status().isOk());
        var result=dashboard();
        assertEquals(1,result.get("timeline").size()); assertEquals(45,result.at("/stats/focusedMinutes").asInt());
        assertEquals(120.25,result.at("/stats/next30Days").asDouble()); assertEquals(100,result.at("/stats/dayScore").asInt());
        assertEquals(2,result.at("/stats/streak").asInt()); assertEquals(45,result.at("/week/4/study").asInt());
        mvc.perform(delete(completion)).andExpect(status().isOk()); assertEquals(50,dashboard().at("/stats/dayScore").asInt());
    }
    @Test void repaymentsPreserveHistoryAndRejectOverpayment() throws Exception {
        var loan=create("loans","""
            {"name":"Person","note":"Loan","amount":100.50,"dueDate":"2026-10-22"}
            """);
        String path="/api/loans/"+loan.get("id").asText()+"/repayments";
        mvc.perform(post(path).header("Idempotency-Key",java.util.UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":40.25}")).andExpect(status().isOk());
        mvc.perform(post(path).header("Idempotency-Key",java.util.UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":70}")).andExpect(status().isBadRequest());
        var item=dashboard().get("loans").get(0); assertEquals(60.25,item.get("outstanding").asDouble()); assertEquals(1,item.get("repayments").size()); assertEquals("Overdue",item.get("status").asText());
        mvc.perform(post(path).header("Idempotency-Key",java.util.UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":60.25}")).andExpect(status().isOk());
        assertEquals("Repaid",dashboard().at("/loans/0/status").asText());
    }
    @Test void validatesRecordsAndSupportsUpdateDeleteAndSettings() throws Exception {
        mvc.perform(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Course\",\"subtitle\":\"\",\"total\":2,\"completed\":3}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Course\",\"subtitle\":\"\",\"total\":2,\"completed\":0,\"url\":\"javascript:alert(1)\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/loans").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\",\"amount\":-1}")).andExpect(status().isBadRequest());
        var course=create("courses","{\"name\":\"Course\",\"subtitle\":\"\",\"total\":2,\"completed\":0}");
        String path="/api/courses/"+course.get("id").asText();
        mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Updated\",\"subtitle\":\"\",\"total\":2,\"completed\":1}")).andExpect(status().isOk());
        assertEquals(50,dashboard().at("/courses/0/progress").asInt());
        mvc.perform(delete(path)).andExpect(status().isNoContent()); mvc.perform(delete(path)).andExpect(status().isNotFound());
        mvc.perform(get("/api/dashboard?date=invalid")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/settings").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Rajesh\",\"intention\":\"Read daily\"}")).andExpect(status().isOk());
        assertEquals("Read daily",dashboard().at("/settings/intention").asText());
    }
    @Test void concurrentRepaymentsCannotOverdrawLoan() throws Exception {
        var loan=create("loans","{\"name\":\"Concurrent\",\"note\":\"\",\"amount\":100,\"dueDate\":\"2026-10-23\"}");
        String path="/api/loans/"+loan.get("id").asText()+"/repayments";
        var start=new java.util.concurrent.CountDownLatch(1);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> request=()-> { start.await(); return mvc.perform(post(path).header("Idempotency-Key",java.util.UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":60}")).andReturn().getResponse().getStatus(); };
            var first=executor.submit(request); var second=executor.submit(request); start.countDown();
            var statuses=new java.util.ArrayList<>(java.util.List.of(first.get(10,java.util.concurrent.TimeUnit.SECONDS),second.get(10,java.util.concurrent.TimeUnit.SECONDS)));
            java.util.Collections.sort(statuses); assertEquals(java.util.List.of(200,400),statuses);
        }
        assertEquals(40,dashboard().at("/loans/0/outstanding").asInt());
        assertEquals(1,dashboard().at("/loans/0/repayments").size());
    }

    @Test void retriesAreIdempotentAndEditsPreserveHistory() throws Exception {
        var loan=create("loans","{\"name\":\"Person\",\"note\":\"\",\"amount\":100,\"dueDate\":\"2026-10-23\"}");
        String path="/api/loans/"+loan.get("id").asText();
        String key=java.util.UUID.randomUUID().toString();
        for(int i=0;i<2;i++) mvc.perform(post(path+"/repayments").header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":30}")).andExpect(status().isOk());
        mvc.perform(post(path+"/repayments").header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":40}")).andExpect(status().isConflict());
        mvc.perform(post(path+"/repayments").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":30}")).andExpect(status().isBadRequest());
        mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Renamed\",\"note\":\"Updated\",\"amount\":100,\"dueDate\":\"2026-11-01\"}")).andExpect(status().isOk());
        assertEquals(70,dashboard().at("/loans/0/outstanding").asInt()); assertEquals(1,dashboard().at("/loans/0/repayments").size());
        mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Renamed\",\"note\":\"\",\"amount\":20,\"dueDate\":\"2026-11-01\"}")).andExpect(status().isBadRequest());
        var habit=create("habits","{\"name\":\"Walk\"}"); String habitPath="/api/habits/"+habit.get("id").asText();
        mvc.perform(put(habitPath+"/completions/2026-10-23")).andExpect(status().isOk());
        mvc.perform(put(habitPath).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Run\"}")).andExpect(status().isOk());
        assertTrue(dashboard().at("/habits/0/completed").asBoolean());
    }
    @Test void monthlyPaymentsKeepAnchorAndRetriesDoNotAdvanceAgain() throws Exception {
        var finance=create("finances","{\"name\":\"SIP\",\"date\":\"2026-01-31\",\"amount\":500,\"category\":\"investment\",\"status\":\"scheduled\",\"recurrence\":\"monthly\"}");
        String path="/api/finances/"+finance.get("id").asText()+"/payments";
        for(int i=0;i<2;i++) mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{\"dueDate\":\"2026-01-31\"}")).andExpect(status().isOk());
        assertEquals("2026-02-28",dashboard().at("/finances/0/date").asText()); assertEquals(1,dashboard().at("/finances/0/payments").size());
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{\"dueDate\":\"2026-02-28\"}")).andExpect(status().isOk());
        assertEquals("2026-03-31",dashboard().at("/finances/0/date").asText());
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{\"dueDate\":\"2026-04-30\"}")).andExpect(status().isConflict());
    }
    @Test void backupRoundTripAndInvalidRestoreLeaveDataIntact() throws Exception {
        create("habits","{\"name\":\"Read\"}");
        var backup=json.readTree(mvc.perform(get("/api/backup")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        create("habits","{\"name\":\"Temporary\"}");
        mvc.perform(post("/api/backup/restore").contentType(MediaType.APPLICATION_JSON).content(backup.toString())).andExpect(status().isOk());
        assertEquals(1,dashboard().get("habits").size());
        ((com.fasterxml.jackson.databind.node.ObjectNode)backup.at("/records/habits/0")).put("name","");
        mvc.perform(post("/api/backup/restore").contentType(MediaType.APPLICATION_JSON).content(backup.toString())).andExpect(status().isBadRequest());
        assertEquals("Read",dashboard().at("/habits/0/name").asText());
    }

    @Test void restorePreservesPaymentKeysAndRejectsCorruptBalances() throws Exception {
        var loan=create("loans","{\"name\":\"Person\",\"note\":\"\",\"amount\":100,\"dueDate\":\"2026-10-23\"}");
        String path="/api/loans/"+loan.get("id").asText()+"/repayments", key=java.util.UUID.randomUUID().toString();
        mvc.perform(post(path).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":25}")).andExpect(status().isOk());
        var finance=create("finances","{\"name\":\"Annual\",\"date\":\"2024-02-29\",\"amount\":500,\"category\":\"bill\",\"status\":\"scheduled\",\"recurrence\":\"yearly\"}");
        mvc.perform(post("/api/finances/"+finance.get("id").asText()+"/payments").contentType(MediaType.APPLICATION_JSON).content("{\"dueDate\":\"2024-02-29\"}")).andExpect(status().isOk());
        var backup=json.readTree(mvc.perform(get("/api/backup")).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/backup/restore").contentType(MediaType.APPLICATION_JSON).content(backup.toString())).andExpect(status().isOk());
        mvc.perform(post(path).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":25}")).andExpect(status().isOk());
        assertEquals(75,dashboard().at("/loans/0/outstanding").asInt());
        assertEquals("2025-02-28",dashboard().at("/finances/0/date").asText());
        assertEquals(1,dashboard().at("/finances/0/payments").size());
        ((com.fasterxml.jackson.databind.node.ObjectNode)backup.at("/records/loans/0")).put("repaid",99);
        mvc.perform(post("/api/backup/restore").contentType(MediaType.APPLICATION_JSON).content(backup.toString())).andExpect(status().isBadRequest());
        assertEquals(75,dashboard().at("/loans/0/outstanding").asInt());
    }

}
