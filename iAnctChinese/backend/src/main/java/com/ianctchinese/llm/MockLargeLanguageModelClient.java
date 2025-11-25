package com.ianctchinese.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ianctchinese.model.ModelJob;
import org.springframework.stereotype.Component;

@Component
public class MockLargeLanguageModelClient implements LargeLanguageModelClient {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public ModelJobResult run(ModelJob job) {
    try {
      String payload = switch (job.getJobType()) {
        case TYPE_CLASSIFICATION -> objectMapper.writeValueAsString(
            new MockClassification("warfare", 0.81));
        case ENTITY_EXTRACTION -> objectMapper.writeValueAsString(
            new MockEntityExtraction(3, 4));
        case RELATION_EXTRACTION -> objectMapper.writeValueAsString(
            new MockRelationExtraction(2));
        case PUNCTUATION -> objectMapper.writeValueAsString(
            new MockPunctuation("自动句读完成"));
        case SUMMARY -> objectMapper.writeValueAsString(
            new MockSummary("模型生成概括"));
      };
      return ModelJobResult.builder()
          .status("SUCCESS")
          .resultPayload(payload)
          .message("Mock inference finished")
          .build();
    } catch (JsonProcessingException e) {
      return ModelJobResult.builder()
          .status("FAILED")
          .message("Unable to serialize mock result")
          .build();
    }
  }

  private record MockClassification(String category, double confidence) {
  }

  private record MockEntityExtraction(int entities, int highlights) {
  }

  private record MockRelationExtraction(int relations) {
  }

  private record MockPunctuation(String message) {
  }

  private record MockSummary(String summary) {
  }
}
