package io.github.dziodzi.controller;

import io.github.dziodzi.service.TranslateService;
import io.github.dziodzi.service.exceptions.TranslationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Slf4j
@RestController
@RequestMapping("/api/translate")
public class TranslateController {

    private final TranslateService translateService;

    @Autowired
    public TranslateController(TranslateService translateService) {
        this.translateService = translateService;
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
                                            @Parameter(description = "Source language code") @RequestParam @NotBlank @Size(min = 2, max = 2) String sourceLang,
                                            @Parameter(description = "Target language code") @RequestParam @NotBlank @Size(min = 2, max = 2) String targetLang,
                                            @Parameter(description = "Text to translate") @RequestParam @NotBlank @Size(max = 1000) String text) {

        log.info("Received parameters: sourceLang={}, targetLang={}, text.length()={}", sourceLang, targetLang, text.length());

        try {
            long startTime = System.currentTimeMillis();

            String outputText = translateService.translateAndSave(request.getRemoteAddr(), sourceLang, text, targetLang);

            long elapsedTime = System.currentTimeMillis() - startTime;
            log.info("Translation finished successfully in {} ms!", elapsedTime);
            return ResponseEntity.ok(outputText);
        } catch (TranslationException e) {
            log.error("Error during translation: {}", e.getMessage());
            return ResponseEntity.status(500).body(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error occurred: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("Internal server error.");
        }
    }
}
