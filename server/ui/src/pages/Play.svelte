<script lang="ts">
  import { querystring } from 'svelte-spa-router';
  import { api, API_BASE_URL } from '../api';

  type ExtractorLink = {
      url: string;
      referer: string;
      quality: number;
      name: string;
      source: string;
      isM3u8: boolean;
      isDash: boolean;
      allHeaders?: Record<string, string>;
      userAgent?: string | null;
  };

  let provider = '';
  let name = '';
  let show = '';
  let episode = '';
  let data = '';
  let poster = '';

  let loading = false;
  let error: string | null = null;
  let links: ExtractorLink[] = [];
  let subtitles: any[] = [];
  let selectedLink: ExtractorLink | null = null;
  let proxySrc = '';
  let playerSrc: any = '';
  let playerEl: any = null;
  let loadToken = 0;
  let currentKey = '';

  $: if ($querystring !== undefined) parseQuery($querystring || '');

  function parseQuery(query: string) {
      const params = new URLSearchParams(query);
      const nextProvider = params.get('provider') || '';
      const nextData = params.get('data') || '';
      const key = `${nextProvider}::${nextData}`;
      provider = nextProvider;
      data = nextData;
      name = params.get('name') || '';
      show = params.get('show') || '';
      episode = params.get('episode') || '';
      poster = params.get('poster') || '';
      if (!provider || !data || key === currentKey) return;
      currentKey = key;
      loadLinks();
  }

  async function loadLinks() {
      const token = ++loadToken;
      loading = true;
      error = null;
      links = [];
      subtitles = [];
      selectedLink = null;
      proxySrc = '';
      try {
          const response = await api.getProviderLinks(provider, data);
          if (token !== loadToken) return;
          if (!response?.links?.length) {
              error = response?.error || 'No playable links found.';
              return;
          }
          links = response.links;
          subtitles = response.subtitles || [];
          selectLink(getBestLink(links));
      } catch (e: any) {
          if (token !== loadToken) return;
          error = e.message || 'Failed to load playback data.';
      } finally {
          if (token === loadToken) loading = false;
      }
  }

  function getBestLink(items: ExtractorLink[]) {
      return [...items].sort((a, b) => (b.quality || 0) - (a.quality || 0))[0] || null;
  }

  function selectLink(link: ExtractorLink | null) {
      selectedLink = link;
      proxySrc = link ? buildProxyUrl(link) : '';
      playerSrc = link ? buildPlayerSource(link, proxySrc) : '';
  }

  $: if (playerEl) {
      playerEl.src = playerSrc || '';
  }

  function buildPlayerSource(link: ExtractorLink, src: string) {
      const type = inferMimeType(link);
      return type ? { src, type } : src;
  }

  function buildProxyUrl(link: ExtractorLink) {
      const params = new URLSearchParams();
      params.set('url', link.url);
      if (link.referer) params.set('referer', link.referer);
      const headers = { ...(link.allHeaders || {}) };
      if (link.userAgent && !headerExists(headers, 'User-Agent')) {
          headers['User-Agent'] = link.userAgent;
      }
      const headersEncoded = encodeHeaders(headers);
      if (headersEncoded) params.set('headers', headersEncoded);
      return `${API_BASE_URL}/proxy?${params.toString()}`;
  }

  function encodeHeaders(headers: Record<string, string>) {
      try {
          const json = JSON.stringify(headers);
          return btoa(unescape(encodeURIComponent(json)));
      } catch {
          return '';
      }
  }

  function headerExists(headers: Record<string, string>, key: string) {
      return Object.keys(headers).some((header) => header.toLowerCase() === key.toLowerCase());
  }

  function subtitleUrl(sub: any) {
      const params = new URLSearchParams();
      params.set('url', sub.url);
      if (sub.headers) {
          const headersEncoded = encodeHeaders(sub.headers);
          if (headersEncoded) params.set('headers', headersEncoded);
      }
      return `${API_BASE_URL}/proxy?${params.toString()}`;
  }

  function inferMimeType(link: ExtractorLink) {
      if (link.isDash) return 'application/dash+xml';
      if (link.isM3u8) return 'application/x-mpegurl';
      const url = link.url.toLowerCase();
      if (url.includes('.mp4')) return 'video/mp4';
      if (url.includes('.webm')) return 'video/webm';
      if (url.includes('.mkv')) return 'video/x-matroska';
      if (url.includes('.mov')) return 'video/quicktime';
      if (url.includes('.mp3')) return 'audio/mpeg';
      if (url.includes('.m4a')) return 'audio/mp4';
      if (url.includes('.aac')) return 'audio/aac';
      if (url.includes('.ogg') || url.includes('.ogv')) return 'video/ogg';
      return 'video/mp4';
  }

  function goBack() {
      window.history.back();
  }
</script>

<div class="p-6 md:p-10 space-y-6">
  <div class="flex items-center justify-between">
      <button class="btn btn-sm btn-ghost" onclick={goBack}>Back</button>
      {#if links.length > 1}
          <select
              class="select select-sm select-bordered"
              onchange={(e) => {
                  const value = (e.target as HTMLSelectElement).value;
                  const next = links.find((link) => link.url === value) || null;
                  selectLink(next);
              }}
          >
              {#each links as link}
                  <option value={link.url} selected={selectedLink?.url === link.url}>
                      {link.name || link.source} • {link.quality}
                  </option>
              {/each}
          </select>
      {/if}
  </div>

  <div>
      <h1 class="text-2xl md:text-3xl font-bold">
          {name || episode || show || 'Playback'}
      </h1>
      {#if show && episode}
          <p class="text-base-content/60">{show} • {episode}</p>
      {:else if show}
          <p class="text-base-content/60">{show}</p>
      {/if}
  </div>

  {#if loading}
      <div class="flex items-center justify-center h-80">
          <span class="loading loading-spinner loading-lg text-primary"></span>
      </div>
  {:else if error}
      <div class="alert alert-error">
          <span>{error}</span>
      </div>
  {:else if playerSrc}
      <media-player
          class="app-player w-full"
          bind:this={playerEl}
          title={name || show || 'Playback'}
          poster={poster || undefined}
          crossorigin
          playsinline
      >
          <media-outlet>
              {#each subtitles as sub}
                  <track
                      kind="subtitles"
                      src={subtitleUrl(sub)}
                      label={sub.lang || sub.langTag || 'Subtitle'}
                      srclang={sub.langTag || sub.lang || 'en'}
                  />
              {/each}
          </media-outlet>
          <media-community-skin></media-community-skin>
          <media-poster></media-poster>
      </media-player>
  {:else}
      <div class="text-base-content/60">Select a title to play.</div>
  {/if}
</div>
