<template>
  <AppShell
    @search="handleSearch"
    @export="handleExport"
    :exporting="store.exporting"
    :can-export="Boolean(store.selectedTextId)"
  >
    <RouterView />
  </AppShell>
</template>

<script setup>
import { RouterView } from "vue-router";
import AppShell from "@/components/layout/AppShell.vue";
import { useTextStore } from "@/store/textStore";

const store = useTextStore();

const handleSearch = (keywords) => {
  store.performSearch(keywords);
};

const handleExport = () => {
  store.exportSelectedText();
};
</script>
