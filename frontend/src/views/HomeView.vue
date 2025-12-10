<template>
  <div class="home-page">
    <nav class="navbar">
      <div class="navbar-container">
        <div class="navbar-brand">
          <img src="/favicon.png" alt="古籍视界" class="logo-icon" />
          <h1 class="logo-text">古籍视界</h1>
        </div>
        <div class="navbar-menu">
          <a href="#features" class="navbar-link">核心功能</a>
          <a href="#faq" class="navbar-link">常见问题</a>
          <template v-if="!authStore.isAuthenticated">
            <router-link to="/login" class="navbar-button">登录</router-link>
          </template>
          <template v-else>
            <router-link to="/projects" class="navbar-button navbar-ghost">项目管理</router-link>
            <router-link to="/documents" class="navbar-button">文档管理</router-link>
            <router-link to="/profile" class="navbar-button navbar-ghost">个人中心</router-link>
            <span class="navbar-user">{{ authStore.user?.username || '已登录' }}</span>
            <button class="navbar-button navbar-logout" @click="handleLogout">退出登录</button>
          </template>
        </div>
      </div>
    </nav>

    <section
      ref="heroRef"
      class="hero"
      :class="{ 'section-visible': heroVisible }"
    >
      <div class="hero-content hero-glass">
        <p class="hero-kicker">Ancient Text Intelligence</p>
        <h1 class="hero-title">让智能服务古籍研究</h1>
        <p class="hero-subtitle">
          从原文上传、实体关系标注，到可视化知识图谱与教学展示，古籍视界为课程与科研提供一站式数字化工作台。
        </p>
        <div class="hero-actions">
          <router-link to="/register" class="btn-primary">
            立即开始
          </router-link>
          <router-link to="/login" class="btn-secondary">
            已有账户
          </router-link>
        </div>
        <div class="hero-meta">
          <span class="hero-tag">实体 / 关系 / 句读 一体化标注</span>
          <span class="hero-tag">战争 / 游记 / 传记 智能类型推断</span>
          <span class="hero-tag">知识图谱 · 时间轴 · 地图 多视角展示</span>
        </div>
      </div>
      <div class="hero-decoration"></div>
    </section>

    <section
      id="features"
      ref="featuresRef"
      class="features"
      :class="{ 'section-visible': featuresVisible }"
    >
      <div class="features-inner">
        <h2 class="section-title">核心功能</h2>
        <p class="section-subtitle">
          围绕古籍教学与科研设计，从文本整理到可视化展示，完整覆盖教师与学生的日常工作流。
        </p>
        <div class="features-grid">
        <div class="feature-card">
          <div class="feature-icon feature-icon-1"></div>
          <h3 class="feature-title">实体标注</h3>
          <p class="feature-desc">
            智能识别人物、地点、时间等实体，支持手动标注与AI辅助标注
          </p>
        </div>
        <div class="feature-card">
          <div class="feature-icon feature-icon-2"></div>
          <h3 class="feature-title">关系抽取</h3>
          <p class="feature-desc">
            自动分析实体间关系，构建人物关系网络与事件关联
          </p>
        </div>
        <div class="feature-card">
          <div class="feature-icon feature-icon-3"></div>
          <h3 class="feature-title">句读标注</h3>
          <p class="feature-desc">
            智能句读辅助，快速完成古文断句与结构化处理
          </p>
        </div>
        <div class="feature-card">
          <div class="feature-icon feature-icon-4"></div>
          <h3 class="feature-title">知识图谱</h3>
          <p class="feature-desc">
            多维度可视化展示，包括关系图谱、时间轴、地图、统计分析
          </p>
        </div>
        <div class="feature-card">
          <div class="feature-icon feature-icon-5"></div>
          <h3 class="feature-title">智能分类</h3>
          <p class="feature-desc">
            自动识别古籍类型（战争、游记、传记等），推荐最佳可视化方式
          </p>
        </div>
        <div class="feature-card">
          <div class="feature-icon feature-icon-6"></div>
          <h3 class="feature-title">数据导出</h3>
          <p class="feature-desc">
            支持多格式导出，便于学术研究与成果分享
          </p>
        </div>
      </div>
    </div>
    </section>

    <section
      id="faq"
      ref="faqRef"
      class="faq"
      :class="{ 'section-visible': faqVisible }"
    >
      <h2 class="section-title">常见问题</h2>
      <div class="faq-list">
        <div class="faq-item">
          <h3 class="faq-question">平台适合哪些用户群体？</h3>
          <p class="faq-answer">
            古籍视界适合古籍研究者、数字人文学者、历史教学工作者以及对古籍感兴趣的学习者。
            无论您是需要标注大量古籍的研究者，还是希望可视化展示研究成果的教师，平台都能提供有力支持。
          </p>
        </div>
        <div class="faq-item">
          <h3 class="faq-question">如何保障数据安全？</h3>
          <p class="faq-answer">
            我们采用严格的数据加密与访问控制机制。用户数据存储在本地服务器，支持私有部署。
            所有标注成果归用户所有，平台不会未经授权使用您的数据。
          </p>
        </div>
        <div class="faq-item">
          <h3 class="faq-question">是否支持多人协作？</h3>
          <p class="faq-answer">
            平台支持项目级权限管理，您可以邀请团队成员共同标注，查看进度，审校成果。
            协作功能让古籍研究项目更加高效有序。
          </p>
        </div>
        <div class="faq-item">
          <h3 class="faq-question">如何开始使用？</h3>
          <p class="faq-answer">
            点击"立即开始"注册账号，登录后即可上传古籍文本，开始标注工作。
            平台提供详细的使用指南与示例项目，帮助您快速上手。
          </p>
        </div>
      </div>
    </section>

    <section
      ref="ctaRef"
      class="cta"
      :class="{ 'section-visible': ctaVisible }"
    >
      <div class="cta-content">
        <h2 class="cta-title">立即开启您的古籍研究之旅</h2>
        <p class="cta-text">
          在古籍视界中，您可以快速构建课程示例库、标注学生作业、整理科研素材，并通过知识图谱和时间轴将抽象的历史脉络直观呈现给课堂。
        </p>
        <router-link to="/register" class="btn-cta">
          立即注册
        </router-link>
      </div>
    </section>

    <footer class="footer">
      <div class="footer-content">
        <div class="footer-brand">
          <h3>古籍视界</h3>
          <p>智能古籍标注与知识图谱平台</p>
        </div>
        <div class="footer-links">
          <a href="#features">功能特色</a>
          <a href="#faq">常见问题</a>
          <router-link to="/login">登录</router-link>
          <router-link to="/register">注册</router-link>
        </div>
      </div>
      <div class="footer-bottom">
        <p>&copy; 2025 古籍视界. 保留所有权利</p>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/store/authStore';

