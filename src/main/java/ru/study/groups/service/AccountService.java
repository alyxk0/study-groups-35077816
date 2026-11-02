package ru.study.groups.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.study.groups.domain.UserAccount;
import ru.study.groups.repository.ObjectRepository;
import ru.study.groups.web.Requests.Registration;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service @RequiredArgsConstructor
public class AccountService {
    private final ObjectRepository objects;
    private final PasswordEncoder encoder;
    @Transactional
    public void register(Registration input) {
        String username = input.username().toLowerCase(Locale.ROOT);
        if (objects.user(username) != null) throw BusinessException.conflict("Этот логин уже занят");
        if (input.password().getBytes(StandardCharsets.UTF_8).length > 72) throw BusinessException.invalid("Пароль должен занимать не больше 72 байт UTF-8");
        var user = new UserAccount();
        user.setUsername(username); user.setPasswordHash(encoder.encode(input.password()));
        objects.persist(user); objects.flush();
    }
    @Transactional(readOnly = true) public UserAccount find(String username) {
        return objects.user(username.toLowerCase(Locale.ROOT));
    }
}
