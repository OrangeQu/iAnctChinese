import { defineStore } from "pinia";
import { getApiBase, setApiBase as setHttpApiBase } from "@/services/http";

export const useAppStore = defineStore("app", {
  state: () => ({
    selectedTextId: null as number | null,
    refreshKey: 0,
    apiBase: getApiBase(),
  }),
  actions: {
    setSelectedTextId(id: number | null) {
      this.selectedTextId = id;
    },
    triggerRefresh() {
      this.refreshKey += 1;
    },
    setApiBase(value: string) {
      const next = value || "/api";
      this.apiBase = next;
      setHttpApiBase(next);
    },
  },
});
