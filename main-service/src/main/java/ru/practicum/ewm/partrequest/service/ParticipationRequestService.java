package ru.practicum.ewm.partrequest.service;

import ru.practicum.ewm.partrequest.dto.ParticipationRequestDto;

import java.util.List;

public interface ParticipationRequestService {

    ParticipationRequestDto addRequest(Long userId, Long eventId);

    ParticipationRequestDto cancelRequest(Long userId, Long requestId);

    List<ParticipationRequestDto> getAllUserRequests(Long userId);
}
