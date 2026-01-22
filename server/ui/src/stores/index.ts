import { writable } from 'svelte/store';
import { api } from '../api';

export const config = writable<any>(null);
export const providers = writable<any[]>([]);
export const plugins = writable<any[]>([]);
export const repositories = writable<any[]>([]);

export const activeProvider = writable<string | null>(null);

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
        // Set first provider as active if none selected (logic could be improved)
        activeProvider.update(current => current || provs[0].name);
    }
  } catch (err) {
    console.error("Failed to load initial data", err);
  }
}
