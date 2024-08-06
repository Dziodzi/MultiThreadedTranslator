package ru.itmo.dao;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Entity
@Getter
@Setter
public class TranslationRequest {

    private static final DateTimeFormatter FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss:SSS");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String ipAddress;
    private String inputLang;
    private String inputText;
    private String outputLang;
    private String dateTime = FILE_NAME_FORMATTER.format(LocalDateTime.now());
}
