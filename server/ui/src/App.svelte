<script lang="ts">
  import Sidebar from './components/layout/Sidebar.svelte';
  import ToastContainer from './components/layout/ToastContainer.svelte';
  import Router from 'svelte-spa-router';
  import Home from './pages/Home.svelte';
  import Search from './pages/Search.svelte';
  import Settings from './pages/Settings.svelte';
  import PluginManager from './pages/PluginManager.svelte';
  import Details from './pages/Details.svelte';
  import Play from './pages/Play.svelte';
  import { theme } from './stores/theme';
  import { onMount } from 'svelte';

  const routes = {
    '/': Home,
    '/search': Search,
    '/settings': Settings,
    '/plugins': PluginManager,
    '/details': Details,
    '/play': Play,
  };

  onMount(() => {
      theme.init();
  });
</script>

<div class="flex h-screen w-full bg-base-300 text-base-content overflow-hidden font-body relative">
  <ToastContainer />
  <!-- Sidebar -->
  <Sidebar />

  <!-- Main Content -->
  <main class="flex-1 h-full overflow-y-auto relative no-scrollbar">
    <Router {routes} />
  </main>
</div>

<style>
  :global(.no-scrollbar) {
    scrollbar-width: none; 
    -ms-overflow-style: none; 
  }
  :global(.no-scrollbar::-webkit-scrollbar) { 
    display: none; 
  }
</style>
