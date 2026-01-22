<script lang="ts">
  import { onMount } from 'svelte';
  import { push } from 'svelte-spa-router';
  import { activeProvider, providers, loadInitialData } from '../stores';
  import { api } from '../api';
  import PosterCard from '../components/shared/PosterCard.svelte';
  import ProviderPicker from '../components/shared/ProviderPicker.svelte';

  let mainPageData: any[] | null = null;
  let loading = false;
  let error: string | null = null;
  let loadToken = 0;

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
    const token = ++loadToken;
    loading = true;
    error = null;
    mainPageData = null;
    try {
      const pages = await api.getProviderMainPages(provider);
      if (token !== loadToken) return;
      const entries = (pages || []).filter((page: any) => page?.data);
      if (entries.length === 0) {
        mainPageData = [];
        return;
      }
      const responses = await Promise.all(
        entries.map(async (page: any) => {
          if (!page?.data) return null;
          try {
            const response = await api.getProviderMainPage(provider, { data: page.data });
            return { page, response };
          } catch (err) {
            console.error('Failed to load main page section', page?.name, err);
            return null;
          }
        })
      );
      if (token !== loadToken) return;
      mainPageData = responses.flatMap((entry) => {
        if (!entry?.response?.items) return [];
        return entry.response.items.map((row: any) => ({
          ...row,
          name: entry.page?.name || row.name,
          isHorizontalImages: entry.page?.horizontalImages ?? row.isHorizontalImages,
        }));
      });
    } catch (e: any) {
      if (token !== loadToken) return;
      error = e.message;
      mainPageData = [];
    } finally {
      if (token === loadToken) loading = false;
    }
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

<div class="min-h-full pb-20">
  
  <!-- Header / Controls -->
  <div class="sticky top-0 z-30 bg-base-300/80 backdrop-blur-md px-6 py-4 flex items-center justify-between border-b border-white/5">
      <h2 class="text-xl font-bold text-base-content">Browse</h2>
      <ProviderPicker
          providers={$providers}
          selectedValue={$activeProvider}
          valueKey="name"
          title="Choose Provider"
          description="Select which source to browse."
          buttonClass="btn btn-sm btn-outline w-full max-w-xs justify-between"
          onchange={handleProviderChange}
      />
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
      {#if mainPageData.length === 0}
          <div class="p-20 text-center text-base-content/50">
              No main page content available for this provider.
          </div>
      {:else}
          <!-- Hero Section (Using first item of first row as Hero roughly) -->
          {#if mainPageData.length > 0 && mainPageData[0]?.list?.length > 0}
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
                      <button class="btn btn-primary btn-lg gap-2 px-8" onclick={() => openDetails(heroItem)}>
                              <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" viewBox="0 0 20 20" fill="currentColor">
                                  <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM9.555 7.168A1 1 0 008 8v4a1 1 0 001.555.832l3-2a1 1 0 000-1.664l-3-2z" clip-rule="evenodd" />
                              </svg>
                              Play Now
                          </button>
                      <button class="btn btn-neutral btn-lg gap-2" onclick={() => openDetails(heroItem)}>
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
                          <div class="flex gap-4 overflow-x-auto pb-6 scroll-smooth snap-x">
                                  {#each row.list as item}
                                      <div class="w-[160px] md:w-[200px] shrink-0 snap-start">
                                      <PosterCard 
                                          title={item.name} 
                                          image={item.posterUrl} 
                                          subtitle={item.type}
                                          onSelect={() => openDetails(item)}
                                      />
                                      </div>
                                  {/each}
                              </div>
                          </div>
                      </section>
                  {/if}
              {/each}
          </div>
      {/if}
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
