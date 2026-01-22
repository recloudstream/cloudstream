<script lang="ts">
  import { onMount } from 'svelte';
  import { config as configStore, plugins, repositories, providers, loadInitialData } from '../stores';
  import { toast } from '../stores/toast';
  import { api } from '../api';
  import ConfirmModal from '../components/shared/ConfirmModal.svelte';
  import ProviderPicker from '../components/shared/ProviderPicker.svelte';

  let activeTab = 'installed';
  let loading = false;
  let repoUrlInput = '';
  let repoNameInput = '';
  let providerOverrides: any[] = [];
  let overrideBaseClass = '';
  let overrideName = '';
  let overrideUrl = '';
  let overrideLang = '';
  let overridesLoading = false;

  onMount(async () => {
    if (!$configStore) await loadInitialData();
    await loadOverrides();
  });

  async function addRepository() {
      if (!repoUrlInput) return;
      loading = true;
      try {
          await api.addRepository(repoUrlInput, repoNameInput);
          await loadInitialData(); // Reload to get new repos/plugins
          repoUrlInput = '';
          repoNameInput = '';
      } catch (e) {
          console.error(e);
          toast.error('Failed to add repository');
      } finally {
          loading = false;
      }
  }

  $: overrideableProviders = $providers.filter(
      (provider) => provider.className && provider.canBeOverridden !== false
  );
  $: if (!overrideBaseClass && overrideableProviders.length > 0) {
      overrideBaseClass = overrideableProviders[0].className;
  }

  
  let confirmRepoDelete: ConfirmModal;
  async function removeRepository(id: string) {
      if (!await confirmRepoDelete.show()) return;
      loading = true;
      try {
          await api.removeRepository(id);
          await loadInitialData();
      } catch (e) {
          console.error(e);
          toast.error('Failed to remove repository');
      } finally {
          loading = false;
      }
  }

  let confirmOverrideDelete: ConfirmModal;
  async function loadOverrides() {
      overridesLoading = true;
      try {
          providerOverrides = await api.getProviderOverrides();
      } catch (e) {
          console.error(e);
          toast.error('Failed to load provider overrides');
      } finally {
          overridesLoading = false;
      }
  }

  function resolveProviderLabel(parentClassName: string) {
      const match = $providers.find((provider) => {
          if (!provider.className) return false;
          const simple = provider.className.split('.').pop();
          return provider.className === parentClassName || simple === parentClassName;
      });
      return match ? match.name : parentClassName;
  }

  async function addOverride() {
      if (!overrideBaseClass || !overrideName || !overrideUrl) return;
      overridesLoading = true;
      try {
          await api.addProviderOverride({
              parentClassName: overrideBaseClass,
              name: overrideName.trim(),
              url: overrideUrl.trim(),
              lang: overrideLang.trim() || undefined,
          });
          overrideName = '';
          overrideUrl = '';
          overrideLang = '';
          await loadOverrides();
          await loadInitialData();
          toast.success('Provider override added');
      } catch (e) {
          console.error(e);
          toast.error('Failed to add provider override');
      } finally {
          overridesLoading = false;
      }
  }

  async function removeOverride(overrideEntry: any) {
      if (!await confirmOverrideDelete.show()) return;
      overridesLoading = true;
      try {
          await api.removeProviderOverride(overrideEntry.name);
          await loadOverrides();
          await loadInitialData();
          toast.success('Provider override removed');
      } catch (e) {
          console.error(e);
          toast.error('Failed to remove provider override');
      } finally {
          overridesLoading = false;
      }
  }

  // File upload handler
  async function handleFileUpload(e: Event) {
      const input = e.target as HTMLInputElement;
      if (input.files && input.files[0]) {
          const file = input.files[0];
          loading = true;
          try {
              await api.uploadPlugin(file);
              await loadInitialData();
              toast.success('Plugin uploaded successfully');
          } catch (e) {
              console.error(e);
              toast.error('Failed to upload plugin');
          } finally {
              loading = false;
              input.value = ''; // Reset input
          }
      }
  }
  // Repository Browsing
  let browsingRepo: any = null;
  let repoPluginsList: any[] = [];
  
  // Filters & Search
  let pluginSearch = '';
  let selectedLang = '';
  let selectedTvTypes: string[] = []; // Changed to array for multi-select

  // Derived filters
  $: uniqueLanguages = [...new Set(repoPluginsList.map(p => p.language).filter(Boolean))].sort();
  $: uniqueTvTypes = [...new Set(repoPluginsList.flatMap(p => p.tvTypes || []).filter(Boolean))].sort();

  $: filteredRepoPlugins = repoPluginsList.filter(plugin => {
      const matchesSearch = !pluginSearch || 
          (plugin.name?.toLowerCase().includes(pluginSearch.toLowerCase()) || 
           plugin.description?.toLowerCase().includes(pluginSearch.toLowerCase()) || 
           plugin.authors?.some((a: string) => a.toLowerCase().includes(pluginSearch.toLowerCase())));
      
      const matchesLang = !selectedLang || plugin.language === selectedLang;
      
      // Filter: Check if ALL selected types are present in the plugin's types
      const matchesType = selectedTvTypes.length === 0 || 
                          selectedTvTypes.every(t => plugin.tvTypes?.includes(t));

      return matchesSearch && matchesLang && matchesType;
  });

  function toggleTvTypeFilter(type: string) {
      if (selectedTvTypes.includes(type)) {
          selectedTvTypes = selectedTvTypes.filter(t => t !== type);
      } else {
          selectedTvTypes = [...selectedTvTypes, type];
      }
  }

  // Helper to check install status
  function getInstalledPlugin(internalName: string) {
      return $plugins.find(p => p.internalName === internalName);
  }

  function getPluginStatus(plugin: any, pluginsList: any[]) {
      const installed = pluginsList.find(p => p.internalName === plugin.internalName);
      if (!installed) return 'install';
      if (installed.version < plugin.version) return 'update';
      return 'installed';
  }

  async function browseRepo(repo: any) {
      loading = true;
      try {
          const data = await api.getRepositoryPlugins(repo.id || repo.url); 
          browsingRepo = data.repository;
          // Normalize tvTypes: some repos might return types as ["Movie, Anime"] instead of ["Movie", "Anime"]
          repoPluginsList = data.plugins.map((p: any) => {
              const plugin = p.plugin;
              if (plugin.tvTypes && Array.isArray(plugin.tvTypes)) {
                  plugin.tvTypes = plugin.tvTypes
                      .flatMap((t: string) => t.split(','))
                      .map((t: string) => t.trim())
                      .filter(Boolean);
              }
              return plugin;
          });
          // Reset filters
          pluginSearch = '';
          selectedLang = '';
          selectedTvTypes = [];
      } catch (e) {
          console.error(e);
          toast.error('Failed to load plugins');
      } finally {
          loading = false;
      }
  }

  let installingPlugins = new Set<string>();

  async function installPluginFromRepo(internalName: string) {
       if (!browsingRepo) return;
       const repoId = browsingRepo.id; 
       
       loading = true; // Keep global loading for safety or remove if per-button is enough? Keeping it might block other actions which is good, but user wants to see the button spinning. 
       // Actually, if we use global loading=true, the whole UI might be blocked or show a global spinner if implemented that way.
       // In this file, global `loading` variable is used to disable buttons or show overlay? 
       // Looking at line 8: `let loading = false;`
       // And looking at the UI, `loading` is not used to mask the whole screen in the browsing view currently, but it is used in "Add Repo" button.
       // Let's rely on local state for the button and keep global loading false or true? 
       // If I set loading=true, does it hide the grid? No, I don't see a "if loading" block wrapping the grid.
       
       installingPlugins.add(internalName);
       installingPlugins = installingPlugins; // Trigger reactivity
       
       try {
           await api.installRepositoryPlugin(repoId, internalName);
           toast.success('Plugin installed!');
           await loadInitialData(); 
       } catch (e) {
           console.error(e);
           toast.error('Failed to install plugin');
       } finally {
           installingPlugins.delete(internalName);
           installingPlugins = installingPlugins;
           loading = false;
       }
  }

  let confirmUninstall: ConfirmModal;
  let pluginToUninstall: any = null;

  function handleIconError(e: Event) {
      const target = e.currentTarget as HTMLImageElement;
      target.classList.add('hidden');
  }

  async function uninstallPlugin(plugin: any) {
      pluginToUninstall = plugin;
      if(!await confirmUninstall.show()) {
          pluginToUninstall = null;
          return;
      }
      
      loading = true;
      try {
          const installed = getInstalledPlugin(plugin.internalName);
          if (installed) {
               await api.removePlugin({ filePath: installed.filePath });
               await loadInitialData();
               toast.success('Plugin uninstalled');
          }
      } catch(e) {
          console.error(e);
          toast.error('Failed to uninstall');
      } finally {
          loading = false;
          pluginToUninstall = null;
      }
  }

  function closeBrowsing() {
      browsingRepo = null;
      repoPluginsList = [];
  }
