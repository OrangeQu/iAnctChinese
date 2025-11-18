package com.ianctchinese.config;

import com.ianctchinese.model.ModelConfig;
import com.ianctchinese.repository.ModelConfigRepository;
import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ModelConfigInitializer {

  private final ModelConfigRepository modelConfigRepository;

  @PostConstruct
  public void initializeDefaultModels() {
    // 不再依赖外部 MySQL 命令行，而是在应用启动时自动重置模型配置表
    log.info("重置模型配置：清空表并写入默认模型");
    modelConfigRepository.deleteAll();

    List<ModelConfig> defaultModels = List.of(
        // 思考类模型（耗时较长）
        ModelConfig.builder()
            .modelKey("Pro/deepseek-ai/DeepSeek-R1")
            .displayName("🧠 DeepSeek-R1 Pro（深度思考）")
            .provider("DeepSeek")
            .enabled(true)
            .sortOrder(1)
            .description("DeepSeek R1 Pro版本，具有深度推理能力，适合复杂分析（响应较慢）")
            .build(),
        ModelConfig.builder()
            .modelKey("deepseek-ai/DeepSeek-R1")
            .displayName("🧠 DeepSeek-R1（深度思考）")
            .provider("DeepSeek")
            .enabled(true)
            .sortOrder(2)
            .description("DeepSeek R1 推理模型，具有深度思考能力（响应较慢）")
            .build(),
        ModelConfig.builder()
            .modelKey("Qwen/QwQ-32B")
            .displayName("🧠 通义千问 QwQ-32B（深度思考）")
            .provider("阿里云")
            .enabled(true)
            .sortOrder(3)
            .description("通义千问深度思考模型，适合复杂推理（响应较慢）")
            .build(),
        ModelConfig.builder()
            .modelKey("Qwen/Qwen3-30B-A3B-Thinking-2507")
            .displayName("🧠 Qwen3-30B Thinking（深度思考）")
            .provider("阿里云")
            .enabled(true)
            .sortOrder(4)
            .description("Qwen3 思考模型，具备深度分析能力（响应较慢）")
            .build(),
        ModelConfig.builder()
            .modelKey("Qwen/Qwen3-235B-A22B-Thinking-2507")
            .displayName("🧠 Qwen3-235B Thinking（深度思考）")
            .provider("阿里云")
            .enabled(true)
            .sortOrder(5)
            .description("Qwen3 大规模思考模型，最强推理能力（响应很慢）")
            .build(),
        ModelConfig.builder()
            .modelKey("THUDM/GLM-Z1-Rumination-32B-0414")
            .displayName("🧠 GLM-Z1 Rumination（深度思考）")
            .provider("智谱AI")
            .enabled(true)
            .sortOrder(6)
            .description("智谱GLM-Z1 反思模型，具备深度思考能力（响应较慢）")
            .build(),
        
        // 高性能模型（推荐使用）
        ModelConfig.builder()
            .modelKey("Pro/deepseek-ai/DeepSeek-V3.2-Exp")
            .displayName("DeepSeek-V3.2 Exp Pro")
            .provider("DeepSeek")
            .enabled(true)
            .sortOrder(10)
            .description("DeepSeek V3.2 实验版 Pro，最新高性能模型")
            .build(),
        ModelConfig.builder()
            .modelKey("deepseek-ai/DeepSeek-V3.2-Exp")
            .displayName("DeepSeek-V3.2 Exp")
            .provider("DeepSeek")
            .enabled(true)
            .sortOrder(11)
            .description("DeepSeek V3.2 实验版，高性能推理")
            .build(),
        ModelConfig.builder()
            .modelKey("Pro/deepseek-ai/DeepSeek-V3")
            .displayName("DeepSeek-V3 Pro")
            .provider("DeepSeek")
            .enabled(true)
            .sortOrder(12)
            .description("DeepSeek V3 Pro版本，性能强大")
            .build(),
        ModelConfig.builder()
            .modelKey("deepseek-ai/DeepSeek-V3")
            .displayName("DeepSeek-V3")
            .provider("DeepSeek")
            .enabled(true)
            .sortOrder(13)
            .description("DeepSeek V3 标准版")
            .build(),
        ModelConfig.builder()
            .modelKey("Qwen/Qwen3-235B-A22B-Instruct-2507")
            .displayName("通义千问 Qwen3-235B")
            .provider("阿里云")
            .enabled(true)
            .sortOrder(14)
            .description("通义千问3大规模模型，性能最强")
            .build(),
        ModelConfig.builder()
            .modelKey("Qwen/Qwen3-30B-A3B-Instruct-2507")
            .displayName("通义千问 Qwen3-30B")
            .provider("阿里云")
            .enabled(true)
            .sortOrder(15)
            .description("通义千问3中等规模，性价比高")
            .build(),
        ModelConfig.builder()
            .modelKey("zai-org/GLM-4.6")
            .displayName("智谱 GLM-4.6")
            .provider("智谱AI")
            .enabled(true)
            .sortOrder(16)
            .description("智谱GLM-4.6最新版本")
            .build(),
        ModelConfig.builder()
            .modelKey("zai-org/GLM-4.5")
            .displayName("智谱 GLM-4.5")
            .provider("智谱AI")
            .enabled(true)
            .sortOrder(17)
            .description("智谱GLM-4.5标准版")
            .build(),
        ModelConfig.builder()
            .modelKey("moonshotai/Kimi-K2-Instruct-0905")
            .displayName("Kimi-K2")
            .provider("月之暗面")
            .enabled(true)
            .sortOrder(18)
            .description("月之暗面 Kimi-K2 智能助手")
            .build(),
        
        // 快速轻量模型
        ModelConfig.builder()
            .modelKey("inclusionAI/Ling-flash-2.0")
            .displayName("Ling Flash 2.0（极速）")
            .provider("InclusionAI")
            .enabled(true)
            .sortOrder(20)
            .description("Ling Flash 2.0，极速响应")
            .build(),
        ModelConfig.builder()
            .modelKey("inclusionAI/Ling-mini-2.0")
            .displayName("Ling Mini 2.0（轻量）")
            .provider("InclusionAI")
            .enabled(true)
            .sortOrder(21)
            .description("Ling Mini 2.0，轻量快速")
            .build(),
        ModelConfig.builder()
            .modelKey("Qwen/Qwen2.5-7B-Instruct")
            .displayName("通义千问 2.5-7B")
            .provider("阿里云")
            .enabled(true)
            .sortOrder(22)
            .description("通义千问2.5轻量版，快速响应")
            .build(),
        ModelConfig.builder()
            .modelKey("THUDM/glm-4-9b-chat")
            .displayName("智谱 GLM-4-9B")
            .provider("智谱AI")
            .enabled(true)
            .sortOrder(23)
            .description("智谱GLM-4轻量版")
            .build(),
        
        // 代码专用模型
        ModelConfig.builder()
            .modelKey("Qwen/Qwen3-Coder-480B-A35B-Instruct")
            .displayName("通义千问 Coder-480B（代码专用）")
            .provider("阿里云")
            .enabled(true)
            .sortOrder(30)
            .description("通义千问超大规模代码模型")
            .build(),
        ModelConfig.builder()
            .modelKey("Qwen/Qwen2.5-Coder-32B-Instruct")
            .displayName("通义千问 Coder-32B（代码专用）")
            .provider("阿里云")
            .enabled(true)
            .sortOrder(31)
            .description("通义千问代码模型，适合编程任务")
            .build(),
        
        // 长文本模型
        ModelConfig.builder()
            .modelKey("Tongyi-Zhiwen/QwenLong-L1-32B")
            .displayName("通义千问 Long（长文本）")
            .provider("阿里云")
            .enabled(true)
            .sortOrder(40)
            .description("通义千问长文本模型，支持超长上下文")
            .build(),
        ModelConfig.builder()
            .modelKey("Qwen/Qwen2.5-72B-Instruct-128K")
            .displayName("通义千问 2.5-72B-128K（长文本）")
            .provider("阿里云")
            .enabled(true)
            .sortOrder(41)
            .description("通义千问2.5长上下文版本，支持128K tokens")
            .build()
    );

    modelConfigRepository.saveAll(defaultModels);
    log.info("已初始化 {} 个默认模型配置", defaultModels.size());
  }
}
