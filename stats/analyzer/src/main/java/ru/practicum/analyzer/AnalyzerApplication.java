package ru.practicum.analyzer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import ru.practicum.analyzer.service.SimilarityProcessor;
import ru.practicum.analyzer.service.UserActionProcessor;

@SpringBootApplication
@Slf4j
public class AnalyzerApplication {
    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(AnalyzerApplication.class, args);

        UserActionProcessor userActionProcessor = context.getBean(UserActionProcessor.class);
        SimilarityProcessor similarityProcessor = context.getBean(SimilarityProcessor.class);

        Thread userThread = new Thread(userActionProcessor, "user-action-processor");
        Thread simThread = new Thread(similarityProcessor, "similarity-processor");

        userThread.start();
        simThread.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down Analyzer processors...");
            userActionProcessor.shutdown();
            similarityProcessor.shutdown();
            try {
                userThread.join(5000);
                simThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "analyzer-shutdown-hook"));
    }
}