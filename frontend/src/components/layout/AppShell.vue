<template>
  <div class="shell">
    <header class="global-bar">
      <div class="title">
        <span>古籍视界 · 智能古籍标注与知识图谱平台</span>
        <small>项目 | 类型 | 时间 | 范围 | 搜索 | 导出</small>
      </div>
      <div class="actions">
        <el-input
          v-model="keywords"
          placeholder="检索文言文 / 作者 / 标签"
          clearable
          prefix-icon="Search"
          size="large"
        />
        <el-button type="primary" @click="emitSearch" size="large">搜索</el-button>
        <el-button :loading="props.exporting" :disabled="!props.canExport" @click="$emit('export')">
          导出
        </el-button>
      </div>
    </header>
    <main class="content">
      <slot />
    </main>
  </div>
</template>

<script setup>
import { ref } from "vue";

const props = defineProps({
  exporting: {
    type: Boolean,
    default: false
  },
  canExport: {
    type: Boolean,
    default: false
  }
});
const emit = defineEmits(["search", "export"]);
const keywords = ref("");

const emitSearch = () => {
  emit("search", keywords.value);
};
</script>

<style scoped>
.shell {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.global-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 32px;
  border-bottom: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(12px);
}

.title {
  display: flex;
  flex-direction: column;
  font-size: 20px;
  font-weight: 600;
}

.title small {
  font-size: 12px;
  color: var(--muted);
}

.actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.content {
  flex: 1;
  padding: 24px;
}
</style>
