package ru.practicum.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserFullDto {
    private Long id;
    private String name;
    private String email;
    private String rank;
    private Integer commentsCount;
    private Integer adminWarnings;
    private String bannedUntil;
    private boolean banned;
}