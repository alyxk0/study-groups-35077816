package ru.study.groups.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LiveUpdates {
    private final Set<SseEmitter> clients = ConcurrentHashMap.newKeySet();
    public SseEmitter connect() {
        var emitter = new SseEmitter(1800000L);
        clients.add(emitter);
        emitter.onCompletion(() -> clients.remove(emitter));
        emitter.onTimeout(() -> { clients.remove(emitter); emitter.complete(); });
        emitter.onError(error -> clients.remove(emitter));
        send(emitter, "ready", "connected");
        return emitter;
    }
    @TransactionalEventListener
    public void changed(ChangeEvent event) { clients.forEach(client -> send(client, "change", event)); }
    @Scheduled(fixedRate = 15000)
    public void heartbeat() { clients.forEach(client -> send(client, "heartbeat", "alive")); }
    private void send(SseEmitter client, String name, Object data) {
        try { client.send(SseEmitter.event().name(name).data(data)); }
        catch (IOException | IllegalStateException ex) { clients.remove(client); client.complete(); }
    }
}
