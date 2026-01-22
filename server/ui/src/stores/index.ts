import { writable } from 'svelte/store';
import { api } from '../api';

export const config = writable<any>(null);
export const providers = writable<any[]>([]);
export const plugins = writable<any[]>([]);
export const repositories = writable<any[]>([]);

export const activeProvider = writable<string | null>(null);
const ACTIVE_PROVIDER_KEY = 'cloudstream_active_provider';

if (typeof localStorage !== 'undefined') {
  activeProvider.subscribe((value) => {
    if (value) {
      localStorage.setItem(ACTIVE_PROVIDER_KEY, value);
    }
  });
}

export async function loadInitialData() {
  try {
    const [cfg, provs, plugs, repos] = await Promise.all([
      api.getConfig(),
      api.getProviders(),
      api.getPlugins(),
      api.getRepositories()
    ]);

    config.set(cfg);
    providers.set(provs);
    plugins.set(plugs);
    repositories.set(repos);

    if (provs.length > 0) {
        const stored = typeof localStorage !== 'undefined'
          ? localStorage.getItem(ACTIVE_PROVIDER_KEY)
          : null;
        const storedValid = stored && provs.some(p => p.name === stored) ? stored : null;
        activeProvider.update(current => {
            if (current && provs.some(p => p.name === current)) return current;
            if (storedValid) return storedValid;
            return provs[0].name;
        });
    }
  } catch (err) {
    console.error("Failed to load initial data", err);
  }
}
