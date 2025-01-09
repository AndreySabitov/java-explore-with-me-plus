package ru.practicum.ewm.event.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.category.model.Category;
import ru.practicum.ewm.category.repository.CategoryRepository;
import ru.practicum.ewm.event.dto.*;
import ru.practicum.ewm.event.enums.State;
import ru.practicum.ewm.event.enums.StateAction;
import ru.practicum.ewm.event.mapper.EventMapper;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.exception.*;
import ru.practicum.ewm.partrequest.dto.ParticipationRequestDto;
import ru.practicum.ewm.partrequest.enums.Status;
import ru.practicum.ewm.partrequest.mapper.ParticipationRequestMapper;
import ru.practicum.ewm.partrequest.model.ParticipationRequest;
import ru.practicum.ewm.partrequest.repository.ParticipationRequestRepository;
import ru.practicum.ewm.partrequest.service.ParticipationRequestService;
import ru.practicum.ewm.stats.client.StatClient;
import ru.practicum.ewm.stats.dto.EndpointHitDto;
import ru.practicum.ewm.stats.dto.StatsDto;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;

    private final CategoryRepository categoryRepository;

    private final UserRepository userRepository;

    private final ParticipationRequestRepository requestRepository;

    private final ParticipationRequestService requestService;

    private final StatClient statClient;

    @Value("${ewm.service.name}")
    private String serviceName;

    @Override
    @Transactional
    public EventFullDto addEvent(NewEventDto eventDto, Long userId) {
        checkFields(eventDto);
        Category category = categoryRepository.findById(eventDto.getCategory())
                .orElseThrow(() -> new NotFoundException("Категория не найдена"));
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        Event event = eventRepository.save(EventMapper.mapToEvent(eventDto, category, user));
        return EventMapper.mapToFullDto(event, 0L);
    }

    @Override
    public List<EventShortDto> getEventsOfUser(Long userId, Integer from, Integer size) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("Пользователь не найден");
        }

        Sort sortByCreatedDate = Sort.by("createdOn");
        PageRequest pageRequest = PageRequest.of(from / size, size, sortByCreatedDate);
        List<Event> events = eventRepository.findAllByInitiatorId(userId, pageRequest);

        List<String> urisList = events
                .stream()
                .map(event -> "/events/" + event.getId())
                .toList();

        String uris = String.join(", ", urisList);

        List<StatsDto> statsList = statClient.getStats(events.getFirst().getCreatedOn(),
                LocalDateTime.now(), uris, false);

        return events.stream().map(event -> {
                    Optional<StatsDto> result = statsList.stream()
                            .filter(statsDto -> statsDto.getUri().equals("/events/" + event.getId()))
                            .findFirst();
                    if (result.isPresent()) {
                        return EventMapper.mapToShortDto(event, result.get().getHits());
                    } else {
                        return EventMapper.mapToShortDto(event, 0L);
                    }
                })
                .toList();
    }

    @Override
    public EventFullDto getEventOfUser(Long userId, Long eventId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("Пользователь не найден");
        }
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new NotFoundException("Событие не найдено"));
        if (!Objects.equals(event.getInitiator().getId(), userId)) {
            throw new ValidationException("Можно просмотреть только своё событие");
        }
        String uri = "/events/" + eventId;
        List<StatsDto> statsList = statClient.getStats(event.getCreatedOn(), LocalDateTime.now(), uri, false);
        Optional<StatsDto> result = statsList.stream().findFirst();
        if (result.isPresent()) {
            return EventMapper.mapToFullDto(event, result.get().getHits());
        } else {
            return EventMapper.mapToFullDto(event, 0L);
        }
    }

    @Override
    @Transactional
    public EventFullDto updateEventOfUser(UpdateEventUserRequest updateRequest, Long userId, Long eventId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("Пользователь не найден");
        }
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new NotFoundException("Событие не найдено"));
        if (!Objects.equals(event.getInitiator().getId(), userId)) {
            throw new ValidationException("Можно просмотреть только своё событие");
        }

        if (event.getState() == State.PUBLISHED) {
            throw new ConflictDataException("Нельзя изменить опубликованное событие");
        }

        if (updateRequest.getAnnotation() != null && !updateRequest.getAnnotation().isBlank()) {
            event.setAnnotation(updateRequest.getAnnotation());
        }
        if (updateRequest.getCategory() != null) {
            Category category = categoryRepository.findById(updateRequest.getCategory())
                    .orElseThrow(() -> new NotFoundException("Категория не найдена"));
            event.setCategory(category);
        }
        if (updateRequest.getDescription() != null && !updateRequest.getDescription().isBlank()) {
            event.setDescription(updateRequest.getDescription());
        }
        if (updateRequest.getEventDate() != null) {
            if (updateRequest.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
                throw new ValidationException("Дата начала события должна быть позже чем через 2 часа от текущего" +
                        " времени");
            }
            event.setEventDate(updateRequest.getEventDate());
        }
        if (updateRequest.getLocation() != null) {
            event.setLat(updateRequest.getLocation().getLat());
            event.setLon(updateRequest.getLocation().getLon());
        }
        if (updateRequest.getPaid() != null) {
            event.setPaid(updateRequest.getPaid());
        }
        if (updateRequest.getParticipantLimit() != null) {
            event.setParticipantLimit(updateRequest.getParticipantLimit());
        }
        if (updateRequest.getRequestModeration() != null) {
            event.setRequestModeration(updateRequest.getRequestModeration());
        }
        if (updateRequest.getTitle() != null && !updateRequest.getTitle().isBlank()) {
            event.setTitle(updateRequest.getTitle());
        }
        if (updateRequest.getStateAction() != null) {
            if (updateRequest.getStateAction() == StateAction.SEND_TO_REVIEW) {
                event.setState(State.PUBLISHED);
            } else if (updateRequest.getStateAction() == StateAction.CANCEL_REVIEW) {
                event.setState(State.CANCELED);
            }
        }

        return EventMapper.mapToFullDto(event, 0L);
    }

    //public Получение событий с возможностью фильтрации
    @Override
    public List<EventShortDto> getPublicEventsByFilter(HttpServletRequest httpServletRequest, EventPublicFilter inputFilter
    ) {
        inputFilter.setText("%" + inputFilter.getText().trim().toLowerCase() + "%");
        List<Event> events;

        var participantLimit = inputFilter.getOnlyAvailable() ? 0 : Integer.MIN_VALUE;

        if (inputFilter.getRangeStart() == null && inputFilter.getRangeEnd() == null) {
            events = eventRepository
                    .findAllByStateAndDescriptionContainsOrAnnotationContainsAndEventDateAfterAndPaidAndParticipantLimitGreaterThanOrderByCreatedOn(State.PUBLISHED, inputFilter.getText(), inputFilter.getText(), LocalDateTime.now(), inputFilter.getPaid(), participantLimit);
        } else {
            events = eventRepository
                    .getPublicEventsByFilter(inputFilter.getText(), inputFilter.getRangeStart(), inputFilter.getRangeEnd(), inputFilter.getPaid(), participantLimit);
        }
        if (events == null || events.isEmpty()) {
            return new ArrayList<EventShortDto>();
        }

        List<String> urisList = events
                .stream()
                .map(event -> "/events/" + event.getId())
                .toList();

        String uris = String.join(", ", urisList);

        List<StatsDto> statsList = statClient.getStats(events.getFirst().getCreatedOn(),
                LocalDateTime.now(), uris, false);

        var result = events.stream().map(event -> {

                    Optional<StatsDto> stat = statsList.stream()
                            .filter(statsDto -> statsDto.getUri().equals("/events/" + event.getId()))
                            .findFirst();
                    return EventMapper.mapToShortDto(event, stat.isPresent() ? stat.get().getHits() : 0L);
                })
                .toList();

        switch (inputFilter.getSort()) {
            case EVENT_DATE:
                result.sort(Comparator.comparing(EventShortDto::getEventDate));
                break;
            case VIEWS:
                result.sort(Comparator.comparing(EventShortDto::getViews));
                break;
            default:
                throw new InvalidSortException(String.format("SortType = %s не определен.", inputFilter.getSort()));
        }

        result = result
                .stream()
                .skip(inputFilter.getFrom())
                .limit(inputFilter.getSize())
                .toList();
        var ids = result.stream().map(EventShortDto::getId).toList();
        Map<Long, List<ParticipationRequest>> confirmedRequests = requestService.prepareConfirmedRequests(ids);

        result.forEach(r -> {
                    var requests = confirmedRequests.get(r.getId());

                    r.setConfirmedRequests(requests != null ? requests.size() : 0);
        });

        try {
            EndpointHitDto requestBody = EndpointHitDto
                    .builder().app(serviceName)
                    .ip(httpServletRequest.getRemoteAddr())
                    .uri(httpServletRequest.getRequestURI())
                    .timestamp(LocalDateTime.now())
                    .build();
            statClient.saveHit(requestBody);
            log.info("Сохранение статистики.");
        } catch (SaveStatsException e) {
            log.error("Не удалось сохранить статистику.");
        }

        return result;
    }

    //public Получение подробной информации об опубликованном событии по его идентификатору
    @Override
    public EventFullDto getPublicEventById(HttpServletRequest httpServletRequest, Long id) {

        Event event = eventRepository.findById(id).orElseThrow(
                () -> new NotFoundRecordInBDException(String.format("Не найдено событие в БД с ID = %d.", id)));

        if (event.getState() != State.PUBLISHED)
            throw new NotFoundException("Посмотреть можно только опубликованное событие.");


        Optional<StatsDto> stat = statClient.getStats(event.getCreatedOn(),
                        LocalDateTime.now(), "/events/" + event.getId(), false).stream()
                .findFirst();

        EventFullDto result = EventMapper.mapToFullDto(event, stat.isPresent() ? stat.get().getHits() : 0L);

        List<ParticipationRequest> confirmedRequests = requestService.prepareConfirmedRequests(List.of(event.getId())).get(event.getId());
        result.setConfirmedRequests(confirmedRequests != null ? confirmedRequests.size() : 0);

        try {
            EndpointHitDto requestBody = EndpointHitDto
                    .builder().app(serviceName)
                    .ip(httpServletRequest.getRemoteAddr())
                    .uri(httpServletRequest.getRequestURI())
                    .timestamp(LocalDateTime.now())
                    .build();

            statClient.saveHit(requestBody);
            log.info("Сохранение статистики.");
        } catch (SaveStatsException e) {
            log.error("Не удалось сохранить статистику.");
        }

        return result;
    }

    // admin Эндпоинт возвращает полную информацию обо всех событиях подходящих под переданные условия
    @Override
    public List<EventFullDto> getEventsForAdmin(EventAdminFilter input) {

        Pageable pageable = PageRequest.of(input.getFrom() / input.getSize(), input.getSize());
        List<Event> events;
        List<String> states = List.of();

        if (input.getStates() != null) {
            states = input.getStates()
                    .stream()
                    .map(Enum::toString)
                    .toList();
        }
        if (input.getRangeStart() == null && input.getRangeEnd() == null) {
            events = eventRepository
                    .findAllByInitiatorIdInAndStateInAndCategoryIdInAndCreatedOnAfterOrderByCreatedOn(input.getUsers(), states, input.getCategories(), LocalDateTime.now(), pageable);
        } else {
            events = eventRepository
                    .findAllByInitiatorIdInAndStateInAndCategoryIdInAndCreatedOnAfterAndCreatedOnBeforeOrderByCreatedOn(input.getUsers(), states, input.getCategories(), input.getRangeStart(), input.getRangeEnd(), pageable);
        }
        if (events == null || events.isEmpty()) {
            return new ArrayList<EventFullDto>();
        }
        List<String> urisList = events
                .stream()
                .map(event -> "/events/" + event.getId())
                .toList();

        String uris = String.join(", ", urisList);

        List<StatsDto> statsList = statClient.getStats(events.getFirst().getCreatedOn(),
                LocalDateTime.now(), uris, false);
        var ids = events.stream().map(Event::getId).toList();
        Map<Long, List<ParticipationRequest>> confirmedRequests = requestService.prepareConfirmedRequests(ids);
        var result = events.stream().map(event -> {

                    Optional<StatsDto> stat = statsList.stream()
                            .filter(statsDto -> statsDto.getUri().equals("/events/" + event.getId()))
                            .findFirst();
                    var requests = confirmedRequests.get(event.getId());
                    var r =  EventMapper.mapToFullDto(event, stat.isPresent() ? stat.get().getHits() : 0L);

                    r.setConfirmedRequests(requests != null ? requests.size() : 0);
                    return r;
                })
                .toList();

        return result;

    }

    // admin Редактирование данных любого события администратором. Валидация данных не требуется
    @Transactional
    @Override
    public EventFullDto updateEventAdmin(Long eventId, UpdateEventAdminRequest updateEventAdminRequest) {

        Event event = eventRepository.findById(eventId).orElseThrow(
                () -> new NotFoundRecordInBDException(String.format("Не найдено событие в БД с ID = %d.", eventId)));

        checkStateAction(event, updateEventAdminRequest);

        if (updateEventAdminRequest.getAnnotation() != null && !updateEventAdminRequest.getAnnotation().isBlank()) {
            event.setAnnotation(updateEventAdminRequest.getAnnotation());
        }
        if (updateEventAdminRequest.getCategory() != null) {
            event.getCategory().setId(updateEventAdminRequest.getCategory());
        }
        if (updateEventAdminRequest.getDescription() != null && !updateEventAdminRequest.getDescription().isBlank()) {
            event.setDescription(updateEventAdminRequest.getDescription());
        }
        if (updateEventAdminRequest.getEventDate() != null) {
            checkDateEvent(updateEventAdminRequest.getEventDate());
            event.setEventDate(updateEventAdminRequest.getEventDate());
        }
        if (updateEventAdminRequest.getLocation() != null) {
            event.setLat(updateEventAdminRequest.getLocation().getLat());
            event.setLon(updateEventAdminRequest.getLocation().getLon());
        }
        if (updateEventAdminRequest.getPaid() != null) {
            event.setPaid(updateEventAdminRequest.getPaid());
        }
        if (updateEventAdminRequest.getParticipantLimit() != null) {
            event.setParticipantLimit(updateEventAdminRequest.getParticipantLimit());
        }
        if (updateEventAdminRequest.getRequestModeration() != null) {
            event.setRequestModeration(updateEventAdminRequest.getRequestModeration());
        }
        if (StateAction.REJECT_EVENT.equals(updateEventAdminRequest.getStateAction())) {
            event.setState(State.CANCELED);
        }
        if (StateAction.CANCEL_REVIEW.equals(updateEventAdminRequest.getStateAction())) {
            event.setState(State.CANCELED);
        }
        if (StateAction.SEND_TO_REVIEW.equals(updateEventAdminRequest.getStateAction())) {
            event.setState(State.PENDING);
        }
        if (StateAction.PUBLISH_EVENT.equals(updateEventAdminRequest.getStateAction())) {
            event.setState(State.PUBLISHED);
            event.setPublishedOn(LocalDateTime.now());
        }
        if (updateEventAdminRequest.getTitle() != null && !updateEventAdminRequest.getTitle().isBlank()) {
            event.setTitle(updateEventAdminRequest.getTitle());
        }
        event = eventRepository.save(event);

        Optional<StatsDto> stat = statClient.getStats(event.getCreatedOn(),
                        LocalDateTime.now(), "/events/" + event.getId(), false).stream()
                .findFirst();

        EventFullDto result = EventMapper.mapToFullDto(event, stat.isPresent() ? stat.get().getHits() : 0L);

        List<ParticipationRequest> confirmedRequests = requestService.prepareConfirmedRequests(List.of(event.getId())).get(event.getId());
        result.setConfirmedRequests(confirmedRequests != null ? confirmedRequests.size() : 0);

        return result;
    }

    @Override
    public List<ParticipationRequestDto> getRequestsOfUserEvent(Long userId, Long eventId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("Пользователь не найден");
        }
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new NotFoundException("Событие не найдено"));
        if (!Objects.equals(event.getInitiator().getId(), userId)) {
            log.error("userId отличается от id создателя события");
            throw new ValidationException("Событие должно быть создано текущим пользователем");
        }
        return ParticipationRequestMapper
                .toParticipationRequestDto(requestRepository.findAllByEventInitiatorIdAndEventId(userId, eventId));
    }

    @Override
    @Transactional
    public List<ParticipationRequestDto> updateRequestsStatus(EventRequestStatusUpdateRequest updateRequest,
                                                              Long userId, Long eventId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("Пользователь не найден");
        }
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new NotFoundException("Событие не найдено"));
        if (!Objects.equals(event.getInitiator().getId(), userId)) {
            throw new ValidationException("Событие должно быть создано текущим пользователем");
        }
        if (Objects.equals(event.getConfirmedRequests(), event.getParticipantLimit())) {
            throw new ConflictDataException("Лимит участников уже исчерпан");
        }
        List<Long> requestIds = updateRequest.getRequestIds();
        log.info("Получили список id запросов на участие: {}", requestIds);
        List<ParticipationRequest> requestList = requestRepository.findAllById(requestIds);
        if (requestList.stream().anyMatch(request -> !Objects.equals(request.getEvent().getId(), eventId))) {
            throw new ValidationException("Все запросы должны принадлежать одному событию");
        }
        if (event.getRequestModeration() && event.getParticipantLimit() != 0) {
            switch (updateRequest.getStatus()) {
                case CONFIRMED -> requestList.forEach(request -> {
                    if (request.getStatus() != Status.PENDING) {
                        throw new ConflictDataException("Можно изменить только статус PENDING");
                    }
                    if (Objects.equals(event.getConfirmedRequests(), event.getParticipantLimit())) {
                        request.setStatus(Status.CANCELED);
                    } else {
                        request.setStatus(Status.CONFIRMED);
                        Integer confirmedRequests = event.getConfirmedRequests();
                        event.setConfirmedRequests(++confirmedRequests);
                    }
                });
                case REJECTED -> requestList.forEach(request -> {
                    if (request.getStatus() != Status.PENDING) {
                        throw new ConflictDataException("Можно изменить только статус PENDING");
                    }
                    request.setStatus(Status.CANCELED);
                });
            }
        }
        return ParticipationRequestMapper.toParticipationRequestDto(requestList);
    }

    private void checkFields(NewEventDto dto) {
        if (dto.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ValidationException("Дата начала события должна быть позже чем через 2 часа от текущего времени");
        }

        if (dto.getPaid() == null) {
            dto.setPaid(false);
        }
        if (dto.getRequestModeration() == null) {
            dto.setRequestModeration(true);
        }
        if (dto.getParticipantLimit() == null) {
            dto.setParticipantLimit(0);
        }
    }

    private void checkDateEvent(LocalDateTime newDateTime) {

        LocalDateTime now = LocalDateTime.now().plusHours(1);
        if (now.isAfter(newDateTime)) {
            throw new InvalidDateTimeException(String.format("Дата начала события должна быть позже текущего времени на %s ч.", 1));
        }
    }

    private void checkStateAction(Event oldEvent, UpdateEventAdminRequest newEvent) {

        if (newEvent.getStateAction() == StateAction.PUBLISH_EVENT) {
            if (oldEvent.getState() != State.PENDING) {
                throw new OperationFailedException("Невозможно опубликовать событие. Его можно " +
                        "опубликовать только в состоянии ожидания публикации.");
            }
        }
        if (newEvent.getStateAction() == StateAction.REJECT_EVENT) {
            if (oldEvent.getState() == State.PUBLISHED) {
                throw new OperationFailedException("Событие опубликовано, поэтому отменить его невозможно.");
            }
        }
    }

}
