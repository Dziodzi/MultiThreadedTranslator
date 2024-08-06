package ru.itmo.service;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.itmo.exceptions.ApiRequestException;
import ru.itmo.exceptions.TranslationException;

import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TranslateService {

    @Value("${translate.api.url}")
    private String apiUrl;

    private final String apiKey = Dotenv.load().get("API_KEY");
    private final String folderId = Dotenv.load().get("FOLDER_ID");

    private final RestTemplate restTemplate;
    private final ExecutorService executorService;

    public TranslateService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.executorService = Executors.newFixedThreadPool(10);
    }

    public String translate(String sourceLang, String targetLang, String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        String[] words = text.split("\\s+");

        try {
            List<Callable<String>> tasks = new ArrayList<>();
            for (String word : words) {
                tasks.add(() -> {
                    try {
                        Thread.sleep(400);
                        return translateWord(sourceLang, targetLang, word);
                    } catch (TranslationException e) {
                        log.error("Error translating word: {} - {}", word, e.getMessage());
                        throw new RuntimeException();
                    }
                });
            }

            List<Future<String>> futures = executorService.invokeAll(tasks);

            return futures.stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            log.error("Translation was interrupted.");
                            throw new TranslationException(e.getCause().getMessage());
                        } catch (ExecutionException e) {
                            log.error("Error retrieving result.");
                            throw new TranslationException(e.getCause().getMessage());
                        }
                    })
                    .collect(Collectors.joining(" "));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TranslationException("Translation was interrupted", e);
        }
    }

    private String translateWord(String sourceLang, String targetLang, String word) throws TranslationException {

        try {
            Map<String, Object> requestBody = new HashMap<>();
            //requestBody.put("folderId", folderId);
            requestBody.put("sourceLanguageCode", sourceLang);
            requestBody.put("targetLanguageCode", targetLang);
            requestBody.put("format", "PLAIN_TEXT");
            requestBody.put("texts", List.of(word));
            requestBody.put("speller", true);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Api-Key " + apiKey);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    requestEntity,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("Translated word: {}", word);
                Map<String, Object> responseBody = response.getBody();
                if (responseBody != null) {
                    List<Map<String, Object>> translations = (List<Map<String, Object>>) responseBody.get("translations");
                    if (translations != null) {
                        return translations.stream()
                                .map(translation -> (String) translation.get("text"))
                                .collect(Collectors.joining(" "));
                    }
                }
                throw new ApiRequestException("Failed to retrieve translated text from API response");
            } else {
                throw new ApiRequestException("Error accessing the resource: " + response.getStatusCode());
            }
        } catch (ApiRequestException e) {
            log.error("Thread: {} encountered an error while translating word: {}", Thread.currentThread().getName(), word);
            log.error("Error: {}", e.getMessage());
            throw new TranslationException("Error executing the translation API request", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(15, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                if (!executorService.awaitTermination(15, TimeUnit.SECONDS)) {
                    log.error("ExecutorService did not terminate within the allotted time.");
                }
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
