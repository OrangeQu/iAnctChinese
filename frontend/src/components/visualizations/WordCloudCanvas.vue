<template>
  <div class="cloud-wrap">
    <canvas ref="canvasRef"></canvas>
  </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref, watch } from "vue";

const props = defineProps({
  words: {
    type: Array,
    default: () => []
  }
});

const canvasRef = ref(null);
const colors = ["#c05621", "#8f6f35", "#5f6f7c", "#2b6cb0", "#805ad5", "#c53030"];

let resizeObserver;

const drawCloud = () => {
  const canvas = canvasRef.value;
  if (!canvas) return;

  const rect = canvas.parentElement.getBoundingClientRect();
  canvas.width = rect.width;
  canvas.height = rect.height || 220;
  const ctx = canvas.getContext("2d");
  ctx.clearRect(0, 0, canvas.width, canvas.height);

  const words = props.words && props.words.length > 0 ? [...props.words] : [];
  if (words.length === 0) {
    ctx.fillStyle = "#cbd5e0";
    ctx.font = "14px sans-serif";
    ctx.fillText("暂无词频", 10, 24);
    return;
  }

  // normalize weight to font size
  const minWeight = Math.min(...words.map((w) => w.weight || 0.3));
  const maxWeight = Math.max(...words.map((w) => w.weight || 1));
  const sizeScale = (w) => {
    const normalized = (w - minWeight) / (maxWeight - minWeight || 1);
    return 16 + normalized * 36; // font size range 16-52
  };

  const placed = [];
  const centerX = canvas.width / 2;
  const centerY = canvas.height / 2;

  words.sort((a, b) => (b.weight || 1) - (a.weight || 1));

  const collide = (box) =>
    placed.some(
      (p) =>
        p.x < box.x + box.w &&
        p.x + p.w > box.x &&
        p.y < box.y + box.h &&
        p.y + p.h > box.y
    );

  words.forEach((word, idx) => {
    const fontSize = sizeScale(word.weight || 0.4);
    const font = `${Math.round(fontSize)}px serif`;
    ctx.font = font;
    const textWidth = ctx.measureText(word.label).width;
    const textHeight = fontSize;
    let angle = Math.random() > 0.7 ? (Math.random() > 0.5 ? -20 : 20) : 0;

    let placedBox = null;
    const maxRadius = Math.max(canvas.width, canvas.height);
    for (let attempt = 0; attempt < 400; attempt++) {
      const radius = (attempt / 400) * maxRadius;
      const theta = attempt * 0.35;
      const x = centerX + radius * Math.cos(theta) - textWidth / 2;
      const y = centerY + radius * Math.sin(theta) - textHeight / 2;
      const box = { x, y, w: textWidth, h: textHeight, angle };
      if (
        x > 0 &&
        y > 0 &&
        x + textWidth < canvas.width &&
        y + textHeight < canvas.height &&
        !collide(box)
      ) {
        placedBox = box;
        break;
      }
    }

    if (placedBox) {
      placed.push(placedBox);
      ctx.save();
      ctx.translate(placedBox.x + placedBox.w / 2, placedBox.y + placedBox.h / 2);
      ctx.rotate((placedBox.angle * Math.PI) / 180);
      ctx.fillStyle = colors[idx % colors.length];
      ctx.textAlign = "center";
      ctx.textBaseline = "middle";
      ctx.font = font;
      ctx.fillText(word.label, 0, 0);
      ctx.restore();
    }
  });
};

onMounted(() => {
  drawCloud();
  resizeObserver = new ResizeObserver(drawCloud);
  resizeObserver.observe(canvasRef.value.parentElement);
});

onBeforeUnmount(() => {
  if (resizeObserver) {
    resizeObserver.disconnect();
  }
});

watch(
  () => props.words,
  () => {
    drawCloud();
  },
  { deep: true }
);
</script>

<style scoped>
.cloud-wrap {
  width: 100%;
  height: 260px;
}

canvas {
  width: 100%;
  height: 100%;
  display: block;
}
</style>
