package com.ianctchinese.llm;

import com.ianctchinese.model.ModelJob;

public interface LargeLanguageModelClient {

  ModelJobResult run(ModelJob job);
}
