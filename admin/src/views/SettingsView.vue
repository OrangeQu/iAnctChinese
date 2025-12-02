<template>
  <div class="panel view">
    <header class="toolbar">
      <div>
        <strong>系统设置</strong>
        <p>配置后台的 API 入口与地图 Token，占位示例</p>
      </div>
    </header>
    <el-form label-width="140px" :model="form" class="form">
      <el-form-item label="API 基础地址">
        <el-input v-model="form.baseUrl" placeholder="默认使用 /api 代理" />
      </el-form-item>
      <el-form-item label="Mapbox Token">
        <el-input v-model="form.mapToken" />
      </el-form-item>
      <el-form-item label="SiliconFlow 模型">
        <el-select v-model="form.model" placeholder="deepseek-ai/DeepSeek-V3">
          <el-option label="DeepSeek-V3" value="deepseek-ai/DeepSeek-V3" />
          <el-option label="Qwen2.5-72B" value="qwen2.5-72b" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="save">保存</el-button>
        <el-button @click="reset">重置</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { reactive } from "vue";
import { ElMessage } from "element-plus";

const form = reactive({
  baseUrl: "/api",
  mapToken: "",
  model: "deepseek-ai/DeepSeek-V3"
});

const save = () => {
  localStorage.setItem("admin_settings", JSON.stringify(form));
  ElMessage.success("已保存（本地存储示例）");
};

const reset = () => {
  form.baseUrl = "/api";
  form.mapToken = "";
  form.model = "deepseek-ai/DeepSeek-V3";
};
</script>

<style scoped>
.view {
  padding: 14px;
}

.form {
  max-width: 520px;
}
</style>
