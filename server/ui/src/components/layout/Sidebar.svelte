<script lang="ts">
  import { onDestroy, onMount } from 'svelte';
  import { location, push } from 'svelte-spa-router';
  import { API_BASE_URL } from '../../api';

  let healthOk = false;
  let healthTimer: number | undefined;
  const navItems = [
    { label: 'Home', icon: 'M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6', path: '/' },
    { label: 'Search', icon: 'M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z', path: '/search' },
    { label: 'Plugins', icon: 'M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10', path: '/plugins' },
    { label: 'Settings', icon: 'M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z M15 12a3 3 0 11-6 0 3 3 0 016 0z', path: '/settings' },
  ];

  function isActive(path: string, currentPath: string) {
      if (path === '/') return currentPath === '/';
      return currentPath.startsWith(path);
  }

  async function checkHealth() {
      try {
          const res = await fetch(`${API_BASE_URL}/health`);
          if (!res.ok) {
              healthOk = false;
              return;
          }
          const data = await res.json();
          healthOk = data?.status === 'ok';
      } catch {
          healthOk = false;
      }
  }

  onMount(() => {
      checkHealth();
      healthTimer = window.setInterval(checkHealth, 3000);
  });

  onDestroy(() => {
      if (healthTimer) window.clearInterval(healthTimer);
  });
</script>

<aside class="w-20 md:w-64 h-full bg-base-200 border-r border-base-100 flex flex-col transition-all duration-300">
  <!-- Logo Area -->
  <div class="h-16 flex items-center justify-center md:justify-start md:px-6 border-b border-base-100/50">
      <div class="size-10 flex items-center justify-center shrink-0 text-primary">
          <svg viewBox="0 0 108 108" class="size-full fill-current scale-150">
              <g transform="translate(29.16, 29.16) scale(0.1755477)">
                  <path d="M 245.05 148.63 C 242.249 148.627 239.463 149.052 236.79 149.89 C 235.151 141.364 230.698 133.63 224.147 127.931 C 217.597 122.233 209.321 118.893 200.65 118.45 C 195.913 105.431 186.788 94.458 174.851 87.427 C 162.914 80.396 148.893 77.735 135.21 79.905 C 121.527 82.074 109.017 88.941 99.84 99.32 C 89.871 95.945 79.051 96.024 69.133 99.545 C 59.215 103.065 50.765 109.826 45.155 118.73 C 39.545 127.634 37.094 138.174 38.2 148.64 L 37.94 148.64 C 30.615 148.64 23.582 151.553 18.403 156.733 C 13.223 161.912 10.31 168.945 10.31 176.27 C 10.31 183.595 13.223 190.628 18.403 195.807 C 23.582 200.987 30.615 203.9 37.94 203.9 L 245.05 203.9 C 252.375 203.9 259.408 200.987 264.587 195.807 C 269.767 190.628 272.68 183.595 272.68 176.27 C 272.68 168.945 269.767 161.912 264.587 156.733 C 259.408 151.553 252.375 148.64 245.05 148.64 Z" />
                  <path d="M 208.61 125 C 208.61 123.22 208.55 121.45 208.48 119.69 C 205.919 119.01 203.296 118.595 200.65 118.45 C 195.913 105.431 186.788 94.458 174.851 87.427 C 162.914 80.396 148.893 77.735 135.21 79.905 C 121.527 82.074 109.017 88.941 99.84 99.32 C 89.871 95.945 79.051 96.024 69.133 99.545 C 59.215 103.065 50.765 109.826 45.155 118.73 C 39.545 127.634 37.094 138.174 38.2 148.64 L 37.94 148.64 C 30.615 148.64 23.582 151.553 18.403 156.733 C 13.223 161.912 10.31 168.945 10.31 176.27 C 10.31 183.595 13.223 190.628 18.403 195.807 C 23.582 200.987 30.615 203.9 37.94 203.9 L 179 203.9 C 198.116 182.073 208.646 154.015 208.646 125 Z" />
                  <path d="M 99.84 99.32 C 89.871 95.945 79.051 96.024 69.133 99.545 C 59.215 103.065 50.765 109.826 45.155 118.73 C 39.545 127.634 37.094 138.174 38.2 148.64 L 37.94 148.64 C 30.783 148.665 23.909 151.471 18.779 156.461 C 13.648 161.452 10.653 168.246 10.43 175.399 C 10.207 182.553 12.773 189.52 17.583 194.82 C 22.392 200.121 29.079 203.349 36.22 203.82 C 67.216 202.93 96.673 189.98 118.284 167.742 C 139.895 145.504 151.997 115.689 152 84.68 C 152 83 151.94 81.33 151.87 79.68 C 149.443 79.361 146.998 79.194 144.55 79.18 C 136.095 79.171 127.735 80.962 120.026 84.434 C 112.317 87.907 105.435 92.982 99.84 99.32 Z" />
              </g>
          </svg>
      </div>
      <span class="ml-3 font-bold text-lg hidden md:inline-flex items-center gap-2">
          CloudStream
          <span class="size-2 rounded-full {healthOk ? 'bg-success' : 'bg-base-content/30'}"></span>
      </span>
  </div>

  <!-- Nav Items -->
  <nav class="flex-1 py-6 flex flex-col gap-2 px-2 md:px-4">
      {#each navItems as item}
          <button
              onclick={() => push(item.path)}
              class="flex items-center gap-4 px-3 py-3 rounded-lg transition-all duration-200 group relative
              {isActive(item.path, $location) 
                  ? 'bg-base-200 text-primary shadow-inner' 
                  : 'text-base-content/60 hover:text-base-content hover:bg-base-200/50'}"
          >
              <svg class="size-6 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d={item.icon} />
              </svg>
              <span class="font-medium hidden md:block">{item.label}</span>
              
              <!-- Active Indicator -->
              {#if isActive(item.path, $location)}
                  <div class="absolute left-0 top-1/2 -translate-y-1/2 w-1 h-8 bg-primary rounded-r-full"></div>
              {/if}
          </button>
      {/each}
  </nav>

</aside>
