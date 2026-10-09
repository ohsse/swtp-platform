package com.mo.swtp.realtime;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import com.mo.swtp.realtime.stream.TelemetryStreamBroadcaster;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.utils.ContainerTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 7 SSE 최소 브로드캐스트 검증 — telemetry.raw 발행 → SSE 구독자 수신.
 * 봉투는 pass-through이므로 payload 스키마와 무관하게 유지되는 영구 테스트다.
 */
class TelemetryStreamIntegrationTest extends AbstractIntegrationTest {

    private static final String TOPIC = "telemetry.raw";

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    @Autowired
    private TelemetryStreamBroadcaster broadcaster;

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("발행된 봉투 JSON이 SSE 스트림으로 그대로 전달된다")
    void publishedEnvelopeIsBroadcastToSseSubscribers() throws Exception {
        // 1) 리스너 파티션 할당 대기 — latest 오프셋이라 할당 전 발행분은 유실된다 (계획 R5)
        for (MessageListenerContainer container : listenerRegistry.getListenerContainers()) {
            ContainerTestUtils.waitForAssignment(container, 1);
        }

        // 2) SSE 연결 — SseEmitter는 첫 이벤트 전송 때 응답 헤더를 flush하므로
        //    헤더 도착을 기다리지 않고, 서버 측 신호(구독자 수)로 emitter 등록을 확인한다
        int port = environment.getRequiredProperty("local.server.port", Integer.class);
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/realtime/telemetry/stream"))
                .header("Accept", "text/event-stream")
                .build();
        CompletableFuture<HttpResponse<InputStream>> responseFuture =
                client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
        awaitTrue(() -> broadcaster.subscriberCount() == 1, "SSE 구독 등록");

        // 3) 봉투 JSON 발행 (내용은 pass-through — 임의 구조여도 무방하다)
        String envelopeJson = "{\"eventType\":\"sample-measurement.recorded\",\"payload\":{\"tagId\":\"sse-tag\"}}";
        try (var producer = new KafkaProducer<String, String>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName()))) {
            producer.send(new ProducerRecord<>(TOPIC, "sse-tag", envelopeJson)).get(10, TimeUnit.SECONDS);
        }

        // 4) 첫 이벤트 전송이 헤더를 flush → 이제 응답이 도착한다
        HttpResponse<InputStream> response = responseFuture.get(30, TimeUnit.SECONDS);
        assertThat(response.statusCode()).isEqualTo(200);

        // 5) data 라인 수신 검증 — 스트림 읽기는 블로킹이므로 future + 타임아웃으로 감싼다
        CompletableFuture<String> dataLine = CompletableFuture.supplyAsync(() -> {
            try (Scanner scanner = new Scanner(response.body())) {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    if (line.startsWith("data:")) {
                        return line;
                    }
                }
                throw new IllegalStateException("data 라인 없이 스트림이 끝났다");
            }
        });

        assertThat(dataLine.get(30, TimeUnit.SECONDS)).contains(envelopeJson);
    }

    /** 조건이 참이 될 때까지 폴링한다 */
    private void awaitTrue(BooleanSupplier condition, String description) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError(description + " 대기 시간 초과");
    }
}
