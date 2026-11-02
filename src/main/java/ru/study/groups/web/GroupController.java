package ru.study.groups.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.study.groups.domain.*;
import ru.study.groups.repository.GroupRepository;
import ru.study.groups.service.GroupService;
import ru.study.groups.web.Requests.*;
import java.util.Map;

@RestController @RequestMapping("/api/groups") @RequiredArgsConstructor
public class GroupController {
    private final GroupService service;
    @GetMapping public GroupRepository.Page list(
        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
        @RequestParam(defaultValue = "id") String sort, @RequestParam(defaultValue = "asc") String direction,
        @RequestParam(defaultValue = "") String filter, @RequestParam(required = false) String value) {
        return service.page(page, size, sort, direction, filter, value);
    }
    @GetMapping("/{id}") public StudyGroup get(@PathVariable long id) { return service.get(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public StudyGroup create(@Valid @RequestBody GroupInput input) { return service.create(input); }
    @PutMapping("/{id}") public StudyGroup update(@PathVariable long id, @Valid @RequestBody GroupInput input) { return service.update(id, input); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable long id, @RequestParam long version) { service.delete(id, version); }
    @GetMapping("/operations/sum") public Map<String,String> sum() { return Map.of("sum", service.sumShouldBeExpelled().toString()); }
    @GetMapping("/operations/max-semester") public Map<String,Object> max() {
        var result = new java.util.HashMap<String,Object>(); result.put("group", service.maxSemester()); return result;
    }
    @GetMapping("/operations/count-after") public Map<String,Long> count(@RequestParam Semester semester) { return Map.of("count", service.countAfter(semester)); }
    @PostMapping("/operations/transfer") public Map<String,Object> transfer(@Valid @RequestBody TransferInput input) { return service.transfer(input); }
    @PostMapping("/{id}/students") public StudyGroup addStudent(@PathVariable long id) { return service.addStudent(id); }
}
