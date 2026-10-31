package ru.practicum.analyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import ru.practicum.analyzer.service.SimilarityProcessor;
import ru.practicum.analyzer.service.UserActionProcessor;

@SpringBootApplication
public class AnalyzerApplication {
    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(AnalyzerApplication.class, args);

        UserActionProcessor userActionProcessor = context.getBean(UserActionProcessor.class);
        SimilarityProcessor similarityProcessor = context.getBean(SimilarityProcessor.class);

        new Thread(userActionProcessor).start();
        new Thread(similarityProcessor).start();
    }
}