import { writable } from 'svelte/store';

const THEME_KEY = 'cloudstream_theme';
const DEFAULT_THEME = 'forest';

export const themes = [
    'forest',
    'dracula', 
    'black', 
    'sunset', 
    'autumn', 
    'synthwave', 
    'retro', 
    'nord',
    'coffee',
    'night',
    'lemonade',
    'aqua'
];

function createThemeStore() {
    const stored = localStorage.getItem(THEME_KEY);
    const initial = stored && themes.includes(stored) ? stored : DEFAULT_THEME;
    
    const { subscribe, set } = writable(initial);

    return {
        subscribe,
        set: (theme: string) => {
            if (!themes.includes(theme)) return;
            localStorage.setItem(THEME_KEY, theme);
            document.documentElement.setAttribute('data-theme', theme);
            set(theme);
        },
        init: () => {
            const current = localStorage.getItem(THEME_KEY) || DEFAULT_THEME;
            document.documentElement.setAttribute('data-theme', current);
            set(current);
        }
    };
}

export const theme = createThemeStore();
