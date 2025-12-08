<template>
  <section class="panel banner">
    <div>
      <h3 class="section-title">文本类型推断</h3>
      <p>
        当前类型：<strong>{{ currentLabel }}</strong>
      </p>
      <p v-if="classification">
        模型建议类型：<strong>{{ labelMap[classification.suggestedCategory] || classification.suggestedCategory }}</strong>
        （可信度 {{ (classification.confidence * 100).toFixed(1) }}%）
      </p>
      <div class="reasons" v-if="classification">
        <el-tag v-for="reason in classification.reasons" :key="reason" type="success">{{ reason }}</el-tag>
      </div>
    </div>
    <div class="actions">
      <el-select v-model="selectedCategory" placeholder="调整类型" size="large" style="width: 220px">
        <el-option label="战争纪实" value="warfare" />
        <el-option label="游记地理" value="travelogue" />
        <el-option label="人物传记" value="biography" />
        <el-option label="官职体系" value="official" />
        <el-option label="农书类" value="agriculture" />
        <el-option label="工艺技术" value="crafts" />
        <el-option label="其他" value="other" />
        <el-option label="待识别" value="unknown" />
      </el-select>
      <el-button type="primary" @click="$emit('update-category', selectedCategory)">保存类型</el-button>
      <el-button @click="$emit('classify')" :loading="loading">重新判定</el-button>
      <el-button type="warning" @click="$emit('auto-annotate')">自动标注示例</el-button>
    </div>
  </section>
</template>

<script setup>
import { watch, ref, computed } from "vue";

const props = defineProps({
  currentCategory: {
    type: String,
    default: ""
  },
  classification: {
    type: Object,
    default: null
  },
  loading: {
    type: Boolean,
    default: false
  }
});

defineEmits(["classify", "update-category", "auto-annotate"]);

const labelMap = {
  warfare: "战争纪实",
  travelogue: "游记地理",
  biography: "人物传记",
  official: "官职体系",
  agriculture: "农书类",
  crafts: "工艺技术",
  other: "其他",
  unknown: "待识别"
};

const selectedCategory = ref(props.currentCategory);
const currentLabel = computed(() => {
  if (!props.currentCategory) {
    return "待识别";
  }
  return labelMap[props.currentCategory] || props.currentCategory;
});

watch(
  () => props.currentCategory,
  (val) => {
    selectedCategory.value = val || "";
  },
  { immediate: true }
);
</script>

<style scoped>
.banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.reasons {
  margin-top: 12px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
