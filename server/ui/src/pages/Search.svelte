<script lang="ts">
  import { onMount } from 'svelte';
  import { push } from 'svelte-spa-router';
  import { activeProvider, providers, loadInitialData } from '../stores';
  import { api } from '../api';
  import PosterCard from '../components/shared/PosterCard.svelte';
  import ProviderPicker from '../components/shared/ProviderPicker.svelte';

  let query = '';
  let searchResults: any[] = [];
  let loading = false;
  let error: string | null = null;
  let selectedTvTypes: string[] = [];
  
  // TvType options typically available (this list could be dynamic in a real app)
  const tvTypes = ['Movie', 'TvSeries', 'Anime', 'Cartoon', 'Live', 'AsianDrama'];

  onMount(async () => {
    if ($providers.length === 0) {
      await loadInitialData();
    }
  });

  async function handleSearch() {
    if (!query.trim() || !$activeProvider) return;
    
    loading = true;
    error = null;
    searchResults = [];
    
    try {
      const data = await api.searchProvider($activeProvider, query);
      // API returns { items: SearchResponse[], hasNext: boolean }
      if (data?.items && Array.isArray(data.items)) {
        searchResults = data.items;
      } else if (Array.isArray(data)) {
        searchResults = data;
      }
      
      if (selectedTvTypes.length > 0) {
           searchResults = searchResults.filter(item => selectedTvTypes.includes(item.type));
      }
    } catch (e: any) {
      error = e.message;
    } finally {
      loading = false;
    }
  }

  function toggleTvType(type: string) {
      if (selectedTvTypes.includes(type)) {
          selectedTvTypes = selectedTvTypes.filter(t => t !== type);
      } else {
          selectedTvTypes = [...selectedTvTypes, type];
      }
      if (query) handleSearch();
  }

  function handleProviderChange(event: CustomEvent) {
      const value = event.detail?.value;
      if (value) activeProvider.set(value);
  }

  function openDetails(item: any) {
      if (!$activeProvider || !item?.url) return;
      const params = new URLSearchParams({
          provider: $activeProvider,
          url: item.url
      });
      if (item.name) params.set('name', item.name);
      if (item.posterUrl) params.set('poster', item.posterUrl);
      if (item.type) params.set('type', item.type);
      push(`/details?${params.toString()}`);
  }
</script>

<div class="p-6 md:p-12 max-w-7xl mx-auto space-y-8">
  
  <div class="flex flex-col gap-6">
      <h1 class="text-4xl font-bold text-base-content">Search</h1>
      
      <!-- Search Bar & Provider Select -->
      <div class="flex flex-col md:flex-row gap-4">
          <div class="join w-full">
              <ProviderPicker
                  providers={$providers}
                  selectedValue={$activeProvider}
                  valueKey="name"
                  title="Provider"
                  description="Select which source to search."
                  buttonClass="btn btn-outline join-item bg-base-200 justify-between"
                  onchange={handleProviderChange}
              />
              <div class="relative w-full">
                  <input 
                      type="text" 
                      placeholder="Search for movies, shows, anime..." 
                      class="input input-bordered join-item w-full bg-base-200 focus:bg-base-100 transition-colors" 
                      bind:value={query}
                      onkeydown={(e) => e.key === 'Enter' && handleSearch()}
                  />
                  {#if loading}
                      <span class="loading loading-spinner loading-sm absolute right-4 top-1/2 -translate-y-1/2 text-primary"></span>
                  {/if}
              </div>
              <button class="btn btn-primary join-item" onclick={handleSearch} disabled={loading || !query}>
                  Search
              </button>
          </div>
      </div>

      <!-- Filters -->
      <div class="flex flex-wrap gap-2">
          {#each tvTypes as type}
              <button 
                  class="btn btn-sm rounded-full {selectedTvTypes.includes(type) ? 'btn-secondary text-base-content' : 'btn-outline border-base-content/20 text-base-content/60 hover:btn-ghost'}"
                  onclick={() => toggleTvType(type)}
              >
                  {type}
              </button>
          {/each}
      </div>
  </div>

  <!-- Results -->
  <div class="min-h-[400px]">
      {#if error}
          <div class="alert alert-error">
              <svg xmlns="http://www.w3.org/2000/svg" class="stroke-current shrink-0 h-6 w-6" fill="none" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>
              <span>{error}</span>
          </div>
      {:else if searchResults.length === 0 && !loading && query}
           <div class="text-center py-20 text-base-content/50">
               No results found for "{query}".
           </div>
      {:else if searchResults.length > 0}
          <div class="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4 md:gap-6">
              {#each searchResults as item}
                  <PosterCard 
                      title={item.name} 
                      image={item.posterUrl} 
                      subtitle={item.type}
                      onSelect={() => openDetails(item)}
                  />
              {/each}
          </div>
      {/if}
  </div>

</div>
