<script lang="ts">
    import { push, querystring } from 'svelte-spa-router';
  import { api } from '../api';
    import PosterCard from '../components/shared/PosterCard.svelte';

  type EpisodeItem = {
      season: number;
      episode: number;
      name: string;
      data: string;
      posterUrl?: string;
      dub?: string;
      description?: string;
      date?: number;
      runTime?: number;
      rating?: number;
      score?: { data: number };
  };

  let provider = '';
  let mediaUrl = '';
  let queryName = '';
  let queryPoster = '';
  let queryType = '';

  let loading = false;
  let error: string | null = null;
  let details: any = null;
  let episodes: EpisodeItem[] = [];
  let seasons: number[] = [];
  let selectedSeason: number | null = null;
  let loadToken = 0;
  let currentKey = '';

  $: if ($querystring !== undefined) parseQuery($querystring || '');

  $: episodes = normalizeEpisodes(details);
  $: seasons = [...new Set(episodes.map((e) => e.season))].sort((a, b) => a - b);
  $: if (seasons.length > 0 && (selectedSeason === null || !seasons.includes(selectedSeason))) {
      selectedSeason = seasons[0];
  }
  $: if (seasons.length === 0) {
      selectedSeason = null;
  }

  const seriesTypes = new Set(['TvSeries', 'Anime', 'OVA', 'Cartoon']);
  $: isSeries = episodes.length > 0 || seriesTypes.has(details?.type);

  function parseQuery(query: string) {
      const params = new URLSearchParams(query);
      const nextProvider = params.get('provider') || '';
      const nextUrl = params.get('url') || '';
      const nextName = params.get('name') || '';
      const nextPoster = params.get('poster') || '';
      const nextType = params.get('type') || '';
      const key = `${nextProvider}::${nextUrl}`;
      if (key === currentKey) return;
      currentKey = key;
      provider = nextProvider;
      mediaUrl = nextUrl;
      queryName = nextName;
      queryPoster = nextPoster;
      queryType = nextType;
      loadDetails();
  }

  async function loadDetails() {
      if (!provider || !mediaUrl) {
          details = null;
          episodes = [];
          return;
      }
      const token = ++loadToken;
      loading = true;
      error = null;
      try {
          const data = await api.loadMedia(provider, mediaUrl);
          if (token !== loadToken) return;
          details = data;
      } catch (e: any) {
          if (token !== loadToken) return;
          details = null;
          error = e.message || 'Failed to load details';
      } finally {
          if (token === loadToken) loading = false;
      }
  }

  function normalizeEpisodes(data: any): EpisodeItem[] {
      if (!data?.episodes) return [];
      if (Array.isArray(data.episodes)) {
          return data.episodes.map((ep: any, index: number) => ({
              season: ep.season ?? 1,
              episode: ep.episode ?? index + 1,
              name: ep.name || `Episode ${ep.episode ?? index + 1}`,
              data: ep.data,
              posterUrl: ep.posterUrl,
              description: ep.description,
              date: ep.date ?? ep.airDate ?? ep.air_date,
              runTime: ep.runTime ?? ep.runtime ?? ep.duration,
              rating: ep.rating,
              score: ep.score,
          }));
      }
      if (typeof data.episodes === 'object') {
          return Object.entries(data.episodes).flatMap(([dub, list]) => {
              if (!Array.isArray(list)) return [];
              return list.map((ep: any, index: number) => ({
                  season: ep.season ?? 1,
                  episode: ep.episode ?? index + 1,
                  name: ep.name || `Episode ${ep.episode ?? index + 1}`,
                  data: ep.data,
                  posterUrl: ep.posterUrl,
                  description: ep.description,
                  date: ep.date ?? ep.airDate ?? ep.air_date,
                  runTime: ep.runTime ?? ep.runtime ?? ep.duration,
                  rating: ep.rating,
                  score: ep.score,
                  dub,
              }));
          });
      }
      return [];
  }

  function formatScore(value: number | undefined) {
      if (value == null) return null;
      return Math.round(value / 10000000) / 10;
  }

  function formatDate(ts: number | string | undefined) {
      if (!ts) return '';
      const n = typeof ts === 'string' ? Number(ts) : ts;
      if (Number.isNaN(n)) return '';
      const d = new Date(n);
      return d.toLocaleDateString();
  }

  function formatRuntime(mins: number | undefined) {
      if (!mins && mins !== 0) return null;
      const h = Math.floor(mins / 60);
      const m = mins % 60;
      return h > 0 ? `${h}h ${m}m` : `${m}m`;
  }

  function openRec(rec: any) {
      const params = new URLSearchParams();
      if (rec.apiName) params.set('provider', rec.apiName);
      if (rec.url) params.set('url', rec.url);
      if (rec.name) params.set('name', rec.name);
      if (rec.posterUrl) params.set('poster', rec.posterUrl);
      if (rec.type) params.set('type', rec.type);
      push(`/details?${params.toString()}`);
  }

  function playMovie() {
      const params = new URLSearchParams();
      if (provider) params.set('provider', provider);
      if (details?.name || queryName) params.set('name', details?.name || queryName);
      if (details?.dataUrl || details?.data || details?.url) {
          params.set('data', details?.dataUrl || details?.data || details?.url);
      }
      if (details?.posterUrl || queryPoster) params.set('poster', details?.posterUrl || queryPoster);
      push(`/play?${params.toString()}`);
  }

  function playEpisode(ep: EpisodeItem) {
      const params = new URLSearchParams();
      if (provider) params.set('provider', provider);
      if (details?.name || queryName) params.set('show', details?.name || queryName);
      params.set('episode', ep.name);
      params.set('data', ep.data);
      if (ep.posterUrl || queryPoster) params.set('poster', ep.posterUrl || queryPoster);
      push(`/play?${params.toString()}`);
  }

  function goBack() {
      window.history.back();
  }