const router = useRouter();
const authStore = useAuthStore();

const heroVisible = ref(false);
const featuresVisible = ref(false);
const faqVisible = ref(false);
const ctaVisible = ref(false);

const heroRef = ref(null);
const featuresRef = ref(null);
const faqRef = ref(null);
const ctaRef = ref(null);

let observer;

const handleLogout = () => {
  authStore.logout();
  router.push('/');
};

onMounted(() => {
  const options = {
    threshold: 0.2
  };

  observer = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (!entry.isIntersecting) {
        return;
      }
      if (entry.target === heroRef.value) {
        heroVisible.value = true;
      } else if (entry.target === featuresRef.value) {
        featuresVisible.value = true;
      } else if (entry.target === faqRef.value) {
        faqVisible.value = true;
      } else if (entry.target === ctaRef.value) {
        ctaVisible.value = true;
      }
      observer.unobserve(entry.target);
    });
  }, options);

  [heroRef, featuresRef, faqRef, ctaRef].forEach((sectionRef) => {
    if (sectionRef.value) {
      observer.observe(sectionRef.value);
    }
  });
});

onBeforeUnmount(() => {
  if (observer) {
    observer.disconnect();
  }
});
</script>

<style scoped>
.home-page {
  min-height: 100vh;
  background: #ffffff;
}

.hero,
.features,
.faq,
.cta {
  opacity: 0;
  transform: translateY(40px);
  transition: opacity 0.7s ease, transform 0.7s ease;
}

.section-visible {
  opacity: 1;
  transform: translateY(0);
}

.navbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
  z-index: 1000;
  padding: 0 40px;
}

.navbar-container {
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 70px;
}

.navbar-brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo-icon {
  width: 34px;
  height: 34px;
  border-radius: 12px;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.18);
}

.logo-text {
  font-size: 28px;
  font-weight: 700;
  color: #1a1a1a;
  margin: 0;
  letter-spacing: 3px;
}

.navbar-menu {
  display: flex;
  align-items: center;
  gap: 24px;
}

.navbar-user {
  font-size: 14px;
  color: #555;
}

.navbar-logout {
  border: none;
  cursor: pointer;
}

.navbar-link {
  color: #333;
  text-decoration: none;
  font-size: 16px;
  transition: color 0.3s;
}

.navbar-link:hover {
  color: #2f7df6;
}

.navbar-button {
  padding: 10px 24px;
  background: #2f7df6;
  color: white;
  text-decoration: none;
  border-radius: 8px;
  font-weight: 500;
  transition: background 0.3s;
}

.navbar-button:hover {
  background: #1d6be0;
}

.navbar-ghost {
  background: transparent;
  color: #2f7df6;
  border: 1px solid #2f7df6;
}

.navbar-ghost:hover {
  background: rgba(47, 125, 246, 0.1);
}

.hero {
  padding: 160px 40px 100px;
  text-align: center;
  position: relative;
  background: linear-gradient(135deg, #e6f2ff 0%, #c2e0ff 50%, #a4d4ff 100%);
  overflow: hidden;
}

.hero::before,
.hero::after {
  content: '';
  position: absolute;
  border-radius: 999px;
  background: radial-gradient(circle at 30% 30%, rgba(255, 255, 255, 0.9), transparent 60%);
  opacity: 0.7;
  pointer-events: none;
}

.hero::before {
  width: 520px;
  height: 520px;
  top: -160px;
  left: -120px;
}

.hero::after {
  width: 640px;
  height: 640px;
  bottom: -220px;
  right: -140px;
}

.hero-content {
  max-width: 800px;
  margin: 0 auto;
  position: relative;
  z-index: 1;
}

.hero-glass {
  background: rgba(255, 255, 255, 0.18);
  border-radius: 24px;
  padding: 40px 48px;
  border: 1px solid rgba(255, 255, 255, 0.45);
  box-shadow: 0 24px 80px rgba(15, 23, 42, 0.22);
  backdrop-filter: blur(22px);
}

.hero-kicker {
  font-size: 14px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: #3b82f6;
  margin-bottom: 12px;
}

.hero-title {
  font-size: 56px;
  font-weight: 800;
  color: #1a1a1a;
  margin: 0 0 24px 0;
  line-height: 1.2;
}

.hero-subtitle {
  font-size: 18px;
  color: #4b5563;
  margin: 0 0 32px 0;
  line-height: 1.7;
}

.hero-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-bottom: 20px;
}

.hero-meta {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 12px;
}

.hero-tag {
  font-size: 13px;
  color: #1f2933;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.85);
  border: 1px solid rgba(226, 232, 240, 0.9);
}

.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 16px 32px;
  background: #2f7df6;
  color: white;
  text-decoration: none;
  border-radius: 12px;
  font-size: 18px;
  font-weight: 600;
  transition: all 0.3s;
  box-shadow: 0 4px 15px rgba(47, 125, 246, 0.4);
}

.btn-primary:hover {
  background: #1d6be0;
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(47, 125, 246, 0.5);
}

.btn-secondary {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 16px 24px;
  background: transparent;
  color: #1d4ed8;
  text-decoration: none;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 500;
  border: 1px solid rgba(148, 163, 184, 0.7);
  backdrop-filter: blur(18px);
  transition: all 0.3s;
}

.btn-secondary:hover {
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 4px 18px rgba(148, 163, 184, 0.6);
}

.btn-primary::after,
.btn-cta::after {
  content: '>';
  margin-left: 8px;
  font-size: 16px;
  transition: transform 0.3s;
}

.btn-primary:hover::after,
.btn-cta:hover::after {
  transform: translateX(4px);
}

.section-title {
  text-align: center;
  font-size: 32px;
  font-weight: 700;
  color: #0f172a;
  margin: 0 0 16px 0;
}

