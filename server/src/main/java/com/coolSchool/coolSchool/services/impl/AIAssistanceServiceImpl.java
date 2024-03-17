package com.coolSchool.coolSchool.services.impl;

import com.coolSchool.coolSchool.exceptions.AI.ErrorProcessingAIResponseException;
import com.coolSchool.coolSchool.exceptions.AI.UnableToExtractContentFromAIResponseException;
import com.coolSchool.coolSchool.models.dto.common.CategoryDTO;
import com.coolSchool.coolSchool.services.AIAssistanceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class AIAssistanceServiceImpl implements AIAssistanceService {

    private final RestTemplate restTemplate;
    private final String openAIEndpoint = "https://api.openai.com/v1/chat/completions";
    private final String apiKey;
    private final MessageSource messageSource;
    private final ObjectMapper objectMapper;

    public AIAssistanceServiceImpl(RestTemplate restTemplate, @Value("${openai.api.key}") String apiKey, MessageSource messageSource, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.messageSource = messageSource;
        this.objectMapper = objectMapper;
    }

    @Override
    public String generateText(String inputText) {
        // Construct request body for generating text
        String requestBody = "{\"model\": \"gpt-3.5-turbo\", \"messages\": [{\"role\": \"user\", \"content\": \"" + inputText + "\"}]}";

        // Set headers and create request entity
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);
        HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);

        try {
            // Send POST request to OpenAI endpoint
            ResponseEntity<String> responseEntity = restTemplate.postForEntity(openAIEndpoint, requestEntity, String.class);

            // Process response and return generated text or error message
            if (responseEntity.getStatusCode() == HttpStatus.OK) {
                return responseEntity.getBody();
            } else {
                return "Error: " + responseEntity.getStatusCodeValue() + " - " + responseEntity.getBody();
            }
        } catch (Exception e) {
            return "Exception occurred: " + e.getMessage();
        }
    }

    @Override
    public String extractContent(String aiGeneratedContent) {
        try {
            // Parse JSON response to extract generated content
            JsonNode rootNode = objectMapper.readTree(aiGeneratedContent);
            JsonNode messageContent = rootNode.path("choices").get(0).path("message").path("content");
            if (!messageContent.isMissingNode()) {
                return messageContent.asText();
            } else {
                // Throw exception if content cannot be extracted
                throw new UnableToExtractContentFromAIResponseException(messageSource);
            }
        } catch (Exception e) {
            // Handle error while processing AI response
            throw new ErrorProcessingAIResponseException(messageSource);
        }
    }

    @Override
    public String analyzeContent(String prompt) throws JsonProcessingException {
        // Create JSON request body for content analysis
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode messageNode = objectMapper.createObjectNode();
        messageNode.put("role", "user");
        messageNode.put("content", prompt);

        ObjectNode rootNode = objectMapper.createObjectNode();
        rootNode.put("model", "gpt-3.5-turbo");
        rootNode.set("messages", objectMapper.createArrayNode().add(messageNode));

        String requestBody = objectMapper.writeValueAsString(rootNode);

        // Set headers and create request entity
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);
        HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);

        try {
            // Send POST request to OpenAI endpoint for content analysis
            ResponseEntity<String> responseEntity = restTemplate.postForEntity(openAIEndpoint, requestEntity, String.class);
            String responseBody = responseEntity.getBody();

            // Extract content from AI response and return
            if (responseBody != null) {
                return extractContent(responseBody);
            } else {
                throw new UnableToExtractContentFromAIResponseException(messageSource);
            }
        } catch (Exception e) {
            // Handle error while processing AI response
            throw new ErrorProcessingAIResponseException(messageSource);
        }
    }

    @Override
    public String buildPrompt(String blogContent, List<CategoryDTO> categories) {
        // Build prompt message for categorizing blog content
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Given the blog content:\n\n");
        promptBuilder.append(blogContent);
        promptBuilder.append("\n\nWhich of the following categories is most appropriate? Please provide the category name.\n\n");
        for (CategoryDTO category : categories) {
            promptBuilder.append("- ").append(category.getName()).append("\n");
        }
        return promptBuilder.toString();
    }

    @Override
    public CategoryDTO matchCategory(String aiResponse, List<CategoryDTO> categories) {
        // Match AI-generated response with a category from the provided list
        for (CategoryDTO category : categories) {
            if (aiResponse.trim().equalsIgnoreCase(category.getName().trim())) {
                return category;
            }
        }
        // Throw exception if no matching category is found
        throw new ErrorProcessingAIResponseException(messageSource);
    }
}


