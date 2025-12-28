export type ToastType = "info" | "error" | "success";

export interface ToastEventDetail {
  id: number;
  message: string;
  type: ToastType;
}

let toastId = 1;

export const notify = (message: string, type: ToastType = "info") => {
  const detail: ToastEventDetail = {
    id: toastId++,
    message,
    type,
  };
  window.dispatchEvent(new CustomEvent<ToastEventDetail>("admin-toast", { detail }));
};
