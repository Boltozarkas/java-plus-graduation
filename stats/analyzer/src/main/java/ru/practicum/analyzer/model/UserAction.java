package ru.practicum.analyzer.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_actions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "event_id", nullable = false)
    private Long eventId;
    @Column(nullable = false)
    private Double weight;
    @Column(name = "action_type", nullable = false)
    private String actionType;
    @Column(nullable = false)
    private LocalDateTime timestamp;
}