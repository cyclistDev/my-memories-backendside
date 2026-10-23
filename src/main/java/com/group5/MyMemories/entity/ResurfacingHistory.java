package com.group5.MyMemories.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(
        name = "resurfacing_history",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_resurfacing_history_user_date",
                columnNames = {"user_id", "surfaced_date"}))
@Getter
@Setter
@NoArgsConstructor
public class ResurfacingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "memory_id", nullable = false)
    private Memory memory;

    @Column(name = "surfaced_date", nullable = false)
    private LocalDate surfacedDate;

    public ResurfacingHistory(User user, Memory memory, LocalDate surfacedDate) {
        this.user = user;
        this.memory = memory;
        this.surfacedDate = surfacedDate;
    }
}
