import client from "./client";

export const modelsApi = {
  // 获取所有启用的模型
  getAllEnabledModels() {
    // client 的 baseURL 已经是 "/api"，这里不要再加 "/api" 前缀
    return client.get("/models");
  },

  // 获取所有模型（包括禁用的）
  getAllModels() {
    return client.get("/models/all");
  },

  // 创建新模型配置
  createModel(modelConfig) {
    return client.post("/models", modelConfig);
  },

  // 更新模型配置
  updateModel(id, modelConfig) {
    return client.put(`/models/${id}`, modelConfig);
  },

  // 删除模型配置
  deleteModel(id) {
    return client.delete(`/models/${id}`);
  }
};
