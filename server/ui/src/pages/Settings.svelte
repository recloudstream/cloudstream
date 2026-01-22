<script lang="ts">
  import { config as configStore, repositories, plugins, loadInitialData } from '../stores';
  import { theme, themes } from '../stores/theme';
  import { api } from '../api';
  import { onMount } from 'svelte';

  let activeTab = 'general';
  let serverConfig: any = {};
  let saveStatus = '';

  // Subscribe to config store to initialize form
  $: if ($configStore && $configStore.server) {
      // Clone to avoid direct mutation
      serverConfig = JSON.parse(JSON.stringify($configStore.server));
  }

  onMount(async () => {
    if (!$configStore) await loadInitialData();
  });

  async function saveConfig() {
      if (!$configStore) return;
      saveStatus = 'saving';
      try {
          const updated = { ...$configStore, server: serverConfig };
          await api.updateConfig(updated);
          configStore.set(updated);
          saveStatus = 'success';
          setTimeout(() => saveStatus = '', 3000);
      } catch (e) {
          console.error(e);
          saveStatus = 'error';
      }
  }
</script>

<div class="p-6 md:p-12 max-w-4xl mx-auto">
  <h1 class="text-4xl font-bold text-base-content mb-8">Settings</h1>

  <!-- Tabs -->
  <div role="tablist" class="tabs tabs-lifted tabs-lg mb-8">
      <a role="tab" class="tab {activeTab === 'general' ? 'tab-active' : ''}" onclick={() => activeTab = 'general'}>General</a>
      <a role="tab" class="tab {activeTab === 'theme' ? 'tab-active' : ''}" onclick={() => activeTab = 'theme'}>Theme</a>
      <a role="tab" class="tab {activeTab === 'accounts' ? 'tab-active' : ''}" onclick={() => activeTab = 'accounts'}>Accounts</a>
  </div>

  <div class="bg-base-200 rounded-box p-6 md:p-8 min-h-[400px]">
      
      {#if activeTab === 'general'}
          <div class="space-y-6 max-w-lg">
              <h2 class="text-2xl font-bold mb-4">Server Configuration</h2>
              
              <div class="form-control w-full">
                  <div class="label">
                      <span class="label-text">Host</span>
                  </div>
                  <input type="text" bind:value={serverConfig.host} class="input input-bordered w-full" />
              </div>

              <div class="form-control w-full">
                  <div class="label">
                      <span class="label-text">Port</span>
                  </div>
                  <input type="number" bind:value={serverConfig.port} class="input input-bordered w-full" />
              </div>

              <div class="form-control w-full">
                  <label class="label cursor-pointer justify-start gap-4">
                      <input type="checkbox" class="toggle toggle-primary" bind:checked={serverConfig.useJsdelivr} />
                      <span class="label-text">Use jsDelivr for repositories</span>
                  </label>
              </div>

              <div class="pt-4">
                  <button class="btn btn-primary" onclick={saveConfig} disabled={saveStatus === 'saving'}>
                      {#if saveStatus === 'saving'}
                          <span class="loading loading-spinner"></span>
                      {/if}
                      Save Changes
                  </button>
                  {#if saveStatus === 'success'}
                      <span class="text-success ml-4 fade-in">Saved!</span>
                  {/if}
                  {#if saveStatus === 'error'}
                      <span class="text-error ml-4">Failed to save.</span>
                  {/if}
              </div>
          </div>

      {:else if activeTab === 'theme'}
          <div class="space-y-6">
              <h2 class="text-2xl font-bold mb-4 text-base-content">Appearance</h2>
              <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
                  {#each themes as t}
                      <button 
                          class="card bg-base-100 shadow-xl overflow-hidden border-2 transition-all hover:scale-105 {$theme === t ? 'border-primary ring-2 ring-primary ring-offset-2 ring-offset-base-100' : 'border-base-content/10'}"
                          onclick={() => theme.set(t)}
                          data-theme={t}
                      >
                          <div class="w-full h-24 bg-base-100 flex flex-col cursor-pointer">
                              <div class="flex-1 bg-base-200 w-full p-2 flex gap-1">
                                  <div class="w-2 h-2 rounded-full bg-primary"></div>
                                  <div class="w-2 h-2 rounded-full bg-secondary"></div>
                                  <div class="w-2 h-2 rounded-full bg-accent"></div>
                                  <div class="w-2 h-2 rounded-full bg-neutral"></div>
                              </div>
                              <div class="flex-1 bg-base-100 w-full flex items-center justify-center">
                                  <span class="font-bold text-sm capitalize">{t}</span>
                              </div>
                          </div>
                      </button>
                  {/each}
              </div>
          </div>

      {:else if activeTab === 'accounts'}
          <div class="space-y-6">
              <h2 class="text-2xl font-bold mb-4 text-base-content">Accounts</h2>
              <div class="alert alert-info">
                  <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" class="stroke-current shrink-0 w-6 h-6"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
                  <span>Account management coming soon. Configuration is stored in `config.json`.</span>
              </div>
              <div class="overflow-x-auto">
                  <table class="table w-full">
                      <thead>
                          <tr>
                              <th>Type</th>
                              <th>Name</th>
                              <th>ID</th>
                          </tr>
                      </thead>
                      <tbody>
                          {#if $configStore && $configStore.accounts}
                              {#each $configStore.accounts as account}
                                  <tr>
                                      <td>{account.type}</td>
                                      <td>{account.name || '-'}</td>
                                      <td class="font-mono text-xs opacity-50">{account.id}</td>
                                  </tr>
                              {/each}
                          {/if}
                      </tbody>
                  </table>
                  {#if !$configStore?.accounts?.length}
                      <p class="text-center py-4 text-base-content/50">No accounts configured.</p>
                  {/if}
              </div>
          </div>
      {/if}

  </div>
</div>