.section-subtitle {
  text-align: center;
  font-size: 15px;
  color: #6b7280;
  margin: 0 0 40px 0;
}

.features {
  padding: 100px 40px;
  background-image: linear-gradient(to top, #fff1eb 0%, #ace0f9 100%);
}

.features-inner {
  max-width: 1200px;
  margin: 0 auto;
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 32px;
}

.feature-card {
  padding: 28px;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 18px;
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.12);
  border: 1px solid rgba(226, 232, 240, 0.9);
  backdrop-filter: blur(22px);
  transition: all 0.3s;
  position: relative;
  overflow: hidden;
}

.feature-card::before {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at top left, rgba(132, 250, 176, 0.15), transparent 55%);
  opacity: 0;
  transition: opacity 0.3s;
}

.feature-card:hover::before {
  opacity: 1;
}

.feature-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.12);
}

.feature-icon {
  width: 48px;
  height: 48px;
  border-radius: 16px;
  margin-bottom: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 14px;
  color: #0f172a;
  background: linear-gradient(120deg, #84fab0 0%, #8fd3f4 100%);
}

.feature-icon-1 { background: linear-gradient(120deg, #84fab0 0%, #8fd3f4 100%); }
.feature-icon-2 { background: linear-gradient(120deg, #8fd3f4 0%, #96fbc4 100%); }
.feature-icon-3 { background: linear-gradient(120deg, #96fbc4 0%, #ace0f9 100%); }
.feature-icon-4 { background: linear-gradient(120deg, #ace0f9 0%, #84fab0 100%); }
.feature-icon-5 { background: linear-gradient(120deg, #84fab0 0%, #96fbc4 100%); }
.feature-icon-6 { background: linear-gradient(120deg, #8fd3f4 0%, #84fab0 100%); }

.feature-title {
  font-size: 22px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0 0 12px 0;
}

.feature-desc {
  font-size: 15px;
  color: #666;
  line-height: 1.6;
  margin: 0;
}

.faq {
  padding: 100px 40px;
  background: #f8f9fa;
}

.faq-list {
  max-width: 800px;
  margin: 0 auto;
}

.faq-item {
  background: white;
  padding: 32px;
  border-radius: 12px;
  margin-bottom: 20px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
}

.faq-question {
  font-size: 20px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0 0 12px 0;
}

.faq-answer {
  font-size: 16px;
  color: #666;
  line-height: 1.7;
  margin: 0;
}

.cta {
  padding: 100px 40px;
  background: #ffffff;
  text-align: center;
}

.cta-content {
  max-width: 800px;
  margin: 0 auto;
}

.cta-title {
  font-size: 32px;
  font-weight: 700;
  color: #1a1a1a;
  margin: 0 0 16px 0;
}

.cta-text {
  font-size: 15px;
  color: #4b5563;
  line-height: 1.8;
  margin: 0 0 32px 0;
}

.btn-cta {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 16px 40px;
  background: #2f7df6;
  color: white;
  text-decoration: none;
  border-radius: 12px;
  font-size: 18px;
  font-weight: 600;
  transition: all 0.3s;
  box-shadow: 0 4px 15px rgba(47, 125, 246, 0.4);
}

.btn-cta:hover {
  background: #1d6be0;
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(47, 125, 246, 0.5);
}

.footer {
  background: #1a1a1a;
  color: white;
  padding: 60px 40px 30px;
}

.footer-content {
  max-width: 1200px;
  margin: 0 auto 40px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 40px;
}

.footer-brand h3 {
  font-size: 24px;
  margin: 0 0 8px 0;
  letter-spacing: 2px;
}

.footer-brand p {
  color: #999;
  margin: 0;
}

.footer-links {
  display: flex;
  gap: 24px;
}

.footer-links a {
  color: #ccc;
  text-decoration: none;
  transition: color 0.3s;
}

.footer-links a:hover {
  color: white;
}

.footer-bottom {
  max-width: 1200px;
  margin: 0 auto;
  padding-top: 30px;
  border-top: 1px solid #333;
  text-align: center;
}

.footer-bottom p {
  color: #999;
  margin: 0;
  font-size: 14px;
}


html {
  scroll-behavior: smooth;
}
</style>