</script>

<div class="min-h-full pb-20">
  {#if loading}
      <div class="flex items-center justify-center h-96">
          <span class="loading loading-spinner loading-lg text-primary"></span>
      </div>
  {:else if error}
      <div class="p-10 flex flex-col items-center justify-center text-center">
          <div class="text-error text-6xl mb-4">!</div>
          <h3 class="text-xl font-bold mb-2">Failed to load details</h3>
          <p class="text-base-content/60 max-w-md">{error}</p>
          <button class="btn btn-primary mt-6" onclick={loadDetails}>Retry</button>
      </div>
  {:else if details}
      {@const heroImage = details?.backgroundPosterUrl || details?.posterUrl || queryPoster}
      {@const posterImage = details?.posterUrl || queryPoster}
      {@const title = details?.name || queryName || 'Details'}
      {@const typeLabel = details?.type || queryType}
      <div class="relative w-full h-[55vh] overflow-hidden">
          {#if heroImage}
              <img src={heroImage} alt="" class="absolute inset-0 w-full h-full object-cover object-top opacity-70" />
          {/if}
          <div class="absolute inset-0 bg-gradient-to-t from-base-300 via-base-300/70 to-transparent"></div>
          <div class="absolute top-6 left-6">
              <button class="btn btn-sm btn-ghost" onclick={goBack}>Back</button>
          </div>
          <div class="absolute bottom-0 left-0 w-full p-8 md:p-12">
              <div class="flex flex-col md:flex-row gap-6 items-end">
                  <div class="w-32 md:w-48 shrink-0">
                      {#if posterImage}
                          <img src={posterImage} alt={title} class="w-full rounded-xl shadow-xl border border-base-content/10" />
                      {:else}
                          <div class="w-full aspect-[2/3] rounded-xl bg-base-200"></div>
                      {/if}
                  </div>
                  <div class="flex-1">
                      <div class="flex flex-wrap gap-2 mb-3">
                          {#if typeLabel}
                              <span class="badge badge-primary">{typeLabel}</span>
                          {/if}
                          {#if details?.year}
                              <span class="badge badge-ghost">{details.year}</span>
                          {/if}
                          {#if details?.duration}
                              <span class="badge badge-ghost">{formatRuntime(details.duration)}</span>
                          {/if}
                          {#if details?.contentRating}
                              <span class="badge badge-ghost">{details.contentRating}</span>
                          {/if}
                          {#if details?.tags}
                              {#each details.tags as tag}
                                  <span class="badge badge-ghost">{tag}</span>
                              {/each}
                          {/if}
                          {#if details?.score?.data}
                              <span class="badge badge-ghost">Score {Math.round(details.score.data / 10000000) / 10}</span>
                          {/if}
                      </div>
                      <h1 class="text-3xl md:text-5xl font-black">{title}</h1>
                      {#if details?.plot}
                          <p class="mt-4 text-base-content/70 max-w-2xl line-clamp-4">
                              {details.plot}
                          </p>
                      {/if}
                      <div class="mt-6 flex gap-3">
                          {#if !isSeries}
                              <button class="btn btn-primary" onclick={playMovie}>Play</button>
                          {/if}
                      </div>
                  </div>
              </div>
          </div>
      </div>
      
              <!-- recommendations moved below -->

              {#if isSeries}
          <div class="px-6 md:px-12 mt-8 grid grid-cols-1 lg:grid-cols-[220px_1fr] gap-6">
              <div class="space-y-4">
                  <h2 class="text-lg font-bold">Seasons</h2>
                  <div class="flex flex-col gap-2 overflow-y-auto max-h-[45vh] pr-2 seasons-scroll">
                      {#each seasons as season}
                          <button
                              class="btn btn-sm justify-start {selectedSeason === season ? 'btn-primary' : 'btn-ghost'}"
                              onclick={() => selectedSeason = season}
                          >
                              Season {season}
                          </button>
                      {/each}
                      {#if seasons.length === 0}
                          <div class="text-sm text-base-content/60">No seasons available.</div>
                      {/if}
                  </div>
              </div>
              <div class="space-y-4">
                  <div class="flex items-center justify-between">
                      <h2 class="text-lg font-bold">Episodes</h2>
                      {#if selectedSeason !== null}
                          <span class="text-sm text-base-content/60">Season {selectedSeason}</span>
                      {/if}
                  </div>
                      <div class="grid gap-3 overflow-y-auto max-h-[45vh] episodes-scroll">
                      {#each episodes.filter((ep) => ep.season === selectedSeason) as ep}
                          <div class="card bg-base-100 border border-base-content/10">
                              <div class="card-body p-4 flex flex-col md:flex-row md:items-center md:justify-between gap-4">
                                  <div class="flex items-center gap-4">
                                      {#if ep.posterUrl}
                                          <img src={ep.posterUrl} alt="" class="w-16 h-24 object-cover rounded-md" />
                                      {:else}
                                          <div class="w-16 h-24 rounded-md bg-base-200"></div>
                                      {/if}
                                      <div>
                                          <div class="text-sm text-base-content/60">Episode {ep.episode}</div>
                                          <div class="font-semibold">{ep.name}</div>
                                              {#if ep.description}
                                                  <div class="text-sm text-base-content/70 mt-1 line-clamp-2">{ep.description}</div>
                                              {/if}
                                              <div class="flex flex-wrap gap-3 mt-2 text-xs text-base-content/60">
                                                  {#if ep.runTime}
                                                      <div>{formatRuntime(ep.runTime)}</div>
                                                  {/if}
                                                  {#if ep.rating}
                                                      <div>Rating {ep.rating}%</div>
                                                  {/if}
                                                  {#if ep.score?.data}
                                                      <div>Score {formatScore(ep.score.data)}</div>
                                                  {/if}
                                                  {#if ep.date}
                                                      <div>Aired {formatDate(ep.date)}</div>
                                                  {/if}
                                              </div>
                                          {#if ep.dub}
                                              <div class="badge badge-xs badge-ghost mt-2">{ep.dub}</div>
                                          {/if}
                                      </div>
                                  </div>
                                  <button class="btn btn-sm btn-primary" onclick={() => playEpisode(ep)}>Play</button>
                              </div>
                          </div>
                      {/each}
                      {#if selectedSeason !== null && episodes.filter((ep) => ep.season === selectedSeason).length === 0}
                          <div class="text-sm text-base-content/60">No episodes found for this season.</div>
                      {/if}
                  </div>
              </div>
          </div>
      {/if}

      {#if details?.recommendations && details.recommendations.length > 0}
          <div class="px-6 md:px-12 mt-8">
              <h2 class="text-lg font-bold mb-3">Recommendations</h2>
              <div class="relative group/carousel">
                  <div class="flex gap-4 overflow-x-auto pb-6 scroll-smooth snap-x">
                      {#each details.recommendations as rec}
                          <div class="w-[160px] md:w-[200px] shrink-0 snap-start">
                              <PosterCard
                                  title={rec.name}
                                  image={rec.posterUrl}
                                  subtitle={rec.type}
                                  onSelect={() => openRec(rec)}
                              />
                          </div>
                      {/each}
                  </div>
              </div>
          </div>
      {/if}
  {:else}
      <div class="p-20 text-center text-base-content/50">
          Select a title to view details.
      </div>
  {/if}
</div>

<style>
/* Make scrollbars visible in WebKit and Firefox for the scrollable lists */
.seasons-scroll, .episodes-scroll {
    scrollbar-width: auto; /* Firefox */
    scrollbar-color: rgba(100,100,100,0.6) transparent;
}
.seasons-scroll::-webkit-scrollbar, .episodes-scroll::-webkit-scrollbar {
    width: 12px;
    height: 12px;
}
.seasons-scroll::-webkit-scrollbar-track, .episodes-scroll::-webkit-scrollbar-track {
    background: transparent;
}
.seasons-scroll::-webkit-scrollbar-thumb, .episodes-scroll::-webkit-scrollbar-thumb {
    background-color: rgba(100,100,100,0.6);
    border-radius: 9999px;
    border: 3px solid transparent;
    background-clip: padding-box;
}
</style>
