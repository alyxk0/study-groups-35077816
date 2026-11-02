package ru.study.groups.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ru.study.groups.domain.*;
import ru.study.groups.service.*;
import ru.study.groups.web.Requests.Registration;
import java.util.Map;

@RestController @RequestMapping("/api") @RequiredArgsConstructor
public class SessionController {
    private final AccountService accounts;
    private final LiveUpdates updates;
    @GetMapping("/session") public Map<String,Object> session(Authentication authentication, CsrfToken token) {
        boolean authenticated = authentication != null && !(authentication instanceof AnonymousAuthenticationToken);
        return Map.of("authenticated", authenticated, "username", authenticated ? authentication.getName() : "",
            "csrfToken", token.getToken(), "csrfHeader", token.getHeaderName());
    }
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Boolean> register(@Valid @RequestBody Registration registration) { accounts.register(registration); return Map.of("ok", true); }
    @GetMapping("/enums") public Map<String,Object> enums() {
        return Map.of("forms", FormOfEducation.values(), "semesters", Semester.values(), "colors", Color.values(), "countries", Country.values());
    }
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE) public SseEmitter events() { return updates.connect(); }
}
