package ru.practicum.ewm.partrequest.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.event.enums.State;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.exception.ConflictDataException;
import ru.practicum.ewm.exception.DuplicateException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.partrequest.dto.ParticipationRequestDto;
import ru.practicum.ewm.partrequest.enums.Status;
import ru.practicum.ewm.partrequest.mapper.ParticipationRequestMapper;
import ru.practicum.ewm.partrequest.model.ParticipationRequest;
import ru.practicum.ewm.partrequest.repository.ParticipationRequestRepository;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.repository.UserRepository;

import java.util.List;

import static ru.practicum.ewm.partrequest.model.ParticipationRequest.ParticipationRequestBuilder;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParticipationRequestServiceImpl implements ParticipationRequestService {
    private final ParticipationRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;

    @Transactional
    @Override
    public ParticipationRequestDto addRequest(Long userId, Long eventId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь c id: " + userId + " не найден"));
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие c id: " + eventId + " не найдено"));
        if (requestRepository.existsByRequesterAndEvent(user, event)) {
            throw new DuplicateException("Такой запрос уже существует");
        }
        if (event.getInitiator().equals(user)) {
            throw new ConflictDataException("Пользователь не может создать запрос на участие в своем же событии");
        }
        if (!event.getState().equals(State.PUBLISHED)) {
            throw new ConflictDataException("Нельзя учавствовать в неопубликованном событии");
        }
        Integer participantLimit = event.getParticipantLimit();
        Integer confirmedRequests = event.getConfirmedRequests();
        if (!participantLimit.equals(0) && participantLimit.equals(confirmedRequests)) {
            throw new ConflictDataException("Лимит запросов на участие в событии уже достигнут");
        }
        ParticipationRequestBuilder builder = ParticipationRequest.builder()
                .requester(user)
                .event(event);
        if (event.getRequestModeration()) {
            builder.status(Status.PENDING);
        } else {
            builder.status(Status.CONFIRMED);
            event.setConfirmedRequests(++confirmedRequests);
        }
        return ParticipationRequestMapper.toParticipationRequestDto(requestRepository.save(builder.build()));
    }

    @Transactional
    @Override
    public ParticipationRequestDto cancelRequest(Long userId, Long requestId) {
        checkExistsUserById(userId);
        ParticipationRequest request = requestRepository.findByRequesterIdAndId(userId, requestId)
                .orElseThrow(() -> new NotFoundException("У пользователя с id: " + userId +
                        " не найдено запроса с id: " + requestId));
        if (request.getStatus() == Status.CONFIRMED) {
            Event event = request.getEvent();
            Integer confirmedRequests = event.getConfirmedRequests();
            event.setConfirmedRequests(--confirmedRequests);
        }
        request.setStatus(Status.CANCELED);
        return ParticipationRequestMapper.toParticipationRequestDto(request);
    }

    @Override
    public List<ParticipationRequestDto> getAllUserRequests(Long userId) {
        checkExistsUserById(userId);
        return ParticipationRequestMapper.toParticipationRequestDto(requestRepository.findAllByRequesterId(userId));
    }


    private void checkExistsUserById(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("Пользователь c id: " + userId + " не найден");
        }
    }
}
