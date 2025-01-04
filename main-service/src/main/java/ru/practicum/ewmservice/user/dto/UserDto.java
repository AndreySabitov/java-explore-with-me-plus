package ru.practicum.ewmservice.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;


@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDto {
    @Email
    @NotBlank
    String email;
    Long id;

    @NotBlank
    String name;
}
