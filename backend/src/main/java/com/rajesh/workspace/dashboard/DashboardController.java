package com.rajesh.workspace.dashboard;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    @GetMapping
    public Map<String, Object> dashboard() {
        return Map.of(
            "stats", Map.of("focusedTime", "6h 10m", "habits", "3 / 5", "next30Days", "₹18,400", "dayScore", 78),
            "timeline", List.of(
                Map.of("time", "07:00", "title", "Morning routine", "subtitle", "Walk · breakfast · plan", "status", "done", "type", "wellbeing"),
                Map.of("time", "09:30", "title", "Office hours", "subtitle", "Focus block · 09:30 — 18:30", "status", "active", "type", "work"),
                Map.of("time", "19:30", "title", "DSA practice", "subtitle", "Arrays & hashing · 45 min", "status", "planned", "type", "learning"),
                Map.of("time", "21:00", "title", "System design course", "subtitle", "Video 12 · consistency models", "status", "planned", "type", "course")
            ),
            "courses", List.of(
                Map.of("name", "DSA Patterns", "progress", 42, "completed", 50, "total", 120, "subtitle", "30-day sprint · 120 problems", "color", "dsa"),
                Map.of("name", "System Design / HLD", "progress", 28, "completed", 12, "total", 42, "subtitle", "Architecture fundamentals", "color", "hld"),
                Map.of("name", "LLD with Java", "progress", 16, "completed", 8, "total", 50, "subtitle", "Patterns · SOLID · practice", "color", "lld")
            ),
            "loans", List.of(
                Map.of("name", "Arjun Kapoor", "initials", "AK", "amount", "₹8,000", "dueDate", "12 Oct", "status", "Reminder soon", "tone", "warn", "color", "amber", "note", "Personal loan · sent 12 Sep 2026"),
                Map.of("name", "Neha Shah", "initials", "NS", "amount", "₹3,500", "dueDate", "28 Oct", "status", "On track", "tone", "quiet", "color", "pink", "note", "Travel split · sent 28 Sep 2026")
            ),
            "finances", List.of(
                Map.of("name", "Index fund SIP", "date", "05 Oct", "amount", "₹4,000", "status", "scheduled", "tone", "good"),
                Map.of("name", "Emergency fund SIP", "date", "10 Oct", "amount", "₹2,000", "status", "scheduled", "tone", "good"),
                Map.of("name", "Rent auto pay", "date", "01 Oct", "amount", "₹28,000", "status", "paid", "tone", "good"),
                Map.of("name", "Learning platform", "date", "15 Oct", "amount", "₹1,499", "status", "review", "tone", "warn"),
                Map.of("name", "Music subscription", "date", "21 Oct", "amount", "₹119", "status", "active", "tone", "quiet")
            )
        );
    }
}
