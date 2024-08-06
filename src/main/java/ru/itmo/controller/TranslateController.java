package ru.itmo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.itmo.dao.TranslationRequest;
import ru.itmo.dao.TranslationRequestRepository;
import ru.itmo.dao.TranslatedTextRepository;
import ru.itmo.dao.TranslatedText;
import ru.itmo.exceptions.ApiRequestException;
import ru.itmo.exceptions.TranslationException;
import ru.itmo.service.TranslateService;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/translate")
public class TranslateController {

    private final TranslateService translateService;
    private final TranslationRequestRepository requestRepository;
    private final TranslatedTextRepository translationRepository;

    @Autowired
    public TranslateController(TranslateService translateService, TranslationRequestRepository requestRepository, TranslatedTextRepository translationRepository) {
        this.translateService = translateService;
        this.requestRepository = requestRepository;
        this.translationRepository = translationRepository;
    }

    @PostMapping
    @Operation(summary = "Translate text from one language to another", description = "Provides translation of a given text from source language to target language.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful translation"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<String> translate(HttpServletRequest request,
                                            @Parameter(description = "Source language code") @RequestParam String sourceLang,
                                            @Parameter(description = "Target language code") @RequestParam String targetLang,
                                            @Parameter(description = "Text to translate") @RequestParam String text) {

        try {
            if (text.length() > 255) {
                return ResponseEntity.status(400).body("The length of the input string must not exceed 255 characters (currently " + text.length() + ").");
            }
            if (sourceLang == null || targetLang == null) {
                return ResponseEntity.status(400).body("Parameters sourceLang and targetLang are required.");
            }
            if (text.replaceAll("[\\s\\W_]+", "").isEmpty()) {
                return ResponseEntity.status(400).body("The input text can't be empty.");
            }

            String outputText;
            try {
                outputText = translateService.translate(sourceLang, targetLang, text);
            } catch (TranslationException e) {
                log.error("Error during translating text: {}", e.getMessage());
                return ResponseEntity.status(500).body(e.getMessage());
            }

            TranslationRequest requestModel = new TranslationRequest();
            requestModel.setIpAddress(request.getRemoteAddr());
            requestModel.setInputLang(sourceLang);
            requestModel.setInputText(text);
            requestModel.setOutputLang(targetLang);
            requestRepository.save(requestModel);

            if (outputText.length() > 256) {
                int start = 0;
                List<String> parts = new ArrayList<>();
                while (start < outputText.length()) {
                    int end = Math.min(start + 255, outputText.length());
                    if (end < outputText.length() && outputText.charAt(end) != ' ') {
                        end = outputText.lastIndexOf(' ', end);
                    }

                    parts.add(outputText.substring(start, end).trim());
                    start = end + 1;
                }
                for (String part : parts) {
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

            log.info("Translation finished successfully!");

            return ResponseEntity.ok(outputText);

        } catch (Exception e) {
            log.error("Unexpected error occurred: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("Internal server error.");
        }
    }

    @ExceptionHandler(TranslationException.class)
    public ResponseEntity<String> handleTranslationException(TranslationException e) {
        log.error("TranslationException: {}", e.getMessage(), e);
        return ResponseEntity.status(500).body(e.getMessage());
    }

    @ExceptionHandler(ApiRequestException.class)
    public ResponseEntity<String> handleApiRequestException(ApiRequestException e) {
        log.error("ApiRequestException: {}", e.getMessage(), e);
        return ResponseEntity.status(500).body(e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception e) {
        log.error("Exception: {}", e.getMessage(), e);
        return ResponseEntity.status(500).body("Internal server error.");
    }
}
