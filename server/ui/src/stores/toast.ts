import { writable } from 'svelte/store';

export type ToastType = 'info' | 'success' | 'warning' | 'error';

export interface Toast {
    id: number;
    message: string;
    type: ToastType;
}

function createToastStore() {
    const { subscribe, update } = writable<Toast[]>([]);

    let nextId = 0;

    return {
        subscribe,
        push: (message: string, type: ToastType = 'info', duration = 3000) => {
            const id = nextId++;
            update(toasts => [...toasts, { id, message, type }]);
            
            if (duration > 0) {
                setTimeout(() => {
                    update(toasts => toasts.filter(t => t.id !== id));
                }, duration);
            }
        },
        remove: (id: number) => {
            update(toasts => toasts.filter(t => t.id !== id));
        },
        success: (msg: string, duration?: number) => toast.push(msg, 'success', duration),
        error: (msg: string, duration?: number) => toast.push(msg, 'error', duration),
        info: (msg: string, duration?: number) => toast.push(msg, 'info', duration),
        warning: (msg: string, duration?: number) => toast.push(msg, 'warning', duration)
    };
}

export const toast = createToastStore();