</script>

<div class="p-6 md:p-12 max-w-7xl mx-auto">
  <div class="flex items-center justify-between mb-8">
      {#if browsingRepo}
          <div class="flex items-center gap-4">
              <button class="btn btn-circle btn-ghost" onclick={closeBrowsing}>
                  <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" /></svg>
              </button>
              <div>
                  <h1 class="text-3xl font-bold text-base-content">{browsingRepo.name}</h1>
                  <p class="text-sm opacity-50">Browsing Repository</p>
              </div>
          </div>
      {:else}
          <h1 class="text-4xl font-bold text-base-content">Plugins</h1>
      {/if}

      {#if !browsingRepo}
          <div class="join">
              <button 
                  class="join-item btn {activeTab === 'installed' ? 'btn-primary' : 'btn-neutral'}" 
                  onclick={() => activeTab = 'installed'}>
                  Installed
              </button>
              <button 
                  class="join-item btn {activeTab === 'repositories' ? 'btn-primary' : 'btn-neutral'}" 
                  onclick={() => activeTab = 'repositories'}>
                  Repositories
              </button>
              <button 
                  class="join-item btn {activeTab === 'overrides' ? 'btn-primary' : 'btn-neutral'}" 
                  onclick={() => activeTab = 'overrides'}>
                  Overrides
              </button>
          </div>
      {/if}
  </div>

  {#if browsingRepo}
       <!-- Filters & Search -->
       <div class="flex flex-col md:flex-row gap-4 mb-8">
           <input 
              type="text" 
              placeholder="Search plugins..." 
              bind:value={pluginSearch} 
              class="input input-bordered w-full md:flex-1" 
           />
           <select class="select select-bordered w-full md:w-auto" bind:value={selectedLang}>
               <option value="">All Languages</option>
               {#each uniqueLanguages as lang}
                   <option value={lang}>{lang}</option>
               {/each}
           </select>
           
           <!-- Multi-select TV Types Dropdown -->
           <div class="dropdown dropdown-end">
                <div tabindex="0" role="button" class="select select-bordered w-full md:w-auto flex items-center justify-between px-3">
                    <span>Filter Types {selectedTvTypes.length > 0 ? `(${selectedTvTypes.length})` : ''}</span>
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4 opacity-60 ml-2" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" /></svg>
                </div>
                <ul tabindex="-1" class="dropdown-content z-[1] menu p-2 shadow bg-base-100 rounded-box min-w-[13rem] w-52 max-h-96 overflow-y-auto flex-nowrap block">
                    {#each uniqueTvTypes as type}
                        <li>
                            <label class="label cursor-pointer justify-start">
                                <input 
                                    type="checkbox" 
                                    class="checkbox checkbox-sm checkbox-primary" 
                                    checked={selectedTvTypes.includes(type)} 
                                    onchange={() => toggleTvTypeFilter(type)}
                                />
                                <span class="label-text">{type}</span>
                            </label>
                        </li>
                    {/each}
                     {#if uniqueTvTypes.length === 0}
                        <li class="disabled"><a>No types found</a></li>
                    {/if}
                </ul>
            </div>
       </div>

       <!-- Browsing View -->
       <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
          {#each filteredRepoPlugins as plugin}
              {@const status = getPluginStatus(plugin, $plugins)}
              <div class="card bg-base-100 shadow-xl border border-base-content/5 flex flex-col h-full">
                  <div class="card-body p-5 flex flex-col h-full">
                       <div class="flex items-start justify-between gap-3">
                          <div class="flex items-center gap-3 w-full overflow-hidden">
                              <div class="size-12 rounded-md bg-base-300 shrink-0 relative overflow-hidden flex items-center justify-center text-base-content/70">
                                  <svg xmlns="http://www.w3.org/2000/svg" class="size-6 opacity-70" viewBox="0 0 24 24" fill="currentColor">
                                      <path d="M20.5 11H19V7c0-1.1-.9-2-2-2h-4V3.5C13 2.12 11.88 1 10.5 1S8 2.12 8 3.5V5H4c-1.1 0-1.99.9-1.99 2v3.8H3.5c1.49 0 2.7 1.21 2.7 2.7s-1.21 2.7-2.7 2.7H2V20c0 1.1.9 2 2 2h3.8v-1.5c0-1.49 1.21-2.7 2.7-2.7 1.49 0 2.7 1.21 2.7 2.7V22H17c1.1 0 2-.9 2-2v-4h1.5c1.38 0 2.5-1.12 2.5-2.5S21.88 11 20.5 11z"/>
                                  </svg>
                                  {#if plugin.iconUrl}
                                      <img 
                                          src={plugin.iconUrl.replace('%size%', '64')} 
                                          alt="" 
                                          class="absolute inset-0 w-full h-full object-contain bg-base-300" 
                                          onerror={handleIconError}
                                      />
                                  {/if}
                              </div>
                              <div class="min-w-0">
                                  <h3 class="font-bold truncate" title={plugin.name}>{plugin.name}</h3>
                                  <p class="text-xs opacity-60 truncate">by {plugin.authors?.join(', ') || 'Unknown'}</p>
                                  <div class="flex flex-wrap gap-1 mt-1">
                                      {#if plugin.language}
                                          <span class="badge badge-xs badge-neutral">{plugin.language}</span>
                                      {/if}
                                      {#if plugin.version}
                                          <span class="badge badge-xs badge-ghost">v{plugin.version}</span>
                                      {/if}
                                  </div>
                              </div>
                          </div>
                       </div>
                       
                       {#if plugin.tvTypes && plugin.tvTypes.length > 0}
                           <div class="flex flex-wrap gap-1 mt-3">
                               {#each plugin.tvTypes.slice(0, 3) as type}
                                   <span class="badge badge-xs badge-outline opacity-70">{type}</span>
                               {/each}
                               {#if plugin.tvTypes.length > 3}
                                  <span class="badge badge-xs badge-outline opacity-50">+{plugin.tvTypes.length - 3}</span>
                               {/if}
                           </div>
                       {/if}
                       
                       <p class="text-sm mt-3 line-clamp-3 opacity-80 grow">
                          {plugin.description || 'No description provided.'}
                       </p>

                       <div class="card-actions justify-end mt-4 pt-4 border-t border-base-content/10">
                           {#if status === 'update'}
                               <button 
                                   class="btn btn-sm btn-info text-info-content" 
                                   onclick={() => installPluginFromRepo(plugin.internalName)}
                                   disabled={installingPlugins.has(plugin.internalName)}
                               >
                                  {#if installingPlugins.has(plugin.internalName)}
                                      <span class="loading loading-spinner loading-xs"></span>
                                  {/if}
                                  Update
                               </button>
                           {:else if status === 'installed'}
                               <button class="btn btn-sm btn-error btn-outline" onclick={() => uninstallPlugin(plugin)}>
                                  Uninstall
                               </button>
                           {:else}
                               <button 
                                   class="btn btn-sm btn-primary" 
                                   onclick={() => installPluginFromRepo(plugin.internalName)}
                                   disabled={installingPlugins.has(plugin.internalName)}
                               >
                                  {#if installingPlugins.has(plugin.internalName)}
                                      <span class="loading loading-spinner loading-xs"></span>
                                  {/if}
                                  Install
                               </button>
                           {/if}
                       </div>
                  </div>
              </div>
          {/each}
          {#if filteredRepoPlugins.length === 0}
               <div class="col-span-full py-20 text-center opacity-50">
                   {#if repoPluginsList.length === 0}
                       No plugins found in this repository.
                   {:else}
                       No plugins match your filters.
                   {/if}
               </div>
          {/if}
       </div>

  {:else if activeTab === 'installed'}
      <div class="space-y-8">
          <!-- Upload Area (Small) -->
          <div class="card bg-base-200 border-2 border-dashed border-base-content/20 hover:border-primary transition-colors">
              <div class="card-body items-center text-center p-6">
                  <h3 class="font-bold text-lg">Install Local Plugin</h3>
                  <p class="text-sm opacity-60">Drag & drop .cs3 file or click to upload</p>
                  <label class="btn btn-sm btn-outline mt-2">
                       Choose File
                       <input type="file" accept=".cs3" class="hidden" onchange={handleFileUpload} />
                  </label>
              </div>
          </div>

          <!-- Installed Grid -->
          <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
              {#each $plugins as plugin}
                  <div class="card bg-base-100 shadow-xl border border-base-content/5">
                      <div class="card-body p-5">
                           <div class="flex items-start justify-between">
                              <div class="flex items-center gap-3">
                                  <div class="size-10 rounded-md bg-base-300 shrink-0 relative overflow-hidden flex items-center justify-center text-base-content/70">
                                      <svg xmlns="http://www.w3.org/2000/svg" class="size-5 opacity-70" viewBox="0 0 24 24" fill="currentColor">
                                          <path d="M20.5 11H19V7c0-1.1-.9-2-2-2h-4V3.5C13 2.12 11.88 1 10.5 1S8 2.12 8 3.5V5H4c-1.1 0-1.99.9-1.99 2v3.8H3.5c1.49 0 2.7 1.21 2.7 2.7s-1.21 2.7-2.7 2.7H2V20c0 1.1.9 2 2 2h3.8v-1.5c0-1.49 1.21-2.7 2.7-2.7 1.49 0 2.7 1.21 2.7 2.7V22H17c1.1 0 2-.9 2-2v-4h1.5c1.38 0 2.5-1.12 2.5-2.5S21.88 11 20.5 11z"/>
                                      </svg>
                                      {#if plugin.iconUrl}
                                          <img 
                                              src={plugin.iconUrl.replace('%size%', '64')} 
                                              alt="" 
                                              class="absolute inset-0 w-full h-full object-contain bg-base-300" 
                                              onerror={handleIconError}
                                          />
                                      {/if}
                                  </div>
                                  <div>
                                      <h3 class="font-bold">{plugin.name || plugin.internalName}</h3>
                                      <p class="text-xs opacity-60">v{plugin.version} • {plugin.authors?.join(', ')}</p>
                                  </div>
                              </div>
                              <div class="flex items-center gap-2 text-xs opacity-70">
                                  <span class="inline-block size-2 rounded-full {plugin.status === 1 ? 'bg-success' : 'bg-warning'}"></span>
                                  <span>{plugin.status === 1 ? 'Working' : 'Issues'}</span>
                              </div>
                           </div>
                           
                           <p class="text-sm mt-3 line-clamp-2 opacity-80 min-h-[2.5em]">
                               {plugin.description || 'No description provided.'}
                           </p>

                           <div class="card-actions justify-end mt-4 pt-4 border-t border-base-content/10">
                               <button class="btn btn-sm btn-ghost text-error" onclick={() => uninstallPlugin(plugin)}>
                                  Uninstall
                               </button>
                           </div>
                      </div>
                  </div>
              {/each}
              {#if $plugins.length === 0}
                   <div class="col-span-full py-20 text-center opacity-50">
                       No plugins installed. Check Repositories to add some.
                   </div>
              {/if}
          </div>
      </div>
  {:else if activeTab === 'repositories'}
      <div class="space-y-8">
           <!-- Add Repo form -->
           <div class="flex flex-col md:flex-row gap-4 bg-base-200 p-6 rounded-box items-end">
                <div class="form-control w-full md:flex-1">
                    <label class="label"><span class="label-text">Repository URL</span></label>
                    <input type="text" bind:value={repoUrlInput} placeholder="https://..." class="input input-bordered w-full" />
                </div>
                <div class="form-control w-full md:w-64">
                    <label class="label"><span class="label-text">Name (Optional)</span></label>
                    <input type="text" bind:value={repoNameInput} placeholder="My Repo" class="input input-bordered w-full" />
                </div>
                <button class="btn btn-primary" onclick={addRepository} disabled={!repoUrlInput || loading}>
                    {loading ? 'Adding...' : 'Add Repository'}
                </button>
           </div>

           <!-- Repo List -->
           <div class="grid gap-4">
               {#each $repositories as repo}
                   <div class="alert bg-base-100 shadow-sm border border-base-content/5 flex items-center justify-between">
                        <div class="flex items-center gap-4">
                             <div class="size-8 rounded bg-base-300 shrink-0 relative overflow-hidden flex items-center justify-center text-base-content/70">
                                 <svg xmlns="http://www.w3.org/2000/svg" class="size-4 opacity-70" viewBox="0 0 24 24" fill="currentColor">
                                     <path d="M20.5 11H19V7c0-1.1-.9-2-2-2h-4V3.5C13 2.12 11.88 1 10.5 1S8 2.12 8 3.5V5H4c-1.1 0-1.99.9-1.99 2v3.8H3.5c1.49 0 2.7 1.21 2.7 2.7s-1.21 2.7-2.7 2.7H2V20c0 1.1.9 2 2 2h3.8v-1.5c0-1.49 1.21-2.7 2.7-2.7 1.49 0 2.7 1.21 2.7 2.7V22H17c1.1 0 2-.9 2-2v-4h1.5c1.38 0 2.5-1.12 2.5-2.5S21.88 11 20.5 11z"/>
                                 </svg>
                                 {#if repo.iconUrl}
                                     <img 
                                         src={repo.iconUrl} 
                                         alt="" 
                                         class="absolute inset-0 w-full h-full object-contain bg-base-300" 
                                         onerror={handleIconError}
                                     />
                                 {/if}
                             </div>
                             <div>
                                 <h3 class="font-bold">{repo.name}</h3>
                                 <div class="text-xs opacity-50 font-mono truncate max-w-md">{repo.url}</div>
                                 {#if repo.description}
                                     <div class="text-xs opacity-70 mt-1 line-clamp-2 max-w-md">{repo.description}</div>
                                 {/if}
                             </div>
                        </div>
                        <div class="flex gap-2">
                             <button class="btn btn-sm btn-outline" onclick={() => browseRepo(repo)}>
                                Browse Plugins
                             </button>
                             <button class="btn btn-sm btn-square btn-ghost text-error" onclick={() => removeRepository(repo.id)}>
                                <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" /></svg>
                             </button>
                        </div>
                   </div>
               {/each}
           </div>
      </div>
  {:else if activeTab === 'overrides'}
      <div class="space-y-8">
          <div class="card bg-base-200 border border-base-content/10">
              <div class="card-body gap-4">
                  <div>
                      <h3 class="font-bold text-lg">Add Provider Override</h3>
                      <p class="text-sm opacity-60">
                          Create a custom provider by overriding the base URL, name, or language.
                      </p>
                  </div>
                  <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                      <div class="form-control">
                          <label class="label"><span class="label-text">Base Provider</span></label>
                          <ProviderPicker
                              providers={overrideableProviders}
                              selectedValue={overrideBaseClass}
                              valueKey="className"
                              title="Select Base Provider"
                              description="Choose the provider to clone."
                              buttonClass="btn btn-outline w-full justify-between"
                              onchange={(event) => (overrideBaseClass = event.detail.value)}
                          />
                      </div>
                      <div class="form-control">
                          <label class="label"><span class="label-text">Override Name</span></label>
                          <input
                              type="text"
                              bind:value={overrideName}
                              placeholder="My Custom Provider"
                              class="input input-bordered w-full"
                          />
                      </div>
                      <div class="form-control md:col-span-2">
                          <label class="label"><span class="label-text">Override URL</span></label>
                          <input
                              type="text"
                              bind:value={overrideUrl}
                              placeholder="https://example.com"
                              class="input input-bordered w-full"
                          />
                      </div>
                      <div class="form-control">
                          <label class="label"><span class="label-text">Language</span></label>
                          <input
                              type="text"
                              bind:value={overrideLang}
                              placeholder="en"
                              class="input input-bordered w-full"
                          />
                          <label class="label">
                              <span class="label-text-alt opacity-60">Optional, defaults to base provider.</span>
                          </label>
                      </div>
                  </div>
                  <div class="flex justify-end">
                      <button
                          class="btn btn-primary"
                          onclick={addOverride}
                          disabled={!overrideBaseClass || !overrideName || !overrideUrl || overridesLoading}
                      >
                          {overridesLoading ? 'Saving...' : 'Add Override'}
                      </button>
                  </div>
              </div>
          </div>

          <div class="space-y-4">
              <h3 class="text-lg font-bold">Current Overrides</h3>
              {#if providerOverrides.length === 0}
                  <div class="py-12 text-center text-sm opacity-60">
                      No overrides added yet.
                  </div>
              {:else}
                  <div class="grid gap-3">
                      {#each providerOverrides as overrideEntry}
                          <div class="alert bg-base-100 shadow-sm border border-base-content/5 flex items-center justify-between">
                              <div>
                                  <h4 class="font-bold">{overrideEntry.name}</h4>
                                  <div class="text-xs opacity-60">
                                      Base: {resolveProviderLabel(overrideEntry.parentClassName)}
                                  </div>
                                  <div class="text-xs opacity-50 font-mono truncate max-w-md">{overrideEntry.url}</div>
                                  <div class="text-xs opacity-50">Lang: {overrideEntry.lang}</div>
                              </div>
                              <button
                                  class="btn btn-sm btn-ghost text-error"
                                  onclick={() => removeOverride(overrideEntry)}
                              >
                                  Remove
                              </button>
                          </div>
                      {/each}
                  </div>
              {/if}
          </div>
      </div>
  {/if}

  <ConfirmModal 
      bind:this={confirmRepoDelete} 
      title="Remove Repository" 
      message="Are you sure you want to remove this repository? Plugins installed from it may no longer receive updates."
      confirmText="Remove"
      type="error"
  />

  <ConfirmModal 
      bind:this={confirmUninstall} 
      title="Uninstall Plugin" 
      message={pluginToUninstall ? `Are you sure you want to uninstall ${pluginToUninstall.name}?` : 'Are you sure?'}
      confirmText="Uninstall"
      type="error"
  />

  <ConfirmModal 
      bind:this={confirmOverrideDelete} 
      title="Remove Override" 
      message="Are you sure you want to remove this override?"
      confirmText="Remove"
      type="error"
  />
</div>
