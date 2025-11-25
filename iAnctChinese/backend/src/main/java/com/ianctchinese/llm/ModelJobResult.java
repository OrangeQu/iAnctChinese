package com.ianctchinese.llm;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ModelJobResult {

  String status;
  String resultPayload;
  String message;
}
