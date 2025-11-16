<template>
  <div>
    <el-button type="primary" @click="drawerVisible = true" size="large">上传文言文</el-button>
    <el-drawer v-model="drawerVisible" title="上传古籍文本" size="40%">
      <el-form :model="form" label-width="96px" :disabled="saving">
        <el-form-item label="标题">
          <el-input v-model="form.title" placeholder="如《赤壁赋》" />
        </el-form-item>
        <el-form-item label="作者">
          <el-input v-model="form.author" placeholder="作者/编者" />
        </el-form-item>
        <el-form-item label="时代">
          <el-input v-model="form.era" placeholder="如 宋" />
        </el-form-item>
        <el-form-item label="正文">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="10"
            placeholder="粘贴完整文言文内容..."
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleSubmit">上传并分析</el-button>
          <el-button @click="drawerVisible = false">取消</el-button>
        </el-form-item>
      </el-form>
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref } from "vue";
import { useTextStore } from "@/store/textStore";
import { ElMessage } from "element-plus";

const store = useTextStore();
const drawerVisible = ref(false);
const saving = ref(false);
const form = reactive({
  title: "",
  author: "",
  era: "",
  content: ""
});

const resetForm = () => {
  form.title = "";
  form.author = "";
  form.era = "";
  form.content = "";
};

const handleSubmit = async () => {
  if (!form.title || !form.content) {
    ElMessage.warning("请填写标题与正文内容");
    return;
  }
  saving.value = true;
  try {
    await store.uploadNewText({ ...form });
    ElMessage.success("上传成功，已载入到结构标注视图");
    drawerVisible.value = false;
    resetForm();
  } finally {
    saving.value = false;
  }
};
</script>
