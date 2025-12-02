import api from "./client";

export const fetchAdminOverview = () => api.get("/dashboard/admin-overview");
