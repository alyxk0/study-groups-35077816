package ru.study.groups.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.study.groups.domain.*;
import ru.study.groups.service.*;
import ru.study.groups.web.Requests.*;
import java.util.List;

@RestController @RequestMapping("/api/references") @RequiredArgsConstructor
public class ReferenceController {
    private final ReferenceService service;
    @GetMapping("/{kind}") public List<?> list(@PathVariable String kind) { return service.all(type(kind)); }
    @GetMapping("/{kind}/{id}") public Object get(@PathVariable String kind, @PathVariable long id) { return service.get(type(kind), id); }
    @PostMapping("/coordinates") @ResponseStatus(HttpStatus.CREATED) public Coordinates coordinates(@Valid @RequestBody CoordinatesInput input) { return service.coordinates(null, input); }
    @PutMapping("/coordinates/{id}") public Coordinates coordinates(@PathVariable long id, @Valid @RequestBody CoordinatesInput input) { return service.coordinates(id, input); }
    @PostMapping("/locations") @ResponseStatus(HttpStatus.CREATED) public Location location(@Valid @RequestBody LocationInput input) { return service.location(null, input); }
    @PutMapping("/locations/{id}") public Location location(@PathVariable long id, @Valid @RequestBody LocationInput input) { return service.location(id, input); }
    @PostMapping("/people") @ResponseStatus(HttpStatus.CREATED) public Person person(@Valid @RequestBody PersonInput input) { return service.person(null, input); }
    @PutMapping("/people/{id}") public Person person(@PathVariable long id, @Valid @RequestBody PersonInput input) { return service.person(id, input); }
    @DeleteMapping("/{kind}/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable String kind, @PathVariable long id, @RequestParam long version) { service.delete(type(kind), id, version); }
    private Class<? extends BaseEntity> type(String kind) {
        return switch (kind) {
            case "coordinates" -> Coordinates.class;
            case "locations" -> Location.class;
            case "people" -> Person.class;
            default -> throw BusinessException.missing();
        };
    }
}
