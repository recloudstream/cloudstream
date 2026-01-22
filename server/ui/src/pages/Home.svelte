<script lang="ts">
  import { onMount } from 'svelte';
  import { activeProvider, providers, loadInitialData } from '../stores';
  import { api } from '../api';
  import PosterCard from '../components/shared/PosterCard.svelte';

  let mainPageData: any = null;
  let loading = false;
  let error: string | null = null;

  onMount(async () => {
    if ($providers.length === 0) {
      await loadInitialData();
    }
  });

  // Reactive statement to load data when provider changes
  $: if ($activeProvider) {
    loadMainPage($activeProvider);
  }

  async function loadMainPage(provider: string) {
    loading = true;
    error = null;
    mainPageData = null;
    try {
      const response = await api.getProviderMainPage(provider);
      // API returns { items: HomePageList[], hasNext: boolean }
      mainPageData = response.items || [];
    } catch (e: any) {
      error = e.message;
    } finally {
      loading = false;
    }
  }

  function handleProviderChange(e: Event) {
      const target = e.target as HTMLSelectElement;
      activeProvider.set(target.value);
  }
</script>

<div class="min-h-full pb-20">
  
  <!-- Header / Controls -->
  <div class="sticky top-0 z-30 bg-base-300/80 backdrop-blur-md px-6 py-4 flex items-center justify-between border-b border-white/5">
      <h2 class="text-xl font-bold text-base-content">Browse</h2>
      <select class="select select-sm select-bordered w-full max-w-xs" onchange={handleProviderChange} value={$activeProvider}>
          {#each $providers as provider}
              <option value={provider.name}>{provider.name} ({provider.lang})</option>
          {/each}
      </select>
  </div>

  {#if loading}
      <div class="flex items-center justify-center h-96">
          <span class="loading loading-spinner loading-lg text-primary"></span>
      </div>
  {:else if error}
      <div class="p-10 flex flex-col items-center justify-center text-center">
          <div class="text-error text-6xl mb-4">⚠️</div>
          <h3 class="text-xl font-bold mb-2">Failed to load content</h3>
          <p class="text-base-content/60 max-w-md">{error}</p>
          <button class="btn btn-primary mt-6" onclick={() => $activeProvider && loadMainPage($activeProvider)}>Retry</button>
      </div>
  {:else if mainPageData}
      
      <!-- Hero Section (Using first item of first row as Hero roughly) -->
      {#if mainPageData.length > 0 && mainPageData[0].list.length > 0}
          {@const heroItem = mainPageData[0].list[0]}
          <div class="relative w-full h-[60vh] overflow-hidden mb-8 group">
              <div class="absolute inset-0">
                   <img src={heroItem.posterUrl} class="w-full h-full object-cover object-top opacity-60 mask-image-gradient" alt="Hero" />
                   <div class="absolute inset-0 bg-gradient-to-t from-base-300 via-base-300/50 to-transparent"></div>
              </div>
              
              <div class="absolute bottom-0 left-0 w-full p-8 md:p-12 flex flex-col items-start gap-4">
                  <div class="badge badge-primary font-bold">Featured</div>
                  <h1 class="text-4xl md:text-6xl font-black text-white drop-shadow-lg max-w-3xl leading-tight">
                      {heroItem.name}
                  </h1>
                  <p class="text-base-content/80 max-w-2xl text-lg line-clamp-3 md:line-clamp-2">
                       {heroItem.type} • Click to watch now
                  </p>
                  <div class="flex gap-3 mt-4">
                      <button class="btn btn-primary btn-lg gap-2 px-8">
                          <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" viewBox="0 0 20 20" fill="currentColor">
                              <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM9.555 7.168A1 1 0 008 8v4a1 1 0 001.555.832l3-2a1 1 0 000-1.664l-3-2z" clip-rule="evenodd" />
                          </svg>
                          Play Now
                      </button>
                      <button class="btn btn-neutral btn-lg gap-2">
                          <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                          </svg>
                          Details
                      </button>
                  </div>
              </div>
          </div>
      {/if}

      <!-- Content Rows -->
      <div class="space-y-12 px-6 md:px-12">
          {#each mainPageData as row}
              {#if row.list && row.list.length > 0}
                  <section>
                      <h3 class="text-xl font-bold text-base-content mb-4 px-1 flex items-center gap-2">
                          <div class="w-1 h-6 bg-primary rounded-full"></div>
                          {row.name}
                      </h3>
                      
                      <!-- Carousel container -->
                      <div class="relative group/carousel">
                          <div class="flex gap-4 overflow-x-auto pb-6 scroll-smooth snap-x no-scrollbar">
                              {#each row.list as item}
                                  <div class="w-[160px] md:w-[200px] shrink-0 snap-start">
                                      <PosterCard 
                                          title={item.name} 
                                          image={item.posterUrl} 
                                          subtitle={item.type}
                                      />
                                  </div>
                              {/each}
                          </div>
                      </div>
                  </section>
              {/if}
          {/each}
      </div>

  {:else}
      <div class="p-20 text-center text-base-content/50">
          Select a provider to start browsing.
      </div>
  {/if}
</div>

<style>
  .mask-image-gradient {
      mask-image: linear-gradient(to bottom, black 50%, transparent 100%);
  }
</style>
