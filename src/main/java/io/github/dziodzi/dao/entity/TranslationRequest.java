package io.github.dziodzi.dao.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "translation_request")
public class TranslationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Basic
    @Column(name = "ip_address", nullable = true, length = 45)
    private String ipAddress;

    @Basic
    @Column(name = "input_lang", nullable = true, length = 2)
    private String inputLang;

    @Basic
    @Column(name = "input_text", nullable = true, length = 100)
    private String inputText;

    @Basic
    @Column(name = "output_lang", nullable = true, length = 2)
    private String outputLang;

    @Basic
    @Column(name = "date_time", nullable = false)
    private LocalDateTime dateTime;

    public TranslationRequest() {
        this.dateTime = LocalDateTime.now();
    }

    public TranslationRequest(String ipAddress, String inputLang, String inputText, String outputLang) {
        this();
        this.ipAddress = ipAddress;
        this.inputLang = inputLang;
        this.inputText = inputText;
        this.outputLang = outputLang;
    }

    @Override
    public String toString() {
        return id + " " + ipAddress + " " + inputLang + " " + inputText + " " + outputLang;
    }
}
