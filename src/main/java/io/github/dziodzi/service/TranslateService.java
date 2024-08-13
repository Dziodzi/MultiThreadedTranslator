package io.github.dziodzi.service;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.dziodzi.dao.entity.TranslatedText;
import io.github.dziodzi.dao.repository.TranslatedTextRepository;
import io.github.dziodzi.dao.entity.TranslationRequest;
import io.github.dziodzi.dao.repository.TranslationRequestRepository;
import io.github.dziodzi.service.exceptions.ApiRequestException;
import io.github.dziodzi.service.exceptions.TranslationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service("translateService")
public class TranslateService {

    @Value("${translate.api.url}")
    private String apiUrl;

    private final String apiKey = Dotenv.load().get("API_KEY");

    private final RestTemplate restTemplate;
    private final ScheduledExecutorService scheduledExecutorService;

    private final TranslationRequestRepository requestRepository;
    private final TranslatedTextRepository translationRepository;

    @Autowired
    public TranslateService(RestTemplate restTemplate,
                            ScheduledExecutorService scheduledExecutorService,
                            TranslationRequestRepository requestRepository,
                            TranslatedTextRepository translationRepository) {
        this.restTemplate = restTemplate;
        this.scheduledExecutorService = scheduledExecutorService;
        this.requestRepository = requestRepository;
        this.translationRepository = translationRepository;
    }

    public String translateAndSave(String ipAddress, String sourceLang, String text, String targetLang) throws ApiRequestException {
        if (sourceLang == null || targetLang == null || text == null) {
            throw new ApiRequestException("Invalid input parameters.");
        }

        if (text.length() > 100) {
            throw new ApiRequestException("Too long text");
        }

        TranslationRequest requestModel = new TranslationRequest(ipAddress, sourceLang, text, targetLang);
        requestRepository.save(requestModel);

        String outputText = translate(sourceLang, targetLang, text);

        log.warn(outputText);
        scheduledExecutorService.schedule(() -> {
            if (outputText.length() > 100) {
                int start = 0;
                List<String> parts = new ArrayList<>();
                while (start < outputText.length()) {
                    int end = Math.min(start + 99, outputText.length());
                    if (end < outputText.length() && outputText.charAt(end) != ' ') {
                        end = outputText.lastIndexOf(' ', end);
                    }

                    parts.add(outputText.substring(start, end).trim());
                    start = end + 1;
                }
                for (String part : parts) {
                    log.warn(part);
                    TranslatedText translationModel = new TranslatedText();
                    translationModel.setRequestId(requestModel.getId());
                    translationModel.setOutputText(part);
                    translationRepository.save(translationModel);
                }
            } else {
                TranslatedText translationModel = new TranslatedText();
                translationModel.setRequestId(requestModel.getId());
                translationModel.setOutputText(outputText);
                translationRepository.save(translationModel);
            }

            log.info("Translation saved successfully after delay.");
        }, 100, TimeUnit.MILLISECONDS);

        return outputText;
    }

    public String translate(String sourceLang, String targetLang, String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        String[] words = text.split("\\s+");

        try {
            List<Callable<String>> tasks = Arrays.stream(words)
                    .map(word -> (Callable<String>) () -> {
                        try {
                            Thread.sleep(400);
                            return translateWord(sourceLang, targetLang, word);
                        } catch (TranslationException e) {
                            log.error("Error translating word '{}': {}", word, e.getMessage());
                            throw e;
                        }
                    })
                    .collect(Collectors.toList());

            List<Future<String>> futures = scheduledExecutorService.invokeAll(tasks);

            return futures.stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            log.error("Translation was interrupted.");
                            throw new TranslationException("Translation was interrupted", e);
                        } catch (ExecutionException e) {
                            log.error("Error retrieving result: {}", e.getCause().getMessage());
                            throw new TranslationException("Error retrieving result", e);
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
            requestBody.put("sourceLanguageCode", sourceLang);
            requestBody.put("targetLanguageCode", targetLang);
            requestBody.put("format", "PLAIN_TEXT");
            requestBody.put("texts", Collections.singletonList(word));
            requestBody.put("speller", true);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Api-Key " + apiKey);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(apiUrl, HttpMethod.POST, requestEntity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK) {
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
        } catch (Exception e) {
            log.error("Error translating word '{}': {}", word, e.getMessage());
            throw new TranslationException("Error executing the translation API request", e);
        }
    }

    private List<String> splitText(String text) {
        List<String> parts = new ArrayList<>();
        int partLength = 1000;
        for (int i = 0; i < text.length(); i += partLength) {
            parts.add(text.substring(i, Math.min(i + partLength, text.length())));
        }
        return parts;
    }

    @PreDestroy
    public void cleanup() {
        scheduledExecutorService.shutdown();
        try {
            if (!scheduledExecutorService.awaitTermination(15, TimeUnit.SECONDS)) {
                scheduledExecutorService.shutdownNow();
                if (!scheduledExecutorService.awaitTermination(15, TimeUnit.SECONDS)) {
                    log.error("ExecutorService did not terminate within the allotted time.");
                }
            }
        } catch (InterruptedException e) {
            scheduledExecutorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
